# C4 — Nivel 1: Contexto

Vista de más alto nivel: quién usa el sistema y con qué otro sistema interactúa, sin mostrar
ningún detalle interno.

```mermaid
C4Context
    title Contexto — Plataforma de Gestión de Solicitudes Operacionales

    Person(solicitante, "Solicitante", "Registra solicitudes operacionales y consulta las propias.")
    Person(analista, "Analista", "Toma, atiende, resuelve y observa solicitudes asignadas.")
    Person(supervisor, "Supervisor", "Consulta todo, devuelve a atención y cierra solicitudes.")

    System(plataforma, "Plataforma de Gestión de Solicitudes Operacionales", "Registro, asignación, seguimiento con trazabilidad completa, y lectura analítica del proceso.")

    Rel(solicitante, plataforma, "Registra y consulta sus solicitudes", "HTTPS")
    Rel(analista, plataforma, "Toma, resuelve y observa", "HTTPS")
    Rel(supervisor, plataforma, "Devuelve, cierra y consulta indicadores", "HTTPS")
```

**Notas de lectura:**

- Los tres actores son roles de un mismo directorio de identidad (Keycloak), no sistemas
  externos: por eso no aparecen como cajas separadas en este nivel — el detalle de identidad
  pertenece al nivel de contenedores (`02-contenedores.md`).
- No hay ningún sistema externo de terceros integrado (no hay pasarela de pagos, ERP externo,
  etc.): el alcance del reto es autocontenido.
