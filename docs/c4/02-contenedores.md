# C4 — Nivel 2: Contenedores

Los bloques desplegables del sistema y cómo se comunican. Corresponde 1:1 con los servicios
declarados en `compose.yaml`.

```mermaid
C4Container
    title Contenedores — Plataforma de Gestión de Solicitudes Operacionales

    Person(usuario, "Solicitante / Analista / Supervisor", "Según el rol autenticado.")

    System_Boundary(plataforma, "Plataforma de Gestión de Solicitudes Operacionales") {
        Container(shell, "Shell", "React 19, Module Federation host", "Enrutamiento, sesión OIDC, layout, tema MUI, store raíz. Puerto 3000.")
        Container(mfe, "MFE Indicadores", "React 19, Module Federation remoto", "Vista analítica: conteos y tendencia. Puerto 3001, expone ./IndicadoresApp.")
        Container(solicitudes, "Servicio de Solicitudes", "Spring Boot 3.5, hexagonal", "Máquina de estados, autorización por rol, trazabilidad. Puerto 8081.")
        Container(indicadores, "Servicio de Indicadores", "Spring Boot 3.5, en capas", "Proyector de eventos a modelo de lectura en estrella. Puerto 8082.")
        ContainerDb(db_operacional, "solicitudes_db", "SQL Server 2022", "Agregado, historial, outbox, idempotencia.")
        ContainerDb(db_analitica, "indicadores_db", "SQL Server 2022", "Esquema estrella: hechos y dimensiones.")
        ContainerQueue(broker, "RabbitMQ", "3.13 management", "Exchange topic solicitudes.events + DLX + colas quorum.")
        Container(keycloak, "Keycloak", "26.x, start-dev", "Realm solicitudes-gov: roles, clientes, usuarios.")
    }

    Rel(usuario, shell, "Usa", "HTTPS")
    Rel(shell, mfe, "Carga en tiempo de ejecución", "Module Federation")
    Rel(shell, keycloak, "Autoriza (Authorization Code + PKCE)", "OIDC")
    Rel(mfe, keycloak, "Reutiliza la sesión vía authBridge (federado) o autentica sola (standalone)", "OIDC")
    Rel(shell, solicitudes, "CRUD de solicitudes", "HTTPS + Bearer JWT")
    Rel(mfe, indicadores, "Consulta resumen y tendencia", "HTTPS + Bearer JWT")
    Rel(solicitudes, keycloak, "Valida el JWT contra JWKS", "HTTPS")
    Rel(indicadores, keycloak, "Valida el JWT contra JWKS", "HTTPS")
    Rel(solicitudes, db_operacional, "Lee y escribe (JPA + Flyway)", "JDBC")
    Rel(indicadores, db_analitica, "Lee y escribe (JPA + Flyway)", "JDBC")
    Rel(solicitudes, broker, "Publica eventos vía Outbox", "AMQP")
    Rel(indicadores, broker, "Consume de forma idempotente", "AMQP")
```

**Notas de lectura:**

- No hay API Gateway: el navegador habla directamente con los dos orígenes de backend
  (8081 y 8082), consecuencia documentada en el blueprint §5.1 — CORS se configura en ambos
  servicios.
- La flecha `solicitudes → broker → indicadores` no es una llamada directa: es asíncrona,
  mediada por el exchange topic y el patrón Transactional Outbox (ADR-002), detallada en el
  diagrama de secuencia (`03-secuencia-flujo-principal.md`).
- El Shell posee la única instancia de sesión OIDC real cuando corre federado; el MFE la
  consume por `authBridge` (blueprint §9.2). En modo standalone el MFE se autentica solo, sin
  que exista nunca una sesión "huérfana" compitiendo con la del Shell.
