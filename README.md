# Plataforma de Gestión de Solicitudes Operacionales

Solución a la prueba técnica Full Stack para un cliente de gobierno: registro, asignación y
seguimiento de solicitudes operacionales internas, con trazabilidad completa por rol y lectura
analítica del proceso.

---

## Índice

1. [Resumen](#1-resumen)
2. [Arquitectura](#2-arquitectura)
3. [Decisiones de arquitectura (ADR)](#3-decisiones-de-arquitectura-adr)
4. [Seguridad](#4-seguridad)
5. [Contratos](#5-contratos)
6. [Modelo de datos](#6-modelo-de-datos)
7. [Ejecución local](#7-ejecución-local)
8. [Pruebas](#8-pruebas)
9. [CI/CD](#9-cicd)
10. [Observabilidad](#10-observabilidad)
11. [Limitaciones y trabajo pendiente](#11-limitaciones-y-trabajo-pendiente)

---

## 1. Resumen

Un cliente de gobierno necesita registrar, asignar y hacer seguimiento a solicitudes
operacionales internas, con trazabilidad completa de cada cambio, autorización restringida por
rol, y una vista analítica del proceso. La solución se compone de:

- **Servicio de Solicitudes** (Spring Boot, arquitectura hexagonal estricta): dueño del
  agregado `Solicitud`, su máquina de estados y su historial.
- **Servicio de Indicadores** (Spring Boot, arquitectura en capas): proyector de eventos a un
  modelo de lectura en esquema estrella, sin reglas de negocio propias.
- **Shell** (React 19, host de Module Federation): sesión OIDC, enrutamiento, layout.
- **MFE Indicadores** (React 19, remoto federado): vista analítica, consumida en tiempo de
  ejecución por el Shell.

Los dos backends se comunican de forma asíncrona a través de **RabbitMQ**, con publicación
confiable (Transactional Outbox) y consumo idempotente. La identidad la gestiona **Keycloak**.
Todo el stack arranca con un único comando.

## 2. Arquitectura

```bash
docker compose up --build
```

### 2.1 Vista de contenedores

Ver [`docs/c4/02-contenedores.md`](docs/c4/02-contenedores.md) para el diagrama completo (C4,
Mermaid). Resumen textual:

```
Shell (:3000) ──Module Federation──▶ MFE Indicadores (:3001)
  │                                        │
  │ OIDC                                   │ OIDC (federado: authBridge / standalone: propio)
  ▼                                        ▼
Keycloak (:8080) ◀──────── valida JWT ─────┤
  │
  ├─ HTTPS+Bearer ──▶ Servicio Solicitudes (:8081, hexagonal)
  │                        │
  │                        ├─▶ solicitudes_db (SQL Server)
  │                        └─▶ RabbitMQ (outbox) ──▶ Servicio Indicadores (:8082, en capas)
  │                                                        │
  └─ HTTPS+Bearer ──▶ ─────────────────────────────────────┴─▶ indicadores_db (SQL Server)
```

No hay API Gateway: el navegador habla directamente con ambos backends, por lo que **CORS se
configura en los dos servicios**.

### 2.2 Asimetría arquitectónica deliberada

**Solicitudes es hexagonal estricto** (`domain` → `application` → `infrastructure` →
`bootstrap`, cuatro módulos Maven, regla de dependencia verificada con ArchUnit). Tiene dominio
real que proteger: máquina de estados, autorización, invariantes.

**Indicadores es arquitectura en capas simple** (un solo módulo Maven: `consumer`/`web` →
`service` → `persistence`). Es un proyector sin una sola regla de negocio propia; aplicar
hexagonal ahí sería ceremonia sin beneficio. Ver [ADR-003](docs/adr/ADR-003-asimetria-arquitectonica.md).

### 2.3 Diagramas

| Diagrama | Ubicación |
|---|---|
| Contexto (C4 nivel 1) | [`docs/c4/01-contexto.md`](docs/c4/01-contexto.md) |
| Contenedores (C4 nivel 2) | [`docs/c4/02-contenedores.md`](docs/c4/02-contenedores.md) |
| Secuencia del flujo principal, con eventos | [`docs/c4/03-secuencia-flujo-principal.md`](docs/c4/03-secuencia-flujo-principal.md) |

## 3. Decisiones de arquitectura (ADR)

Once decisiones registradas en `docs/adr/`, formato contexto · decisión · alternativas
consideradas · consecuencias:

| ADR | Decisión |
|---|---|
| [001](docs/adr/ADR-001-rabbitmq-sobre-kafka.md) | RabbitMQ sobre Kafka |
| [002](docs/adr/ADR-002-transactional-outbox.md) | Transactional Outbox sobre publicación directa o CDC |
| [003](docs/adr/ADR-003-asimetria-arquitectonica.md) | Hexagonal solo en Solicitudes; en capas en Indicadores |
| [004](docs/adr/ADR-004-bloqueo-optimista.md) | Bloqueo optimista sobre pesimista para la concurrencia (A2) |
| [005](docs/adr/ADR-005-dimension-rol-no-persona.md) | Dimensión rol en lugar de persona en el modelo analítico |
| [006](docs/adr/ADR-006-una-uri-de-transiciones.md) | Una URI de transiciones en lugar de un endpoint por acción |
| [007](docs/adr/ADR-007-keycloak-start-dev.md) | Keycloak en `start-dev` con H2 para local |
| [008](docs/adr/ADR-008-modulos-maven-como-frontera.md) | Módulos Maven separados como mecanismo de frontera |
| [009](docs/adr/ADR-009-distincion-409-422.md) | Distinción semántica entre 409 y 422 |
| [010](docs/adr/ADR-010-idempotencia-en-adaptador-rest.md) | Idempotencia HTTP resuelta en el adaptador REST |
| [011](docs/adr/ADR-011-compose-unico-sin-perfiles.md) | Compose único en la raíz sin perfiles |

## 4. Seguridad

- **Identidad:** Keycloak, realm `solicitudes-gov`, roles `SOLICITANTE` / `ANALISTA` /
  `SUPERVISOR`. Cliente `shell-web` (público, Authorization Code + PKCE); cliente `karate-e2e`
  (público, solo *direct grant*, exclusivo para pruebas automatizadas).
- **Defensa en profundidad, tres capas:** filtro de seguridad (`SecurityFilterChain` por
  authority) → borde de aplicación (`@PreAuthorize`) → dominio (el agregado `Solicitud` lanza
  `AccionNoPermitidaException` si el actor no tiene el rol). La tercera capa es la que garantiza
  que la regla sobreviva a un canal de entrada distinto de REST, y es la razón por la que el
  escenario **A3** no persiste nada ni emite eventos cuando el rol es insuficiente.
- **Token en el cliente:** vive únicamente **en memoria** del Shell (`oidc-client-ts` con
  `InMemoryWebStorage`), **nunca** en `localStorage` ni `sessionStorage`. Refresco silencioso;
  si falla, redirección a login preservando la ruta destino. `401` dispara reautenticación;
  `403` renderiza una vista de autorización insuficiente explícita, nunca una pantalla en
  blanco.
- **Ocultar un botón en React es usabilidad, no seguridad.** La autorización real está siempre
  en el servidor — verificado explícitamente en la sesión de la Fase 5 navegando de forma
  directa a una ruta prohibida.
- **Secretos:** ninguno en el repositorio. `.env.example` con valores ficticios rotulados;
  `.env` en `.gitignore`; en Helm, `externo.secretName` referencia un Secret que **ya existe**
  en el clúster, nunca uno creado por el chart.
- **CORS:** lista explícita de orígenes permitidos en ambos backends, nunca `*` con
  credenciales.

## 5. Contratos

- **OpenAPI:** generado desde el código con springdoc, expuesto en
  `http://localhost:8081/swagger-ui.html` y `http://localhost:8082/swagger-ui.html` con el
  stack corriendo; exportado también como archivo en [`docs/openapi-solicitudes.json`](docs/openapi-solicitudes.json)
  y [`docs/openapi-indicadores.json`](docs/openapi-indicadores.json).
- **Formato de error:** RFC 9457 Problem Details en ambos servicios, con un catálogo de
  códigos propio (`VALIDACION_DOMINIO`, `TRANSICION_INVALIDA`, `CONFLICTO_CONCURRENCIA`, ...) y
  `correlationId` para trazar un error hasta el log correspondiente.
- **Semántica de códigos propia del dominio:** `409` significa "el estado cambió, reintenta"
  (A2); `422` significa "esto nunca fue posible, no reintentes" (A4) — ver
  [ADR-009](docs/adr/ADR-009-distincion-409-422.md).
- **Contrato de eventos:** sobre común (`eventId`, `type`, `version`, `occurredAt`,
  `correlationId`, `causationId`, `producer`, `data`) sobre un exchange topic
  `solicitudes.events`, con dead-letter exchange y colas quorum. Catálogo de cinco eventos
  (`SolicitudRegistrada`, `SolicitudTomada`, `SolicitudResuelta`, `SolicitudDevuelta`,
  `SolicitudCerrada`) — el cuarto excede el mínimo exigido y se documenta como extensión propia:
  sin él, el modelo analítico no puede distinguir un reproceso de una resolución limpia.
- **Idempotencia HTTP:** header `Idempotency-Key` en `POST /solicitudes`, resuelta íntegramente
  en el adaptador REST (ver [ADR-010](docs/adr/ADR-010-idempotencia-en-adaptador-rest.md)).

## 6. Modelo de datos

**Operacional (`solicitudes_db`):** `categoria`, `solicitud` (agregado raíz, `version` para
bloqueo optimista), `observacion`, `cambio_estado` (historial), `outbox_evento`,
`idempotencia_comando`. Migraciones versionadas en
[`apps/services/solicitudes-service/bootstrap/src/main/resources/db/migration`](apps/services/solicitudes-service/bootstrap/src/main/resources/db/migration).

**Analítico (`indicadores_db`), esquema estrella:** `hecho_transicion` (grano: una fila por
transición ocurrida, incluido el registro inicial como `NULL → REGISTRADA` con fila centinela
en `dim_estado`) referenciando `dim_fecha`, `dim_categoria`, `dim_estado`, `dim_rol` — **rol, no
persona**, ver [ADR-005](docs/adr/ADR-005-dimension-rol-no-persona.md). Migraciones en
[`apps/services/indicadores-service/src/main/resources/db/migration`](apps/services/indicadores-service/src/main/resources/db/migration).

El modelo de lectura se puede reconstruir por completo reproyectando desde `outbox_evento`, que
actúa como log durable del sistema.

## 7. Ejecución local

### Prerrequisitos

| Requisito | Versión verificada |
|---|---|
| Docker Engine | 24.0.7 |
| Docker Compose | v2.23.3 |

No hace falta instalar Java, Maven, Node, pnpm ni Helm en la máquina: todo el *toolchain* se
ejecuta dentro de contenedores.

### Arranque

```bash
cp .env.example .env
docker compose up --build
```

Ese es el único comando necesario. Construye los cuatro componentes de aplicación (dos
servicios Spring Boot, dos SPA servidas por nginx) y arranca el stack completo: SQL Server,
RabbitMQ, Keycloak, y las cuatro aplicaciones, en ese orden, encadenado por healthchecks
(`depends_on: condition: service_healthy`).

Para levantar solo las dependencias mientras se desarrollan los servicios por fuera:

```bash
docker compose up -d sqlserver rabbitmq keycloak db-init
```

### Validación del estado de los servicios

```bash
docker compose ps
```

Se esperan siete servicios corriendo, cinco de ellos con estado `(healthy)`: `sqlserver`,
`rabbitmq`, `keycloak`, `solicitudes-service`, `indicadores-service`. `shell` y
`mfe-indicadores` no declaran healthcheck de Actuator (son SPA estáticas servidas por nginx; su
propio `HEALTHCHECK` de Docker valida que nginx responda). El contenedor `db-init` es de un
solo uso y debe aparecer como `Exited (0)`: crea las dos bases de datos y termina.

```bash
curl -s http://localhost:8080/realms/solicitudes-gov/.well-known/openid-configuration
curl -s http://localhost:8081/actuator/health
curl -s http://localhost:8082/actuator/health
```

### Puertos

| Puerto | Servicio |
|---|---|
| 1433 | SQL Server 2022 |
| 5672 | RabbitMQ (AMQP) |
| 15672 | RabbitMQ (consola de administración) |
| 8080 | Keycloak |
| 8081 | Servicio de Solicitudes |
| 8082 | Servicio de Indicadores |
| 3000 | Shell (host de microfrontends) |
| 3001 | MFE Indicadores |

### Usuarios de prueba

> **Credenciales ficticias, locales y de demostración.** No corresponden a ninguna persona ni
> a ningún sistema real. Se documentan a propósito: son parte del entregable, no un secreto
> filtrado.

Contraseña común: `Demo#2026`

| Usuario | Rol | Para qué existe |
|---|---|---|
| `solicitante1` | SOLICITANTE | Registra solicitudes y consulta solo las propias |
| `analista1` | ANALISTA | Toma, observa y resuelve |
| `analista2` | ANALISTA | Segundo analista: permite demostrar **A2**, dos analistas compitiendo por la misma solicitud |
| `supervisor1` | SUPERVISOR | Consulta todo, devuelve y cierra |
| `sinrol1` | *(ninguno)* | Permite demostrar **A3**: 403 sin efectos persistidos |

Administrador de Keycloak: `admin` / `Local#Keycloak2026` en <http://localhost:8080>.
Consola de RabbitMQ: `solicitudes_local` / `Local#Rabbit2026` en <http://localhost:15672>.

### Obtener un token para pruebas manuales

El cliente `karate-e2e` existe **exclusivamente para pruebas automatizadas** y es el único con
*direct grant* habilitado. El cliente del navegador, `shell-web`, usa Authorization Code + PKCE
y rechaza este flujo a propósito.

```bash
curl -s -X POST http://localhost:8080/realms/solicitudes-gov/protocol/openid-connect/token -d "client_id=karate-e2e" -d "username=analista1" -d "password=Demo#2026" -d "grant_type=password"
```

### Variables de entorno

Todas las credenciales viven en `.env`, que **no se versiona**. `.env.example` contiene la
lista completa con valores ficticios y es el que se copia al clonar.

## 8. Pruebas

| Nivel | Herramienta | Qué cubre |
|---|---|---|
| Dominio | JUnit 5 puro, sin mocks | Matriz de transiciones, rol requerido por acción, invariantes de creación |
| Casos de uso | JUnit + dobles en memoria | Orquestación, escritura al outbox, propagación de errores — un test por escenario |
| Arquitectura | ArchUnit | Regla de dependencia — **falla el build si se viola** |
| Aceptación | Karate | A1, A3 y el recorrido REGISTRADA → EN_ATENCION → RESUELTA, contra el stack de Compose |
| Frontend | Vitest + React Testing Library | Guard por rol, validación Zod, render de la línea de tiempo, los cuatro estados de vista |

```bash
alias mvnd='docker run --rm -v "$PWD":/app -v "$HOME/.m2":/root/.m2 -w /app maven:3.9-eclipse-temurin-21 mvn'

cd apps/services/solicitudes-service
mvnd clean verify                     # compila, prueba (incluida ArchUnit) y genera JaCoCo
mvnd -Dtest=ArquitecturaTest test      # verifica solo la regla de dependencia

cd apps/services/indicadores-service
mvnd clean verify

cd apps/e2e-karate
mvnd test                             # A1, A3, recorrido REGISTRADA -> EN_ATENCION -> RESUELTA

cd apps/frontend/shell
corepack pnpm test                    # Vitest
corepack pnpm exec tsc --noEmit       # tipado estricto, sin any
```

**JaCoCo:** exclusiones honestas de configuración, DTOs y entidades JPA — la cifra reportada
refleja dominio y casos de uso, no getters triviales. Reportes HTML en
`*/target/site/jacoco/index.html` de cada módulo tras `mvnd clean verify`; evidencia capturada
en [`docs/evidencias/`](docs/evidencias/).

## 9. CI/CD

Pipeline de GitLab CI (`.gitlab-ci.yml`, siete etapas):

```
lint → build → test → coverage → package (Kaniko) → helm-validate → deploy (manual)
```

- **Kaniko en lugar de Docker-in-Docker**: construye las imágenes sin daemon Docker ni
  contenedor privilegiado.
- **Caché** de `~/.m2` (keyed por los `pom.xml`) y de `node_modules` (keyed por
  `pnpm-lock.yaml`).
- **`helm-validate`** ejecuta `helm lint` y `helm template` contra los tres conjuntos de
  valores (base, dev, qa) — el mismo comando documentado abajo para ejecución local.
- **`deploy`** queda manual a propósito: el reto no exige un clúster real de destino; la
  promoción `dev → qa` sí está modelada y documentada.

No hay un GitLab real conectado a este repositorio en el entorno de evaluación: el pipeline se
valida por sintaxis YAML y por revisión, no por una ejecución real contra runners. Se documenta
con la misma honestidad que el resto del proyecto: **no se declara "probado en CI" lo que no se
pudo probar en CI.**

### Helm

```bash
docker run --rm -v "$PWD/deploy/helm":/apps alpine/helm lint /apps/solicitudes-gov
docker run --rm -v "$PWD/deploy/helm":/apps alpine/helm template solicitudes-gov /apps/solicitudes-gov -f /apps/solicitudes-gov/values-dev.yaml
```

Un chart (`deploy/helm/solicitudes-gov/`) con plantillas separadas por componente
(`deployment-*.yaml` / `service-*.yaml`), `values-dev.yaml` y `values-qa.yaml`, probes de
liveness/readiness sobre Actuator para los backends (grupos de probes de Spring Boot,
`/actuator/health/liveness` y `/actuator/health/readiness`) y sobre `/` para las SPA, `requests`
y `limits` declarados, un `ConfigMap` por backend para configuración no sensible, y una
referencia (`secretKeyRef`) a un `Secret` que debe existir previamente en el clúster —el chart
nunca crea ni versiona uno con valores reales.

## 10. Observabilidad

Actuator (`health`, `info`, `metrics`, `prometheus`) en ambos backends · Micrometer · logs
estructurados en JSON con `correlationId` en MDC, propagado al sobre del evento
(`SobreEvento.correlationId`) y devuelto en el header de respuesta HTTP. Un `TraceFilter`
genera o propaga `X-Correlation-Id` en cada petición, de modo que una operación pueda seguirse
desde la petición del navegador hasta la fila proyectada en el modelo analítico.

## 11. Limitaciones y trabajo pendiente

Documentado con la misma honestidad que exige el reto: preferible declarar aquí lo que quedó
diseñado y no implementado a prometerlo silenciosamente.

- **No hay Prometheus ni Grafana en el Compose.** Actuator expone `/actuator/prometheus` en
  ambos backends, listo para que un Prometheus externo lo scrapee; añadir el propio Prometheus
  y un Grafana al stack local era el primer punto de la política de recorte y no se priorizó
  frente al núcleo evaluado.
- **Storybook cubre exactamente los dos componentes exigidos** (`EstadoChip`, `EstadoVista`),
  no el resto de componentes de presentación del frontend.
- **No hay pruebas de integración con Testcontainers.** La cobertura de integración real
  (bloqueo optimista bajo concurrencia, publicador del outbox, consumo idempotente) se
  demostró manualmente y está documentada como evidencia en `docs/evidencias/`, pero no está
  automatizada contra una base de datos real en un pipeline.
- **El chart de Helm no se desplegó contra un clúster Kubernetes real**; se validó con
  `helm lint` y `helm template`, que es la verificación que el blueprint pide explícitamente,
  pero no reemplaza un `helm upgrade --install` real.
- **`outbox_evento` no tiene purga automatizada.** Crece indefinidamente; en un entorno real
  necesitaría un job de limpieza para las filas `PUBLICADO` más antiguas que la ventana de
  reproceso deseada.
- **`GeneradorCodigoAdapter`** (generador del código legible `SOL-2026-000123`) tiene un riesgo
  documentado de colisión bajo concurrencia muy alta, no mitigado con un mecanismo adicional
  más allá de la restricción `UNIQUE` de base de datos — aceptable para el volumen esperado de
  este sistema, declarado explícitamente como riesgo abierto.
- **El pipeline de GitLab CI no se ejecutó contra un GitLab real**: se valida por sintaxis y
  revisión manual, no por una corrida real de principio a fin.
