-- =============================================================================
--  V1 - Modelo operacional del Servicio de Solicitudes
--
--  MIGRACION INMUTABLE. Una vez aplicada no se edita jamas: todo cambio
--  posterior entra como una migracion nueva (CLAUDE.md, seccion SQL).
--
--  Nomenclatura snake_case. Lenguaje ubicuo en espanol para los conceptos de
--  dominio; los identificadores tecnicos siguen la convencion relacional.
-- =============================================================================

-- Los indices filtrados (los que llevan WHERE) exigen estas dos opciones
-- activas. El driver JDBC las activa por omision, pero sqlcmd no: fijarlas aqui
-- hace que la migracion se comporte igual sea cual sea el cliente que la ejecute.
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO

-- -----------------------------------------------------------------------------
--  categoria - catalogo persistente consultado por el formulario de creacion
-- -----------------------------------------------------------------------------
CREATE TABLE categoria (
    id            UNIQUEIDENTIFIER NOT NULL CONSTRAINT pk_categoria PRIMARY KEY,
    codigo        VARCHAR(40)      NOT NULL,
    nombre        NVARCHAR(120)    NOT NULL,
    activa        BIT              NOT NULL CONSTRAINT df_categoria_activa DEFAULT (1),
    creada_en     DATETIME2(3)     NOT NULL CONSTRAINT df_categoria_creada DEFAULT (SYSUTCDATETIME()),
    CONSTRAINT uq_categoria_codigo UNIQUE (codigo)
);

-- -----------------------------------------------------------------------------
--  solicitud - agregado raiz
--
--  'version' soporta el bloqueo optimista de JPA (@Version). Es el unico
--  mecanismo de control de concurrencia del sistema: la segunda escritura
--  simultanea afecta cero filas y se traduce a 409 (escenario A2, ADR-004).
--
--  La regla "solo se puede tomar lo que esta REGISTRADA" NO se codifica aqui:
--  vive en el agregado. Duplicarla en una restriccion SQL crearia dos duenos
--  para la misma regla.
-- -----------------------------------------------------------------------------
CREATE TABLE solicitud (
    id              UNIQUEIDENTIFIER NOT NULL CONSTRAINT pk_solicitud PRIMARY KEY,
    codigo          VARCHAR(20)      NOT NULL,
    asunto          NVARCHAR(200)    NOT NULL,
    descripcion     NVARCHAR(2000)   NOT NULL,
    categoria_id    UNIQUEIDENTIFIER NOT NULL,
    prioridad       VARCHAR(10)      NOT NULL,
    estado          VARCHAR(20)      NOT NULL,
    solicitante_id  VARCHAR(80)      NOT NULL,
    analista_id     VARCHAR(80)      NULL,
    creada_en       DATETIME2(3)     NOT NULL,
    actualizada_en  DATETIME2(3)     NOT NULL,
    version         BIGINT           NOT NULL CONSTRAINT df_solicitud_version DEFAULT (0),
    CONSTRAINT uq_solicitud_codigo    UNIQUE (codigo),
    CONSTRAINT fk_solicitud_categoria FOREIGN KEY (categoria_id) REFERENCES categoria (id),
    CONSTRAINT ck_solicitud_prioridad CHECK (prioridad IN ('BAJA', 'MEDIA', 'ALTA')),
    CONSTRAINT ck_solicitud_estado    CHECK (estado IN ('REGISTRADA', 'EN_ATENCION', 'RESUELTA', 'CERRADA'))
);

-- -----------------------------------------------------------------------------
--  observacion - comentarios adjuntos a una solicitud
-- -----------------------------------------------------------------------------
CREATE TABLE observacion (
    id            UNIQUEIDENTIFIER NOT NULL CONSTRAINT pk_observacion PRIMARY KEY,
    solicitud_id  UNIQUEIDENTIFIER NOT NULL,
    texto         NVARCHAR(1000)   NOT NULL,
    actor_id      VARCHAR(80)      NOT NULL,
    actor_rol     VARCHAR(20)      NOT NULL,
    ocurrido_en   DATETIME2(3)     NOT NULL,
    CONSTRAINT fk_observacion_solicitud FOREIGN KEY (solicitud_id)
        REFERENCES solicitud (id) ON DELETE CASCADE,
    CONSTRAINT ck_observacion_rol CHECK (actor_rol IN ('SOLICITANTE', 'ANALISTA', 'SUPERVISOR'))
);

-- -----------------------------------------------------------------------------
--  cambio_estado - historial completo. Alimenta la linea de tiempo del detalle.
--
--  estado_origen es NULL en el registro inicial: no hay estado previo. El modelo
--  analitico lo proyecta como la transicion NULL -> REGISTRADA (BLUEPRINT 7.2).
-- -----------------------------------------------------------------------------
CREATE TABLE cambio_estado (
    id             UNIQUEIDENTIFIER NOT NULL CONSTRAINT pk_cambio_estado PRIMARY KEY,
    solicitud_id   UNIQUEIDENTIFIER NOT NULL,
    estado_origen  VARCHAR(20)      NULL,
    estado_destino VARCHAR(20)      NOT NULL,
    actor_id       VARCHAR(80)      NOT NULL,
    actor_rol      VARCHAR(20)      NOT NULL,
    motivo         NVARCHAR(500)    NULL,
    ocurrido_en    DATETIME2(3)     NOT NULL,
    CONSTRAINT fk_cambio_estado_solicitud FOREIGN KEY (solicitud_id)
        REFERENCES solicitud (id) ON DELETE CASCADE,
    CONSTRAINT ck_cambio_estado_rol CHECK (actor_rol IN ('SOLICITANTE', 'ANALISTA', 'SUPERVISOR'))
);

-- -----------------------------------------------------------------------------
--  outbox_evento - cola transaccional (Transactional Outbox)
--
--  Se escribe en la MISMA transaccion que el agregado. Un publicador agendado la
--  drena hacia RabbitMQ. Es tambien el log durable desde el cual se puede
--  reconstruir el modelo analitico.
-- -----------------------------------------------------------------------------
CREATE TABLE outbox_evento (
    id              UNIQUEIDENTIFIER NOT NULL CONSTRAINT pk_outbox_evento PRIMARY KEY,
    tipo            VARCHAR(60)      NOT NULL,
    version_evento  INT              NOT NULL CONSTRAINT df_outbox_version DEFAULT (1),
    agregado_id     UNIQUEIDENTIFIER NOT NULL,
    agregado_tipo   VARCHAR(40)      NOT NULL,
    routing_key     VARCHAR(80)      NOT NULL,
    payload         NVARCHAR(MAX)    NOT NULL,
    correlation_id  VARCHAR(60)      NOT NULL,
    estado          VARCHAR(15)      NOT NULL CONSTRAINT df_outbox_estado DEFAULT ('PENDIENTE'),
    intentos        INT              NOT NULL CONSTRAINT df_outbox_intentos DEFAULT (0),
    ultimo_error    NVARCHAR(1000)   NULL,
    ocurrido_en     DATETIME2(3)     NOT NULL,
    publicado_en    DATETIME2(3)     NULL,
    CONSTRAINT ck_outbox_estado CHECK (estado IN ('PENDIENTE', 'PUBLICADO', 'FALLIDO'))
);

-- -----------------------------------------------------------------------------
--  idempotencia_comando - soporte del header HTTP Idempotency-Key
--
--  Vive en el borde REST, no en el dominio: es una preocupacion del transporte
--  (ADR-010). Guarda el hash del cuerpo para distinguir un reintento legitimo
--  (misma llave, mismo cuerpo, misma respuesta) de una colision de llave
--  (misma llave con cuerpo distinto, que responde 409).
-- -----------------------------------------------------------------------------
CREATE TABLE idempotencia_comando (
    llave        VARCHAR(120)     NOT NULL CONSTRAINT pk_idempotencia_comando PRIMARY KEY,
    hash_cuerpo  VARCHAR(64)      NOT NULL,
    estado_http  INT              NOT NULL,
    respuesta    NVARCHAR(MAX)    NOT NULL,
    recurso_id   UNIQUEIDENTIFIER NULL,
    creada_en    DATETIME2(3)     NOT NULL,
    expira_en    DATETIME2(3)     NOT NULL
);

-- =============================================================================
--  Indices. Cada uno responde a una consulta concreta del sistema.
-- =============================================================================

-- Bandeja: listado filtrado por estado y ordenado por fecha descendente.
CREATE INDEX ix_solicitud_estado_creada
    ON solicitud (estado, creada_en DESC);

-- Filtro por rol SOLICITANTE: solo puede ver las propias.
CREATE INDEX ix_solicitud_solicitante
    ON solicitud (solicitante_id);

-- Filtro de bandeja por categoria.
CREATE INDEX ix_solicitud_categoria
    ON solicitud (categoria_id);

-- Bandeja del analista: "mis solicitudes en atencion".
CREATE INDEX ix_solicitud_analista
    ON solicitud (analista_id)
    WHERE analista_id IS NOT NULL;

-- Linea de tiempo del detalle, en orden cronologico.
CREATE INDEX ix_cambio_estado_solicitud
    ON cambio_estado (solicitud_id, ocurrido_en);

-- Observaciones del detalle, en orden cronologico.
CREATE INDEX ix_observacion_solicitud
    ON observacion (solicitud_id, ocurrido_en);

-- Poller del outbox: toma el lote mas antiguo pendiente. Es la consulta mas
-- frecuente del sistema (cada 500 ms), por eso el indice filtrado.
CREATE INDEX ix_outbox_pendientes
    ON outbox_evento (estado, ocurrido_en)
    WHERE estado = 'PENDIENTE';

-- Purga de llaves de idempotencia vencidas.
CREATE INDEX ix_idempotencia_expiracion
    ON idempotencia_comando (expira_en);
