-- =============================================================================
--  V1 - Modelo analitico, esquema en estrella
--
--  MIGRACION INMUTABLE. Un cambio posterior entra como migracion nueva.
--
--  GRANO DEL HECHO: una fila por transicion de estado ocurrida.
--
--  DECISION DE PRIVACIDAD (ADR-005): la dimension de actor es el ROL, nunca la
--  persona. El evento que llega por el bus si transporta identificadores como
--  analistaId o solicitanteId, porque el productor los necesita para derivar el
--  rol; esta proyeccion los DESCARTA y no los persiste. La minimizacion se
--  aplica donde el dato se volveria permanente, que es aqui.
--
--  Se conserva solicitud_id porque sin el no se puede calcular la duracion
--  entre transiciones del mismo expediente. Es el identificador de un caso, no
--  de una persona.
-- =============================================================================

SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO

-- -----------------------------------------------------------------------------
--  dim_fecha - calendario. Clave entera AAAAMMDD, legible al inspeccionar datos.
-- -----------------------------------------------------------------------------
CREATE TABLE dim_fecha (
    fecha_key     INT          NOT NULL CONSTRAINT pk_dim_fecha PRIMARY KEY,
    fecha         DATE         NOT NULL,
    anio          SMALLINT     NOT NULL,
    mes           TINYINT      NOT NULL,
    dia           TINYINT      NOT NULL,
    dia_semana    TINYINT      NOT NULL,
    CONSTRAINT uq_dim_fecha_fecha UNIQUE (fecha)
);

-- -----------------------------------------------------------------------------
--  dim_estado
--
--  Incluye la fila centinela NINGUNO. El registro de una solicitud no es una
--  transicion entre dos estados: no tiene origen. Sin este centinela habria que
--  admitir nulos en estado_origen_key y cada consulta necesitaria un caso
--  especial; peor aun, si se dejara fuera el registro, cada expediente perderia
--  su primera fila y los conteos nunca cuadrarian con el modelo operacional.
-- -----------------------------------------------------------------------------
CREATE TABLE dim_estado (
    estado_key  INT         NOT NULL CONSTRAINT pk_dim_estado PRIMARY KEY,
    codigo      VARCHAR(20) NOT NULL,
    CONSTRAINT uq_dim_estado_codigo UNIQUE (codigo)
);

INSERT INTO dim_estado (estado_key, codigo) VALUES
    (0, 'NINGUNO'),
    (1, 'REGISTRADA'),
    (2, 'EN_ATENCION'),
    (3, 'RESUELTA'),
    (4, 'CERRADA');

-- -----------------------------------------------------------------------------
--  dim_rol - quien ejecuto la transicion, por rol y nunca por persona.
-- -----------------------------------------------------------------------------
CREATE TABLE dim_rol (
    rol_key  INT         NOT NULL CONSTRAINT pk_dim_rol PRIMARY KEY,
    codigo   VARCHAR(20) NOT NULL,
    CONSTRAINT uq_dim_rol_codigo UNIQUE (codigo)
);

INSERT INTO dim_rol (rol_key, codigo) VALUES
    (1, 'SOLICITANTE'),
    (2, 'ANALISTA'),
    (3, 'SUPERVISOR');

-- -----------------------------------------------------------------------------
--  dim_categoria
--
--  Se replica el catalogo en la base analitica. Es lo normal en un esquema en
--  estrella: la dimension vive junto al hecho para que las consultas no crucen
--  bases ni servicios. La fila DESCONOCIDA absorbe eventos que referencian una
--  categoria aun no replicada, de modo que un catalogo desactualizado degrade
--  la calidad del reporte en lugar de tumbar al consumidor.
-- -----------------------------------------------------------------------------
CREATE TABLE dim_categoria (
    categoria_key  INT              NOT NULL IDENTITY(1,1) CONSTRAINT pk_dim_categoria PRIMARY KEY,
    categoria_id   UNIQUEIDENTIFIER NOT NULL,
    codigo         VARCHAR(40)      NOT NULL,
    nombre         NVARCHAR(120)    NOT NULL,
    CONSTRAINT uq_dim_categoria_id UNIQUE (categoria_id)
);

INSERT INTO dim_categoria (categoria_id, codigo, nombre) VALUES
    ('00000000-0000-0000-0000-000000000000', 'DESCONOCIDA',        N'Categoria no replicada'),
    ('11111111-1111-4111-8111-111111111111', 'SOPORTE_TECNICO',    N'Soporte tecnico'),
    ('22222222-2222-4222-8222-222222222222', 'COORDINACION',       N'Coordinacion interna'),
    ('33333333-3333-4333-8333-333333333333', 'INFRAESTRUCTURA',    N'Infraestructura'),
    ('44444444-4444-4444-8444-444444444444', 'ATENCION_CIUDADANA', N'Atencion ciudadana'),
    ('55555555-5555-4555-8555-555555555555', 'OTROS',              N'Otros'),
    ('66666666-6666-4666-8666-666666666666', 'HISTORICO_ARCHIVADO', N'Historico archivado (inactiva)');

-- -----------------------------------------------------------------------------
--  hecho_transicion - tabla de hechos
--
--  Medidas: el conteo de filas, y duracion_minutos, que es el tiempo desde la
--  transicion anterior del mismo expediente. En la fila de registro la duracion
--  es NULL y no cero: no hubo transicion previa desde la cual medir, y un cero
--  contaminaria cualquier promedio.
-- -----------------------------------------------------------------------------
CREATE TABLE hecho_transicion (
    id                  BIGINT           NOT NULL IDENTITY(1,1) CONSTRAINT pk_hecho_transicion PRIMARY KEY,
    solicitud_id        UNIQUEIDENTIFIER NOT NULL,
    solicitud_codigo    VARCHAR(20)      NOT NULL,
    fecha_key           INT              NOT NULL,
    categoria_key       INT              NOT NULL,
    estado_origen_key   INT              NOT NULL,
    estado_destino_key  INT              NOT NULL,
    rol_key             INT              NOT NULL,
    duracion_minutos    INT              NULL,
    ocurrido_en         DATETIME2(3)     NOT NULL,
    CONSTRAINT fk_hecho_fecha     FOREIGN KEY (fecha_key)          REFERENCES dim_fecha (fecha_key),
    CONSTRAINT fk_hecho_categoria FOREIGN KEY (categoria_key)      REFERENCES dim_categoria (categoria_key),
    CONSTRAINT fk_hecho_origen    FOREIGN KEY (estado_origen_key)  REFERENCES dim_estado (estado_key),
    CONSTRAINT fk_hecho_destino   FOREIGN KEY (estado_destino_key) REFERENCES dim_estado (estado_key),
    CONSTRAINT fk_hecho_rol       FOREIGN KEY (rol_key)            REFERENCES dim_rol (rol_key)
);

-- -----------------------------------------------------------------------------
--  evento_procesado - consumidor idempotente
--
--  ESTA TABLA ES EL ESCENARIO A5 COMPLETO.
--
--  El consumidor inserta aqui el eventId en la MISMA transaccion en que
--  actualiza la proyeccion. Si el evento ya llego, la violacion de clave
--  primaria aborta la transaccion entera: la fila del hecho no se escribe, el
--  conteo no se altera, y el mensaje se confirma igualmente para que el broker
--  no lo reintente para siempre.
--
--  No hace falta consultar antes de insertar. Comprobar y luego insertar seria
--  una condicion de carrera; dejar que la clave primaria falle es atomico por
--  construccion.
-- -----------------------------------------------------------------------------
CREATE TABLE evento_procesado (
    event_id      UNIQUEIDENTIFIER NOT NULL CONSTRAINT pk_evento_procesado PRIMARY KEY,
    tipo          VARCHAR(60)      NOT NULL,
    procesado_en  DATETIME2(3)     NOT NULL
);

-- =============================================================================
--  Indices. Cada uno responde a una consulta expuesta por el API.
-- =============================================================================

-- Tendencia diaria: agrupa por fecha.
CREATE INDEX ix_hecho_fecha
    ON hecho_transicion (fecha_key, estado_destino_key);

-- Resumen por categoria.
CREATE INDEX ix_hecho_categoria
    ON hecho_transicion (categoria_key, estado_destino_key);

-- Estado actual de cada expediente: se toma la ultima transicion por solicitud.
CREATE INDEX ix_hecho_solicitud
    ON hecho_transicion (solicitud_id, ocurrido_en DESC);
