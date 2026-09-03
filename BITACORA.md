# BITÁCORA

Memoria entre sesiones. Se lee al abrir cada sesión y se actualiza al cerrarla.

---

## Fase 0 — Análisis y alineación documental — 2 de septiembre de 2026

### Completado
- Lectura completa del enunciado (PDF), `BLUEPRINT.md`, `CLAUDE.md`, `PROMPT_MAESTRO.md`
  y `USO_DE_IA.md`.
- Detección y resolución de **siete contradicciones** entre los documentos y el enunciado.
  Registradas en `BLUEPRINT.md` §17, con corrección propagada a los tres documentos.
- Blueprint elevado a **v1.1**.

### Decisiones tomadas
| Tema | Decisión | ADR |
|---|---|---|
| Ubicación del Compose | Raíz, sin perfiles, para que `docker compose up --build` baste (A7) | ADR-011 |
| Concurrencia A2 | Solo `@Version`; se descarta el `UPDATE ... WHERE estado=...` porque filtraba la máquina de estados hacia SQL | ADR-004 |
| Idempotencia HTTP | Vive en el adaptador REST, no como puerto de aplicación | ADR-010 |
| Privacidad analítica | El evento transporta el identificador; la **proyección** lo descarta. Redacción corregida | ADR-005 |
| Grano del hecho | El registro se proyecta como `NULL → REGISTRADA` con fila centinela en `dim_estado` | — |
| CORS | Se configura en **ambos** servicios: el navegador habla con dos orígenes, no hay gateway | — |
| Toolchain | Contenerizado por completo (Maven, Helm por imagen; pnpm por corepack) | — |

---

## Fase 1 — Infraestructura ejecutable — 2 de septiembre de 2026

### Completado
- `git init` y primer commit. El repositorio no existía.
- `.gitignore` con exclusión de `.env` y artefactos de build.
- `.env.example` con la lista completa de variables, valores ficticios y rotulados.
- `compose.yaml` en la raíz: SQL Server 2022, RabbitMQ 3.13-management, Keycloak 26 y
  `db-init`, todos con healthcheck y `depends_on: service_healthy`.
- `deploy/compose/db-init/01-crear-bases.sql`: crea ambas bases de forma idempotente y
  activa `READ_COMMITTED_SNAPSHOT`.
- `deploy/keycloak/realm-solicitudes-gov.json`: realm, 3 roles, 2 clientes, 5 usuarios.
- Migraciones Flyway `V1__esquema_operacional.sql` (6 tablas, 8 índices justificados) y
  `V2__semillas_categorias.sql` (5 categorías activas + 1 inactiva).
- `README.md` de arranque.

### Verificado con
```
docker compose ps
  sqlserver  Up (healthy) · rabbitmq Up (healthy) · keycloak Up (healthy) · db-init Exited (0)

docker compose logs db-init
  Base creada: solicitudes_db / Base creada: indicadores_db / db-init completado correctamente.

curl .../.well-known/openid-configuration
  issuer http://localhost:8080/realms/solicitudes-gov · PKCE: plain, S256

curl direct grant analista1 @ karate-e2e
  token emitido (900 s) · realm_access.roles ["ANALISTA"] · azp karate-e2e

SELECT name, is_read_committed_snapshot_on FROM sys.databases
  solicitudes_db 1 · indicadores_db 1

shell-web con grant_type=password  →  unauthorized_client  (correcto)
```

### Defectos encontrados y corregidos durante la fase
1. **`db-init` fallaba con "Login failed for user 'sa'".** Un `entrypoint` en forma de
   lista no pasa por un shell, así que `${SQLSERVER_SA_PASSWORD}` llegaba literal.
   Corregido invocando `bash -c` de forma explícita.
2. **RabbitMQ nunca alcanzaba `healthy`.** El healthcheck apuntaba a `rabbit@localhost`,
   pero el nodo se llama `rabbit@<hostname-del-contenedor>`. Corregido omitiendo `--node`.
3. **Rutas de `sqlcmd` verificadas, no asumidas.** La imagen de SQL Server 2022 trae
   `/opt/mssql-tools18/bin/sqlcmd` (v18), que **exige `-C`** por el certificado
   autofirmado. Se reutiliza esa imagen para `db-init` en vez de descargar otra.
4. **Keycloak 26 no incluye `curl` ni `wget`.** El healthcheck usa el descriptor
   `/dev/tcp` de bash contra el puerto de administración 9000, que requiere
   `KC_HEALTH_ENABLED=true`.

### Riesgos abiertos
- **`realm_access` puede estar completamente ausente del token.** Verificado con
  `sinrol1`: un usuario sin roles de realm recibe un JWT **sin la claim**, no con un
  arreglo vacío. El conversor de authorities de la fase 3 debe ser null-safe; si no,
  A3 devolvería **500 en vez de 403** y se perdería el escenario.
- **Presupuesto de memoria:** 8 GB para Docker. Con SQL Server (2 g), Keycloak (768 m) y
  RabbitMQ (512 m) ya comprometidos, las dos JVM de las fases 3 y 4 deben arrancar con
  `-XX:MaxRAMPercentage` acotado. Se vigila al añadirlas al Compose.
- Module Federation con React 19 + MUI 7 sigue siendo el mayor riesgo del proyecto. Es
  justamente el objeto de la fase 2.

### Pendiente
- Fase 2: prueba de riesgo del microfrontend federado.
- Los ADR se redactan en la fase 6, pero las decisiones ya están registradas arriba y en
  `BLUEPRINT.md` §17.

### Correccion posterior a la verificacion
5. **Los indices filtrados fallaban con `QUOTED_IDENTIFIER` incorrecto.** Detectado al
   aplicar `V1` contra una base desechable antes de que Flyway exista. El driver JDBC
   activa esa opcion por omision y el fallo habria pasado inadvertido hasta que alguien
   ejecutara la migracion con otro cliente. Se fijan `SET QUOTED_IDENTIFIER ON` y
   `SET ANSI_NULLS ON` al inicio de la migracion.

   Verificado: `V1` y `V2` aplicadas sobre una base limpia crean 6 tablas, 8 indices y
   6 semillas; la base de prueba se elimino despues.
