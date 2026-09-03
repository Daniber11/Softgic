# BLUEPRINT TÉCNICO
## Plataforma de Gestión de Solicitudes Operacionales

> **Naturaleza de este documento.** Es la fuente de verdad técnica del proyecto.
> Toda decisión de implementación debe poder rastrearse hasta una sección de aquí.
> Si el código y este documento se contradicen, o se corrige el código o se
> actualiza el documento con un ADR que justifique el cambio. Nunca se deja la
> contradicción viva.

**Versión:** 1.1
**Alcance:** prueba técnica Full Stack — arquitectura hexagonal, microservicios, eventos y microfrontends.

> **v1.1 — 2 de septiembre de 2026.** Se resolvieron siete contradicciones e imprecisiones
> detectadas en la lectura previa a la construcción. El detalle está en el
> **§17 Registro de correcciones**, al final de este documento. Ninguna quedó viva.

---

# 1. Contexto y objetivo

Un cliente de gobierno necesita registrar, asignar y hacer seguimiento a solicitudes
operacionales internas. El sistema debe conservar trazabilidad completa de cada cambio,
restringir acciones por rol y habilitar lectura analítica del proceso.

**Lo que realmente se evalúa** (derivado de la rúbrica de 100 puntos):

| Prioridad | Qué | Por qué |
|---|---|---|
| 1 | Que arranque con un solo comando | El escenario A7 es la condición de entrada. Si no ejecuta, se desploma toda la nota. |
| 2 | Fronteras arquitectónicas verificables | 20 puntos. No basta con nombrar paquetes "domain". |
| 3 | Publicación confiable de eventos y consumo idempotente | 18 puntos + hasta 5 adicionales por Outbox bien implementado. |
| 4 | Seguridad en servidor, no solo en cliente | Umbral eliminatorio: mínimo 7/12. |
| 5 | Pruebas con valor real sobre dominio | 12 puntos, y la rúbrica advierte contra cobertura inflada. |

**Regla de oro del alcance:** la rúbrica dice textualmente que una funcionalidad demostrable
y sencilla puntúa mejor que una arquitectura extensa que no puede ejecutarse. Ante cualquier
disyuntiva entre elegancia y ejecutabilidad, gana la ejecutabilidad.

---

# 2. Principios de ingeniería

## 2.1 Regla de dependencia

Las dependencias apuntan siempre hacia el núcleo:

```
bootstrap ──▶ infrastructure ──▶ application ──▶ domain
                                                  ▲
                                     (no depende de nada)
```

Esto **no es una convención de carpetas**: son módulos Maven independientes. El `pom.xml`
del módulo `domain` no declara ninguna dependencia de producción. Ni Spring, ni JPA, ni
Jackson, ni Lombok. Además se verifica con una prueba ArchUnit que rompe el build si alguien
introduce un import prohibido.

## 2.2 SOLID, aterrizado al código

| Principio | Manifestación concreta y verificable |
|---|---|
| **SRP** | Un caso de uso por comando de negocio. No existe `SolicitudService` con doce métodos. El controlador solo traduce HTTP↔comando. |
| **OCP** | Agregar un estado o un evento no obliga a modificar consumidores: la tabla de transiciones vive en un único lugar y el sobre del evento está versionado. |
| **LSP** | Cada puerto de salida tiene implementación JPA en producción y *fake* en memoria en pruebas. Los casos de uso no notan la diferencia. |
| **ISP** | Puertos de entrada de un solo método. Ningún componente depende de operaciones que no invoca. |
| **DIP** | `application` declara las interfaces, `infrastructure` las implementa, `bootstrap` las cablea. El núcleo nunca importa el framework. |

## 2.3 Patrones de diseño y su justificación

| Patrón | Dónde | Por qué este y no otro |
|---|---|---|
| Ports & Adapters | Estructura global de Solicitudes | Requisito explícito del reto |
| Aggregate Root | `Solicitud` | Protege invariantes; imposible construir un estado inválido |
| Value Object | `SolicitudId`, `CategoriaId`, `Actor`, `Observacion` | Elimina el *primitive obsession* y valida en el borde del tipo |
| Factory Method | `Solicitud.registrar(...)`, `Solicitud.rehidratar(...)` | Separa creación de negocio de reconstrucción desde persistencia |
| State | Tabla de transiciones dentro del enum `EstadoSolicitud` | Con cuatro estados, una jerarquía de clases sería ceremonia sin beneficio. La rúbrica premia la simplicidad. |
| Command | Objetos inmutables de entrada a casos de uso | Desacopla la firma del caso de uso del contrato HTTP |
| Repository | Puertos de salida | Aísla el modelo de dominio del modelo relacional |
| Domain Events | Acumulados en el agregado, drenados por el caso de uso | El dominio expresa hechos sin conocer el broker |
| **Transactional Outbox** | Escritura de eventos en la misma transacción del agregado | Única forma de garantizar que no se publique un hecho no confirmado |
| **Idempotent Consumer** | Tabla `evento_procesado` en Indicadores | Resuelve el escenario A5 de forma determinista |
| CQRS ligero | Escritura por agregado, lectura por proyección | Indicadores *es* el read model; separa modelos por razón de cambio |
| Anti-corruption / Mapper | Entidad JPA ≠ modelo de dominio ≠ DTO | Tres representaciones porque tienen tres razones de cambio distintas |
| Container/Presentational | Frontend | Componentes de presentación puros y testeables |
| Facade | Capa de API del frontend sobre RTK Query | Las vistas no conocen la forma del backend |
| Module Federation | Shell + remoto | Arquitectura de plugins en tiempo de ejecución |

## 2.4 Clean code: reglas duras

- **Lenguaje ubicuo en español** para conceptos de dominio (`Solicitud`, `Analista`, `Prioridad`).
  Términos técnicos en inglés (`Repository`, `Adapter`, `Port`). No se mezclan dentro del mismo identificador.
- Cero lógica de negocio en controladores, entidades JPA o componentes React.
- Prohibido `catch (Exception e)`. Se capturan excepciones específicas o se dejan propagar.
- Sin números ni cadenas mágicas: constantes con nombre.
- Los comentarios explican **por qué**, jamás **qué**. Un comentario que describe lo que la
  línea siguiente hace es código mal nombrado.
- Métodos que caben en una pantalla. Máximo tres niveles de anidación.
- Nombres que revelan intención: `puedeTransicionarA` y no `check`.
- Las pruebas son documentación ejecutable: nombres en formato
  `debeRechazarTransicionDeResueltaARegistrada`.

---

# 3. Arquitectura de solución

## 3.1 Vista de contenedores

```
┌──────────────┐        ┌────────────────────┐
│  Shell (SPA) │◀──────▶│      Keycloak      │
│  React 19    │  OIDC  │   realm: solicitudes-gov
└──────┬───────┘        └─────────┬──────────┘
       │ Module Federation                 │ valida JWT (JWKS)
       ▼                                   │
┌──────────────┐                           │
│ MFE          │                           │
│ Indicadores  │                           │
└──────┬───────┘                           │
       │  HTTPS + Bearer                   │
       ├───────────────────────┬───────────┘
       ▼                       ▼
┌────────────────────┐   ┌────────────────────┐
│ Servicio           │   │ Servicio           │
│ Solicitudes  :8081 │   │ Indicadores  :8082 │
│ (hexagonal)        │   │ (en capas)         │
└────┬──────────┬────┘   └─────┬─────────┬────┘
     │          │ Outbox       │ consume │
     ▼          ▼              │         ▼
 solicitudes_db  ┌─────────────▼──┐  indicadores_db
   (SQL Server)  │   RabbitMQ     │   (SQL Server,
                 │ topic exchange │    esquema estrella)
                 └────────────────┘
```

## 3.2 Asimetría arquitectónica deliberada

**Servicio de Solicitudes: hexagonal estricto.** Tiene dominio real que proteger: máquina de
estados, reglas de autorización, invariantes, trazabilidad.

**Servicio de Indicadores: arquitectura en capas simple.** Es un proyector. No tiene reglas
de negocio, solo transformación de eventos a un modelo de lectura. Aplicar hexagonal aquí
sería sobreingeniería.

> **Esta asimetría es intencional y debe quedar registrada en un ADR.** Es un punto fuerte
> en la entrevista: demuestra criterio para aplicar arquitectura donde aporta y evitarla
> donde solo agrega ceremonia. La rúbrica penaliza explícitamente la arquitectura extensa
> sin justificación.

## 3.3 Estructura de paquetes del Servicio de Solicitudes

```
domain/
  co.gov.solicitudes.domain
    model/          Solicitud, EstadoSolicitud, Prioridad, Rol, Actor,
                    SolicitudId, CategoriaId, Categoria, Observacion, CambioEstado
    event/          EventoDominio (sealed) + 5 records de eventos
    exception/      DominioException y su jerarquía

application/
  co.gov.solicitudes.application
    port/in/        RegistrarSolicitudUseCase, TomarSolicitudUseCase,
                    ResolverSolicitudUseCase, DevolverSolicitudUseCase,
                    CerrarSolicitudUseCase, AgregarObservacionUseCase,
                    ConsultarSolicitudesQuery, ConsultarDetalleQuery,
                    ConsultarCategoriasQuery
    port/out/       SolicitudRepositoryPort, CategoriaRepositoryPort,
                    EventoPublicadorPort, RelojPort, GeneradorCodigoPort
    command/        Comandos inmutables de entrada
    service/        Implementaciones de los casos de uso
    result/         Objetos de salida de la capa de aplicación

infrastructure/
  co.gov.solicitudes.infrastructure
    adapter/in/rest/       Controladores, DTOs, mappers, manejador de errores,
                           interceptor de Idempotency-Key (ver §5.4)
    adapter/out/persistence/  Entidades JPA, repositorios Spring Data, mappers
    adapter/out/messaging/    Escritura y publicación del outbox
    adapter/out/clock/        Implementación de RelojPort
    security/                 Resource Server, conversor de authorities, CORS
    config/                   Configuración transversal

bootstrap/
  co.gov.solicitudes.SolicitudesApplication
  config/BeanConfiguration    Cableado explícito de casos de uso
  resources/
    application.yml
    db/migration/             Scripts Flyway versionados
```

**Regla de cableado:** los casos de uso son clases planas, sin anotaciones de Spring. Se
registran como beans con `@Bean` en `BeanConfiguration`. Esto hace visible el grafo de
dependencias y demuestra que la capa de aplicación es independiente del contenedor.

---

# 4. Stack tecnológico

> **Regla de versionado:** ninguna versión se fija de memoria. Antes de escribirla en un
> `pom.xml` o `package.json` se verifica que exista. Se prefiere la última estable menor
> dentro de la mayor indicada.

## Backend

| Elemento | Elección | Justificación |
|---|---|---|
| Java | 21 LTS | Records, sealed interfaces y pattern matching hacen el modelo de dominio expresivo sin Lombok |
| Spring Boot | 3.5.x | No se salta a 4.x: la madurez de springdoc y Karate manda cuando hay 24 h |
| Build | Maven multi-módulo | La separación física de módulos es la evidencia de las fronteras |
| Persistencia | Spring Data JPA + Hibernate | `@Version` para bloqueo optimista |
| Base de datos | SQL Server 2022 Developer | Exigido. Corre nativo bajo WSL2 |
| Migraciones | Flyway + `flyway-sqlserver` | SQL versionado legible como entregable |
| Mensajería | **RabbitMQ 3.13 (management)** | Quorum queues, DLX nativo, UI que sirve como evidencia visual en la demostración, y una huella de memoria muy inferior a Kafka |
| Documentación API | springdoc-openapi 2.8.x | OpenAPI generado desde el código, no escrito a mano |
| Identidad | Keycloak 26.x | Exigido. Realm exportado e importado al arrancar |
| Pruebas | JUnit 5, Mockito, AssertJ, **ArchUnit**, JaCoCo | ArchUnit es el diferenciador: convierte la arquitectura en algo que falla el build |
| Aceptación | Karate 1.4.x | Exigido |

## Frontend

| Elemento | Elección | Justificación |
|---|---|---|
| React + TypeScript | 19 / 5.x | Exigido |
| UI | MUI 7 + Emotion | Exigido |
| Bundler | Rspack + `@module-federation/enhanced` | Exigido. **Riesgo técnico más alto del proyecto** |
| Estado | Redux Toolkit + RTK Query | RTK Query entrega los estados de carga, vacío y error casi sin código |
| Validación | Zod + react-hook-form | Zod valida formularios y también el parseo de respuestas del backend |
| Pruebas | Vitest + React Testing Library | Exigido |
| Documentación | Storybook 8.x | Exigido: mínimo dos componentes reutilizables |

## Plataforma

Docker multi-stage con usuario no root · Docker Compose único en la raíz · Helm 3 ·
GitLab CI con Kaniko · Actuator + Micrometer + logs JSON correlacionados.

## Decisión: RabbitMQ sobre Kafka

| Criterio | RabbitMQ | Kafka |
|---|---|---|
| Memoria en local | ~200 MB | ~1 GB |
| Configuración en Compose | Trivial | KRaft, cluster id, advertised listeners |
| Evidencia visual en demo | UI de management muestra DLQ y reintentos | Requiere herramientas extra |
| Replay del modelo de lectura | No nativo | Sí, ventaja real |

Con el presupuesto de memoria de WSL2 compartido con SQL Server (2 GB), Keycloak y dos JVM,
y con la demostración en vivo como criterio de evaluación, RabbitMQ gana. La pérdida de
replay se compensa: el modelo analítico se reconstruye desde la tabla `outbox_evento`,
que es el log durable del sistema. **Esto hay que poder decirlo en la entrevista.**

---

# 5. Contratos HTTP

## 5.1 Endpoints

**Servicio de Solicitudes — puerto 8081**

| Método | Ruta | Rol | Éxito |
|---|---|---|---|
| POST | `/api/v1/solicitudes` | SOLICITANTE | 201 + `Location` |
| GET | `/api/v1/solicitudes` | todos (filtrado por rol) | 200 paginado |
| GET | `/api/v1/solicitudes/{id}` | según pertenencia/rol | 200 |
| POST | `/api/v1/solicitudes/{id}/asignaciones` | ANALISTA | 201 |
| POST | `/api/v1/solicitudes/{id}/transiciones` | ANALISTA / SUPERVISOR | 200 |
| POST | `/api/v1/solicitudes/{id}/observaciones` | ANALISTA / SUPERVISOR | 201 |
| GET | `/api/v1/categorias` | autenticado | 200 |

**Servicio de Indicadores — puerto 8082**

| Método | Ruta | Rol | Éxito |
|---|---|---|---|
| GET | `/api/v1/indicadores/resumen` | SUPERVISOR / ANALISTA | 200 |
| GET | `/api/v1/indicadores/tendencia` | SUPERVISOR / ANALISTA | 200 |

> **Consecuencia operativa.** No hay API Gateway en esta solución: el navegador habla
> directamente con los dos orígenes. Por lo tanto **CORS se configura en ambos servicios**,
> y ambos actúan como Resource Server validando el **mismo** JWT contra el JWKS de Keycloak.
> Omitir el CORS de Indicadores es el fallo que hace que la vista analítica muera en el
> navegador con un error que aparenta ser de red y no de configuración.

**Cuerpo de transiciones:**

```json
{ "accion": "RESOLVER | DEVOLVER | CERRAR", "motivo": "...", "observacion": "..." }
```

Una sola URI para todas las transiciones porque el recurso conceptual es *la transición*.
Mantiene la máquina de estados con un único punto de entrada y evita multiplicar endpoints
que harían lo mismo. Si se prefieren URIs por acción, el reto lo permite, pero hay que
justificarlo; esta es la justificación de la elección tomada.

## 5.2 Semántica de códigos

| Código | Significado en este sistema | Escenario |
|---|---|---|
| 400 | Error sintáctico: campo faltante, tipo inválido, UUID mal formado | |
| 401 | Sin token, token inválido o expirado | |
| 403 | Token válido, rol insuficiente | **A3** |
| 404 | No existe, o el solicitante consulta una solicitud ajena | |
| 409 | El estado cambió: otro analista ya la tomó | **A2** |
| 422 | Regla de dominio violada: transición no permitida | **A4** |

> La distinción entre 409 y 422 hay que poder defenderla: **409** significa "el estado cambió
> bajo tus pies, reintenta"; **422** significa "lo que pediste nunca fue posible, no reintentes".

**Nota de privacidad en 404 vs 403:** cuando un solicitante consulta una solicitud ajena se
devuelve 404, no 403, para no revelar la existencia del recurso.

## 5.3 Formato de error — RFC 9457 Problem Details

```json
{
  "type": "https://api.local/errors/transicion-invalida",
  "title": "Transición no permitida",
  "status": 422,
  "detail": "No se permite pasar de RESUELTA a REGISTRADA.",
  "instance": "/api/v1/solicitudes/8f3.../transiciones",
  "codigo": "TRANSICION_INVALIDA",
  "correlationId": "b1c2...",
  "timestamp": "2026-09-03T10:15:30Z"
}
```

**Catálogo de códigos:** `VALIDACION_DOMINIO`, `TRANSICION_INVALIDA`, `ACCION_NO_PERMITIDA`,
`SOLICITUD_NO_ENCONTRADA`, `CONFLICTO_CONCURRENCIA`, `CATEGORIA_INACTIVA`, `TOKEN_INVALIDO`.

**Regla:** ningún mensaje de error expone stack traces, nombres de tabla, SQL ni detalles
internos de infraestructura.

## 5.4 Paginación, filtros, versionado e idempotencia

- **Paginación:** `?page=0&size=20&sort=creadaEn,desc`. Respuesta con `content`, `page`,
  `size`, `totalElements`, `totalPages`. Tamaño máximo 100.
- **Filtros:** `estado`, `categoriaId`, `prioridad`, `desde`, `hasta`.
- **Versionado:** `/api/v1` en la URI para cambios rompientes. Los cambios aditivos
  (campos nuevos opcionales) no generan versión nueva.
- **Idempotencia HTTP:** header `Idempotency-Key` en `POST /solicitudes`. Se persiste
  llave + hash del cuerpo + respuesta emitida. Un reintento con la misma llave devuelve la
  misma respuesta sin crear duplicado; la misma llave con cuerpo distinto devuelve 409.
  **Se resuelve íntegramente en el adaptador REST** mediante un interceptor y la tabla
  `idempotencia_comando`. No existe `IdempotenciaPort`: `Idempotency-Key` es una
  preocupación del **transporte HTTP**, no del negocio, y modelarla como puerto de salida
  obligaría al núcleo a conocer un detalle de protocolo. Los casos de uso ignoran que
  existe. Registrado en **ADR-010**.
- `POST /asignaciones` es idempotente por naturaleza: si el mismo analista repite, devuelve
  el estado actual; si es otro, 409.

---

# 6. Contrato de eventos

## 6.1 Sobre común

```json
{
  "eventId": "uuid",
  "type": "SolicitudRegistrada",
  "version": 1,
  "occurredAt": "2026-09-03T14:31:07.482Z",
  "aggregateId": "uuid",
  "aggregateType": "Solicitud",
  "correlationId": "uuid",
  "causationId": "uuid",
  "producer": "solicitudes-service",
  "data": { }
}
```

El dominio **no conoce este sobre**. Emite solo el hecho de negocio; el adaptador de salida
lo envuelve al escribir en el outbox, tomando el `correlationId` del MDC de la petición.

## 6.2 Catálogo

| Evento | Routing key | Payload (`data`) |
|---|---|---|
| `SolicitudRegistrada` | `solicitud.registrada` | codigo, categoriaId, prioridad, solicitanteId |
| `SolicitudTomada` | `solicitud.tomada` | codigo, categoriaId, analistaId |
| `SolicitudResuelta` | `solicitud.resuelta` | codigo, categoriaId, analistaId |
| `SolicitudDevuelta` | `solicitud.devuelta` | codigo, categoriaId, supervisorId, motivo |
| `SolicitudCerrada` | `solicitud.cerrada` | codigo, categoriaId, supervisorId |

`SolicitudDevuelta` excede el mínimo exigido. Se incluye porque sin él el modelo analítico no
puede distinguir un reproceso de una resolución limpia. **Documentar como extensión propia.**

> **Precisión sobre datos personales.** El sobre del evento **sí transporta** identificadores
> de persona (`solicitanteId`, `analistaId`, `supervisorId`), porque el consumidor necesita
> saber *qué rol* actuó y esa información se deriva del actor. Lo que no ocurre es la
> **replicación**: la proyección analítica descarta el identificador y persiste únicamente el
> rol (§7.2). Afirmar "por el bus no viajan datos personales" sería falso y comprobable
> abriendo la tabla `outbox_evento`. La minimización se aplica **en la proyección**, que es
> donde el dato se volvería permanente. Registrado con esta redacción en **ADR-005**.

**Evolución de contratos:** solo se permiten cambios aditivos con campos opcionales dentro de
la misma `version`. Un cambio rompiente incrementa `version` y ambos contratos conviven
durante la transición.

## 6.3 Topología RabbitMQ

```
exchange: solicitudes.events        (topic, durable)
  └─ binding "solicitud.#" ──▶ cola: indicadores.solicitudes  (quorum, durable)
                                  ├─ x-dead-letter-exchange: solicitudes.events.dlx
                                  └─ 3 reintentos con backoff exponencial
exchange: solicitudes.events.dlx    (fanout)
  └──▶ cola: indicadores.solicitudes.dlq
```

Publisher confirms habilitados. La DLQ queda visible en la UI de RabbitMQ (puerto 15672):
es la evidencia que se muestra en vivo durante la entrevista.

## 6.4 Publicación confiable — Transactional Outbox

**Problema:** publicar antes del commit puede emitir un hecho que luego se revierte;
publicar después del commit puede perder el evento si el proceso muere en medio.

**Solución:** el caso de uso, en **una sola transacción**, persiste el agregado y escribe los
eventos en `outbox_evento`. Un publicador agendado (`@Scheduled`, cada 500 ms) toma lotes:

```sql
SELECT TOP (@lote) *
FROM outbox_evento WITH (UPDLOCK, READPAST)
WHERE estado = 'PENDIENTE'
ORDER BY ocurrido_en;
```

`UPDLOCK, READPAST` permite que varias instancias del servicio tomen lotes distintos sin
bloquearse entre sí: es la clave para que el patrón escale horizontalmente en SQL Server.
Tras el confirm del broker, la fila pasa a `PUBLICADO`.

**Garantía resultante:** *at-least-once*. Nunca se publica un hecho no confirmado, y nunca se
pierde un hecho confirmado. La duplicidad la absorbe el consumidor.

## 6.5 Consumo idempotente

```
Tabla evento_procesado (event_id UNIQUEIDENTIFIER PRIMARY KEY, procesado_en DATETIME2)
```

El consumidor, en una sola transacción: inserta el `eventId` y actualiza la proyección. Si el
evento ya llegó, la violación de clave primaria aborta la transacción, se hace `ack` y el
conteo no se altera. **Ese es exactamente el escenario A5.**

---

# 7. Modelo de datos

## 7.1 Operacional — `solicitudes_db`

| Tabla | Contenido | Notas |
|---|---|---|
| `categoria` | Catálogo persistente | `codigo` único, `activa` |
| `solicitud` | Agregado raíz | `codigo` único legible `SOL-2026-000123`; `version` para bloqueo optimista |
| `observacion` | Comentarios | FK a solicitud, cascada |
| `cambio_estado` | Historial de transiciones | actor, rol, motivo, fecha, estado origen y destino |
| `outbox_evento` | Cola transaccional | `estado`, `intentos`, `payload`, `ocurrido_en` |
| `idempotencia_comando` | Llaves de idempotencia HTTP | llave, hash del cuerpo, respuesta, expiración |

**Índices:**

```
UQ  solicitud(codigo)
IX  solicitud(estado, creada_en DESC)      -- bandeja
IX  solicitud(solicitante_id)              -- filtro por rol SOLICITANTE
IX  solicitud(categoria_id)                -- filtro
IX  cambio_estado(solicitud_id, ocurrido_en)
IX  outbox_evento(estado, ocurrido_en)     -- poller
```

**Semillas no sensibles:** categorías `SOPORTE_TECNICO`, `COORDINACION`, `INFRAESTRUCTURA`,
`ATENCION_CIUDADANA`, `OTROS`. Ningún dato real, personal ni institucional.

## 7.2 Analítico — `indicadores_db`, esquema estrella

```
                  dim_fecha
                      │
 dim_categoria ── hecho_transicion ── dim_estado
                      │
                   dim_rol
```

**Grano del hecho:** una fila por transición de estado ocurrida.

**El registro también es una fila.** `SolicitudRegistrada` no es una transición entre dos
estados: es una creación, no tiene estado de origen. Si se la deja fuera, cada solicitud
pierde su primera fila y el conteo por estado nunca cuadra con el operacional. Se modela
como la transición `NULL → REGISTRADA`, con una fila centinela en `dim_estado`
(`codigo = 'NINGUNO'`) que representa la ausencia de estado previo. Así `estado_origen_key`
nunca es nulo y las agregaciones no necesitan casos especiales.

**Medidas:** conteo de transiciones, y duración en minutos desde la transición anterior del
mismo agregado (habilita tiempo medio de atención). En la fila de registro la duración es
nula, no cero: no hubo transición previa desde la cual medir.

> **Decisión de privacidad:** la dimensión es **rol, no persona**. No se replica `actor_id` ni
> ningún dato identificable al modelo analítico. Responde directamente al requisito
> "evite replicar datos personales innecesarios" y es un buen punto de conversación en la
> entrevista.

**Consultas expuestas:** solicitudes por estado, solicitudes por categoría, tendencia diaria.

**Reconstrucción:** el modelo de lectura se puede reconstruir reproyectando desde
`outbox_evento`, que actúa como log durable. Debe existir el procedimiento documentado
aunque no se automatice.

---

# 8. Seguridad

## 8.1 Configuración de identidad

Realm `solicitudes-gov`, exportado a JSON e importado con `--import-realm`.

| Elemento | Configuración |
|---|---|
| Roles de realm | `SOLICITANTE`, `ANALISTA`, `SUPERVISOR` |
| Cliente `shell-web` | Público, Authorization Code + PKCE S256, sin secreto, redirect a 3000 y 3001 |
| Cliente `karate-e2e` | Público, solo direct grant, **exclusivo para pruebas**, documentado como tal |
| Usuarios de prueba | `solicitante1`, `analista1`, `analista2`, `supervisor1`, `sinrol1` |

`analista2` existe para demostrar A2 (dos analistas compitiendo). `sinrol1` existe para
demostrar A3 (403 sin efectos).

## 8.2 Defensa en profundidad

La autorización se valida en **tres capas**:

1. **Filtro de seguridad** — `SecurityFilterChain` restringe rutas por authority.
2. **Borde de aplicación** — `@PreAuthorize` sobre el adaptador de entrada.
3. **Dominio** — el agregado lanza `AccionNoPermitidaException` si el `Actor` no tiene el rol.

La tercera capa es la que garantiza que la regla sobreviva si mañana llega un comando por un
canal distinto de REST. Es también la razón por la que A3 no persiste nada ni emite eventos.

## 8.3 Manejo en el cliente

- El token vive **en memoria** del shell. Nunca en `localStorage` ni `sessionStorage`.
- Refresco silencioso; si falla, redirección a login **preservando la ruta destino**.
- `401` dispara reautenticación. `403` renderiza una vista de autorización insuficiente,
  nunca una pantalla en blanco ni un error genérico.
- El menú y los botones se ocultan por rol, pero eso es **usabilidad, no seguridad**: la
  autorización real siempre está en el servidor.

## 8.4 Secretos y CORS

CORS restringido por lista de orígenes configurable, nunca `*` con credenciales.
Ningún secreto en el repositorio: `.env.example` con valores ficticios, `.env` en
`.gitignore`, y `existingSecret` en Helm. Las credenciales de demo se documentan
explícitamente como locales y ficticias.

---

# 9. Frontend y microfrontends

## 9.1 Reparto de responsabilidades

| Aplicación | Responsabilidad |
|---|---|
| **Shell** (host, :3000) | Enrutamiento, sesión OIDC, layout, tema MUI, store raíz, error boundary |
| **MFE Indicadores** (remoto, :3001) | Vista analítica. Expone `./IndicadoresApp`. Arranca standalone con bootstrap propio |

La frontera del microfrontend coincide con la frontera del microservicio. Es la justificación
arquitectónica limpia: cada equipo posee un vertical completo.

## 9.2 El punto crítico: sesión compartida

El shell posee **la única instancia** de Keycloak y expone por federación un `authBridge`
con `getToken()` y `getUser()`. El remoto lo consume. En modo standalone el remoto usa su
propio proveedor. **Nunca existen dos instancias OIDC compitiendo por el mismo token.**

## 9.3 Módulos compartidos como singleton

```
react, react-dom, react-router-dom,
@mui/material, @emotion/react, @emotion/styled,
@reduxjs/toolkit, react-redux
```

Todos con `singleton: true` y `requiredVersion` estricta. **Emotion duplicado es la causa
número uno de fallos en federación con MUI**, y se manifiesta como estilos que desaparecen
o errores crípticos de contexto en tiempo de ejecución.

## 9.4 Vistas mínimas

| Vista | Contenido |
|---|---|
| Login / redirección | Manejo de retorno a la ruta solicitada |
| Bandeja | Tabla con filtros (estado, categoría, prioridad) y paginación servidor |
| Creación | Formulario validado con Zod + react-hook-form |
| Detalle | Datos, observaciones, línea de tiempo del historial, acciones según rol |
| Resumen analítico | Conteos por estado, por categoría y tendencia diaria (MFE remoto) |

**Cada vista implementa cuatro estados explícitos:** cargando, vacío, error con acción de
reintento, y autorización insuficiente. Se encapsulan en un componente `EstadoVista`
reutilizable, que además es uno de los dos documentados en Storybook.

## 9.5 Accesibilidad

Navegación completa por teclado con orden de tabulación coherente · `aria-label` en acciones
sin texto · foco gestionado al abrir diálogos y devuelto al cerrarlos · roles semánticos en
tablas · contraste conforme a WCAG AA · mensajes de error asociados a los campos con
`aria-describedby` · anuncios de cambio de estado en regiones `aria-live`.

## 9.6 Storybook

Mínimo dos componentes reutilizables con estados representativos:
`EstadoChip` (los cuatro estados y las tres prioridades) y `EstadoVista`
(cargando, vacío, error, sin autorización).

---

# 10. Estrategia de pruebas

| Nivel | Herramienta | Qué cubre | Criterio |
|---|---|---|---|
| **Dominio** | JUnit 5 puro, sin mocks | Matriz completa de transiciones, rol requerido por acción, invariantes de creación | Aquí vive la cobertura que vale |
| **Casos de uso** | JUnit + Mockito | Orquestación, escritura al outbox, propagación de errores | Un test por escenario, no por método |
| **Arquitectura** | ArchUnit | Regla de dependencia, prohibición de imports de framework en el núcleo, convenciones de nombres | **Falla el build si se viola** |
| **Integración** | Slices de Spring | Repositorio, bloqueo optimista bajo concurrencia, publicador outbox | |
| **Aceptación** | Karate | A1, A3 y el recorrido REGISTRADA → EN_ATENCION → RESUELTA | Contra el stack de Compose |
| **Frontend** | Vitest + RTL | Guard por rol, validación Zod, render de la línea de tiempo, estados de vista | |

**JaCoCo:** exclusiones honestas de configuración, DTOs y entidades JPA, para que la cifra
refleje dominio y casos de uso. La rúbrica advierte explícitamente contra la cobertura
inflada por código trivial. Es preferible reportar 65 % real sobre dominio que 85 % diluido.

**Estrategia Karate a documentar:** obtiene tokens del cliente `karate-e2e` por direct grant
contra el Keycloak del stack local, y ejecuta contra los servicios levantados por Compose.

---

# 11. Contenedores, Kubernetes y CI/CD

## 11.1 Docker

Multi-stage: build con `maven:3.9-eclipse-temurin-21`, runtime con JRE 21 alpine, usuario no
root, `HEALTHCHECK` apuntando a Actuator. Frontend construido con Node y servido por nginx
con configuración de SPA (fallback a `index.html`).

## 11.2 Compose

**`compose.yaml` vive en la raíz del repositorio y ningún servicio declara perfiles.** El
comando canónico es exactamente el que exige el enunciado:

```bash
docker compose up --build
```

Se descartaron los perfiles `infra` / `full`. Un servicio con `profiles: [full]` **no
arranca** con `docker compose up`, de modo que esa configuración habría dejado los dos
servicios y el frontend abajo justo en el comando del que depende el escenario A7: el
evaluador habría visto un stack a medias. Para desarrollar contra dependencias sueltas no
hace falta ningún perfil, basta nombrar los servicios:

```bash
docker compose up -d sqlserver rabbitmq keycloak db-init
```

Menos piezas, y el comando del README es idéntico al del enunciado.

Healthchecks en todos los servicios y `depends_on: condition: service_healthy` para que el
arranque sea determinista. **El escenario A7 es literalmente esto.**

Puertos: 1433 SQL Server · 5672/15672 RabbitMQ · 8080 Keycloak · 8081 Solicitudes ·
8082 Indicadores · 3000 Shell · 3001 MFE.

## 11.3 Helm

Un chart con templates por componente. `values-dev.yaml` y `values-qa.yaml`.
Probes de liveness y readiness sobre Actuator, `requests` y `limits` declarados,
ConfigMap para configuración y referencias a Secret existente para credenciales.
Validación en CI con `helm lint` y `helm template`.

## 11.4 GitLab CI

```
lint → build → test → coverage → package (Kaniko) → helm-validate → deploy (manual)
```

Caché de `~/.m2` y `node_modules`. Kaniko en lugar de Docker-in-Docker por seguridad.
La promoción entre ambientes se documenta aunque el despliegue quede manual.

## 11.5 Observabilidad

Actuator (`health`, `info`, `metrics`, `prometheus`) · Micrometer · logs estructurados en
JSON con `correlationId` en MDC, propagado al sobre del evento y devuelto en el header de
respuesta. Un `TraceFilter` genera o propaga el `X-Correlation-Id`.

Esto entra en los hasta 5 puntos adicionales junto con el Outbox.

---

# 12. Trazabilidad de escenarios de aceptación

| ID | Escenario | Mecanismo que lo garantiza | Prueba que lo demuestra |
|---|---|---|---|
| **A1** | Registro válido | Caso de uso + outbox en la misma transacción | Karate + test de caso de uso |
| **A2** | Doble toma | **Bloqueo optimista con `@Version`**, único mecanismo. La segunda escritura afecta 0 filas → `OptimisticLockingFailureException` → 409 | Test de integración concurrente |
| **A3** | Sin rol intenta cerrar | Filtro + `@PreAuthorize` + validación en agregado; sin persistencia ni evento | Karate + test de dominio |
| **A4** | RESUELTA → REGISTRADA | Tabla de transiciones en el enum → 422 con Problem Details | Test de dominio |
| **A5** | Evento duplicado | `evento_procesado` con PK en `eventId` en la misma transacción que la proyección | Test de integración del consumidor |
| **A6** | Recarga en detalle | Rehidratación de sesión al montar el shell + refetch de RTK Query | Test de Vitest |
| **A7** | Clonar y levantar | Healthchecks encadenados; `docker compose up --build` como único comando | Verificación manual documentada |

> **Por qué A2 usa un solo mecanismo.** La versión 1.0 describía `@Version` **y** un
> `UPDATE ... WHERE estado='REGISTRADA'` con verificación de filas afectadas. Son dos
> soluciones distintas al mismo problema y tenerlas juntas es indefendible en entrevista:
> no se puede responder cuál está protegiendo realmente. Se conserva **solo `@Version`**
> por tres razones. Es una anotación que Hibernate aplica a **todas** las transiciones, no
> solo a la toma —el `UPDATE` condicional habría que repetirlo a mano para resolver,
> devolver y cerrar—. Es determinista y se demuestra con un test de dos hilos. Y sobre
> todo: la condición `WHERE estado='REGISTRADA'` **es una regla de negocio escrita en SQL**,
> es decir, la máquina de estados filtrándose desde el agregado hacia la capa de
> infraestructura. Esa regla tiene un único dueño, `Solicitud`, y duplicarla en una
> sentencia garantiza que algún día las dos copias discrepen en silencio. Registrado en
> **ADR-004**.

---

# 13. ADRs a redactar

Uno por archivo en `docs/adr/`, formato: contexto · decisión · alternativas consideradas ·
consecuencias.

| ID | Decisión |
|---|---|
| ADR-001 | RabbitMQ sobre Kafka |
| ADR-002 | Transactional Outbox sobre publicación directa o CDC |
| ADR-003 | Hexagonal solo en Solicitudes; en capas en Indicadores |
| ADR-004 | Bloqueo optimista sobre pesimista para la concurrencia |
| ADR-005 | Dimensión rol en lugar de persona en el modelo analítico |
| ADR-006 | Una URI de transiciones en lugar de un endpoint por acción |
| ADR-007 | Keycloak en `start-dev` con H2 para local |
| ADR-008 | Módulos Maven separados como mecanismo de frontera |
| ADR-009 | Distinción semántica entre 409 y 422 |
| ADR-010 | Idempotencia HTTP resuelta en el adaptador REST y no como puerto de aplicación |
| ADR-011 | Compose único en la raíz sin perfiles, para que `docker compose up --build` sea suficiente |

---

# 14. Entregables

| # | Entregable | Ubicación |
|---|---|---|
| 1 | Repositorio Git privado, acceso al equipo evaluador | — |
| 2 | Arranque con `docker compose up --build` | `deploy/compose/` |
| 3 | README completo | `README.md` |
| 4 | Diagramas C4 (contexto y contenedores) + secuencia | `docs/c4/` |
| 5 | OpenAPI, contratos de evento con ejemplos, migraciones, modelo analítico | `docs/`, `db/migration/` |
| 6 | Dockerfiles, Compose, Helm, `.gitlab-ci.yml` | `deploy/`, raíz |
| 7 | Evidencia de pruebas y cobertura | `docs/evidencias/` |
| 8 | `USO_DE_IA.md` | raíz |

**Estructura obligatoria del README:** resumen · arquitectura · decisiones (ADR) · seguridad ·
contratos · modelo de datos · ejecución local · pruebas · CI/CD · observabilidad ·
limitaciones y trabajo pendiente.

---

# 15. Gestión de riesgos

| Riesgo | Probabilidad | Impacto | Mitigación |
|---|---|---|---|
| Module Federation con MUI 7 y React 19 falla por Emotion duplicado | Alta | Alto | **Se prueba en un "hola mundo" federado antes de escribir cualquier vista.** Si falla, se documenta y se ajusta la configuración de `shared` |
| SQL Server agota la memoria de WSL2 | Media | Alto | `mem_limit: 2g`, heaps de JVM acotados, WSL configurado a 8 GB |
| Arranque en frío no determinista | Media | Crítico | Healthchecks encadenados con `service_healthy` |
| Alcance mayor que el tiempo | Alta | Medio | Recorte declarado en el README como "diseñado, no implementado", que el reto permite |
| Versiones inexistentes fijadas de memoria | Media | Medio | Verificación obligatoria antes de fijar cualquier versión |

---

# 16. Política de recorte

Si el tiempo aprieta, se recorta **en este orden** y se documenta cada recorte en la sección
"limitaciones y trabajo pendiente" del README:

1. Observabilidad avanzada (Prometheus y Grafana en Compose)
2. Storybook más allá de los dos componentes exigidos
3. Pruebas de integración con Testcontainers
4. Chart de Helm reducido a un solo servicio con el segundo documentado
5. Vistas secundarias del frontend

**Nunca se recorta:** el arranque con un comando, la seguridad en servidor, el Outbox, la
idempotencia del consumidor, ni las pruebas de dominio. Son el núcleo evaluado y los
umbrales eliminatorios.

---

# 17. Registro de correcciones (v1.0 → v1.1)

Correcciones aplicadas el 2 de septiembre de 2026, tras la lectura completa del enunciado y
del blueprint previa a la construcción. Se documentan porque este archivo declara que una
contradicción entre código y documento nunca se deja viva: la trazabilidad de la corrección
es parte del entregable.

| # | Contradicción o imprecisión en v1.0 | Resolución en v1.1 | Sección |
|---|---|---|---|
| C1 | El Compose vivía en `deploy/compose/` con perfiles `infra` y `full`. `docker compose up --build` desde la raíz habría fallado, y con el perfil `full` las aplicaciones no habrían arrancado: A7 caía. | `compose.yaml` en la raíz, sin perfiles. Modo dependencias nombrando servicios. | §11.2, ADR-011 |
| C2 | A2 describía `@Version` **y** `UPDATE ... WHERE estado='REGISTRADA'`. Dos mecanismos para el mismo problema, y ADR-004 declaraba solo el optimista. | Únicamente `@Version`. El `UPDATE` condicional filtraba la máquina de estados hacia SQL. | §12, ADR-004 |
| C3 | Los endpoints de indicadores figuraban en la tabla del Servicio de Solicitudes, sugiriendo un solo origen HTTP. | Tablas separadas por servicio y puerto, con la consecuencia explícita: **CORS en ambos servicios**. | §5.1 |
| C4 | "Evite replicar datos personales" se leía como que por el bus no viajan. Los eventos sí los transportan. | Precisión: el sobre los transporta, la **proyección** los descarta. La minimización ocurre donde el dato se vuelve permanente. | §6.2, §7.2, ADR-005 |
| C5 | El grano "una fila por transición" excluía `SolicitudRegistrada`, que no tiene estado de origen. Cada solicitud habría perdido su primera fila. | Se modela como `NULL → REGISTRADA` con fila centinela `dim_estado.codigo = 'NINGUNO'`. Duración nula, no cero. | §7.2 |
| C6 | `IdempotenciaPort` figuraba como puerto de salida de `application`, obligando al núcleo a conocer un detalle del protocolo HTTP. | Se elimina el puerto. `Idempotency-Key` se resuelve en el adaptador REST con un interceptor. | §3.3, §5.4, ADR-010 |
| C7 | Los comandos de `CLAUDE.md` §5 asumían Maven, JDK 21, pnpm y Helm instalados en la máquina anfitriona. Ninguno lo está. | Toolchain contenerizado: Maven y Helm por imagen Docker, pnpm por `corepack`. No se instala nada en el anfitrión. | `CLAUDE.md` §5 |

**Restricción de entorno registrada:** el anfitrión dispone de 8 GB para Docker y 16 CPU.
El presupuesto obliga a `mem_limit: 2g` en SQL Server y heaps de JVM acotados
(`-XX:MaxRAMPercentage`) en ambos servicios. Es un riesgo real de arranque simultáneo y se
vigila en la Fase 1.
