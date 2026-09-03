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

---

## Fase 2 — Prueba de riesgo del microfrontend federado — 2 de septiembre de 2026

### Completado
- `apps/frontend/shell` (host, :3000) y `apps/frontend/mfe-indicadores` (remoto, :3001)
  con Rspack 1.7.12, React 19.2.8, MUI 7.3.11 y TypeScript 5.9.3 en modo estricto.
- `apps/frontend/shared/federacion-compartida.js`: **lista unica** de modulos compartidos,
  importada por ambas configuraciones. Duplicarla es la causa numero uno de desfase.
- El remoto expone `./IndicadoresApp` y arranca standalone con su propio `ThemeProvider`.
- `LimiteDeError`: el fallo de un remoto no deja el shell en blanco.
- Contrato de tipos del remoto declarado a mano en `tipos-remotos.d.ts`, sin `any`.

### Verificado con
```
pnpm exec tsc --noEmit          shell y remoto: sin errores, strict + noUncheckedIndexedAccess
NODE_ENV=production pnpm build  ambos: 0 errores

Build de produccion servido y abierto en el navegador:
  share scope, 10 entradas (5 paquetes x 2 instancias), TODAS proveedor=shell
    react 19.2.8 · react-dom 19.2.8 · @emotion/react 11.14.0
    @emotion/styled 11.14.1 · @mui/material 7.3.11
  caches de Emotion distintos: ["css-global","css"]   (los dos estandar de MUI, sin duplicar)
  recursos descargados del remoto: ["remoteEntry.js","__federation_expose_IndicadoresApp.js"]
    -> ningun vendor chunk: el remoto consume TODO del host
  color primario leido por el remoto: #1565c0  (el del host, no el suyo)
  consola: sin mensajes

Standalone en :4001  -> tema propio #6a1b9a, estilos aplicados, consola limpia
Remoto caido         -> el shell sobrevive y muestra error accionable
Remoto restaurado + reintentar -> el remoto vuelve a montar
```

### Defectos encontrados y corregidos
1. **MUI se empaquetaba dos veces.** Las importaciones profundas (`@mui/material/Alert`)
   no casan con la clave compartida `@mui/material`: son especificadores distintos. No
   fallaba nada a la vista porque el tema lo transporta Emotion, que si era unico.
   Corregido pasando a importaciones del barril. Se probo antes la clave `'@mui/material/'`
   y se descarto: el manifiesto la expande por submodulo pero esas entradas nunca llegan
   al share scope, o sea configuracion muerta que aparenta resolver el problema.
2. **MUI seguia sin registrarse pese al barril.** MUI 7 resuelve a
   `@mui/material/esm/index.js` y ese subdirectorio no tiene `package.json` con version,
   asi que Module Federation no podia deducirla y descartaba el modulo en silencio.
   Corregido declarando `version` ademas de `requiredVersion`.
3. **Pantalla en blanco en el build de produccion.** El transform de SWC llevaba
   `development: true` fijo, de modo que produccion emitia llamadas a `jsxDEV`, que no
   existe en el runtime de produccion de React. Corregido derivandolo del modo.
4. **`noImplicitOverride` detecto un `render` sin `override`** en el limite de error.

### Riesgo cerrado
El mayor riesgo del proyecto queda **mitigado y verificado**: Module Federation sobre
Rspack funciona con React 19 y MUI 7, con singleton real comprobado en el share scope y no
solo por inspeccion visual.

### Limitacion documentada
**El reintento en caliente de un remoto caido no es viable.** El fallo se memoiza en tres
niveles: `React.lazy`, el runtime de Module Federation y el module cache del bundler. Se
resolvieron los dos primeros —`React.lazy` nuevo por intento y `registerRemotes` con
`force: true`— y el tercero siguio devolviendo el modulo invalido, con React fallando en
el error #306. El reintento recarga la pagina, que es lo unico que limpia los tres. En la
fase 2 no hay estado que perder; para la fase 5 queda anotado que, si se quisiera
conservarlo, la salida es montar el remoto en su propia ruta.

### Pendiente
- Fase 3: backend transaccional con arquitectura hexagonal.

---

## Fase 3 — Backend transaccional (Servicio de Solicitudes) — 3 de septiembre de 2026

### Completado
- **Maven multi-modulo** `domain / application / infrastructure / bootstrap`. El pom de `domain`
  no declara ninguna dependencia de produccion.
- **Dominio**: agregado `Solicitud`, tabla de transiciones en el enum `Accion`, value objects,
  eventos como `sealed interface`, jerarquia de excepciones con codigo estable.
- **26 pruebas de dominio escritas ANTES de la implementacion**, verificadas fallando primero.
- **ArquitecturaTest (ArchUnit)**: 9 reglas que rompen el build.
- **7 casos de uso**, clases planas sin anotaciones de Spring, cableados en `BeanConfiguration`.
- **Persistencia JPA**: entidades separadas del dominio, mapper, `@Version`.
- **Outbox transaccional** con `Propagation.MANDATORY` y publicador agendado con
  `SELECT ... WITH (UPDLOCK, READPAST)`.
- **REST**: controladores, DTOs, Problem Details RFC 9457, paginacion, 5 filtros,
  filtro de `Idempotency-Key`.
- **Seguridad**: Resource Server, conversor null-safe de authorities, `@PreAuthorize`, CORS.
- **OpenAPI 3.1** generado por springdoc, exportado a `docs/openapi-solicitudes.json`.

### Verificado con
```
mvn test                 43 pruebas, 0 fallos (26 dominio + 8 aplicacion + 9 ArchUnit)
docker compose ps        infraestructura (healthy); servicio arrancado contra ella

bash docs/evidencias/verificar-escenarios.sh   -> docs/evidencias/salida-escenarios-a1-a4.txt
  A1  201  estado REGISTRADA, historial (inicio)->REGISTRADA
  A2  analista1 201 EN_ATENCION / analista2 409 CONFLICTO_CONCURRENCIA  (en paralelo real)
      Asignaciones exitosas: 1   Conflictos: 1   -> no hubo doble asignacion
  A3  403 ACCION_NO_PERMITIDA, sin cambios persistidos ni eventos
  A4  422 TRANSICION_INVALIDA "No se permite CERRAR sobre una solicitud en estado REGISTRADA"
  Recorrido completo REGISTRADA->EN_ATENCION->RESUELTA->(DEVUELTA)->RESUELTA->CERRADA
  Aislamiento por rol: solicitante1 ve 6 solicitudes, 0 de otros solicitantes

Outbox:    20 filas, todas PUBLICADO, 0 FALLIDO
RabbitMQ:  indicadores.solicitudes 20 mensajes, DLQ 0

Idempotencia:
  1a peticion             201  SOL-2026-000007
  2a misma llave+cuerpo   201  MISMO id, sin duplicado en base
  3a misma llave, otro cuerpo  409
  Solicitudes con ese asunto en base: 1

OpenAPI: 7 rutas documentadas, 8 esquemas
```

### Defectos encontrados y corregidos
1. **`@Transactional` sobre un metodo `@Bean` no hace transaccional al objeto devuelto.** Spring
   proxifica los metodos anotados de un bean, no la factoria que lo produce. Los casos de uso
   corrian sin transaccion y el outbox se habria escrito fuera de ella. **No paso inadvertido
   porque el adaptador del outbox declara `Propagation.MANDATORY`**: en vez de escribir eventos
   sin transaccion, la primera peticion fallo senalando la causa. Corregido con decoradores
   `TransactionTemplate` en el cableado, que caben en una lambda porque los puertos tienen un
   solo metodo.
2. **`LazyInitializationException` en las consultas.** Con `open-in-view: false` la sesion se
   cierra al salir del metodo transaccional y las consultas no tenian ninguna. Corregido con una
   `TransactionTemplate` de solo lectura.
3. **N+1 latente en la bandeja.** El mapper cargaba historial y observaciones tambien en el
   listado: hasta 101 consultas para pintar una tabla que no muestra esos datos. Corregido con
   `aDominioResumen`.
4. **Desajuste de tipos de fecha.** Hibernate 6 mapea `Instant` a `DATETIMEOFFSET`; las
   migraciones usan `DATETIME2`. Lo detecto `ddl-auto: validate` al arrancar. Se fijo
   `@JdbcTypeCode(SqlTypes.TIMESTAMP)`: todos los instantes son UTC por construccion, de modo que
   una columna con desplazamiento guardaria siempre +00:00.
5. **401 en vez de aceptar tokens validos.** El token lo emite `localhost:8080` pero el servicio,
   dentro de la red Docker, resolvia `keycloak:8080`. Corregido separando `issuer-uri` (lo que se
   valida) de `jwk-set-uri` (de donde se descargan las claves).
6. **Formato de error inconsistente.** Los manejadores 401/403 usaban un `ObjectMapper` propio,
   sin el mixin de ProblemDetail, y anidaban los campos bajo `properties` mientras la ruta MVC los
   aplanaba. El cliente habria necesitado dos parsers. Corregido inyectando el `ObjectMapper` de
   Spring.
7. **`ContentCachingRequestWrapper` no sirve para pre-leer el cuerpo**: memoriza lo que otro lee,
   no reproduce lo ya leido, y el controlador recibia "Required request body is missing".
   Corregido con un envoltorio propio que devuelve un flujo nuevo sobre los mismos bytes.

### Decisiones tomadas
- **La tabla de transiciones vive en `Accion`, no en `EstadoSolicitud`.** Una accion necesita
  declarar su estado de ORIGEN, no solo el destino: desde RESUELTA se llega a EN_ATENCION, pero
  solo por DEVOLVER. Con una tabla por destino, TOMAR sobre una solicitud RESUELTA se habria
  aceptado por error. Se aparta de la letra del blueprint y se documenta.
- **`CambioEstado` es clase y no record**: dos campos son genuinamente opcionales y un record
  obliga a que el accesor devuelva el tipo del componente.
- **ArquitecturaTest: se corrigio la expresion de una regla, no su intencion.** Exigia que toda
  entidad JPA viviera en `adapter.out.persistence` y fallaba con `OutboxEventoEntity`, que el
  blueprint ubica a proposito en `adapter.out.messaging`. Se relajo a `adapter.out..`; el riesgo
  real —que el controlador devuelva una entidad— lo cubre otra regla que si se conserva intacta.
  Las otras dos violaciones se corrigieron moviendo CODIGO, no la prueba: `Filtro` y `Pagina`
  salieron del paquete de puertos hacia `command` y `result`.
- **La idempotencia se partio en dos**: el filtro HTTP en `adapter.in.rest`, la tabla en
  `adapter.out.persistence`, unidos por la interfaz `RegistroIdempotencia` en terreno neutral.
  Sin ella, el filtro habria violado la regla que prohibe al adaptador de entrada conocer el de
  salida.

### Riesgos abiertos
- **`GeneradorCodigoAdapter` no es seguro bajo concurrencia alta**: calcula el consecutivo desde
  el maximo del anio. La restriccion UNIQUE impide el duplicado, asi que el peor caso es un error,
  no un codigo repetido. La solucion productiva es un SEQUENCE por anio. Declarado en el codigo.
- Faltan pruebas de integracion con Testcontainers. La verificacion de A1-A4 es por script contra
  el stack real, reproducible con un comando, pero no corre en CI sin infraestructura.

### Pendiente
- Fase 4: Servicio de Indicadores, consumidor idempotente y escenario A5.

---

## Fase 4 — Servicio de Indicadores (modelo de lectura por eventos) — 3 de septiembre de 2026

### Completado
- **Un solo modulo Maven**, arquitectura en capas y no hexagonal (ADR-003): sin dominio que
  proteger, separar puertos y adaptadores aqui solo habria agregado ceremonia.
- **Migraciones del esquema estrella**: `hecho_transicion`, `dim_fecha` (poblada de forma
  perezosa, no por rango fijo), `dim_categoria` (replicada, con fila `DESCONOCIDA` de respaldo),
  `dim_estado` (con centinela `NINGUNO` para el registro), `dim_rol`.
- **`TipoDeEvento`**: unica traduccion entre el nombre del hecho y las claves de dimension.
  No es logica de negocio — el productor ya decidio si la transicion era valida.
- **`ProyeccionService`**: consumo idempotente en una unica transaccion, con la marca de
  procesado y la fila del hecho viviendo o muriendo juntas.
- **Consumidor RabbitMQ** con topologia declarada tambien del lado del consumidor (idempotente
  entre servicios), politica de reintento con exclusion de fallos deterministicos, y DLQ.
- **Endpoints** `/resumen` y `/tendencia`, restringidos a ANALISTA/SUPERVISOR.
- **CORS configurado tambien aqui**: no hay gateway, el navegador habla con los dos origenes.
- **5 pruebas** del contrato de eventos, incluida la verificacion de que `SobreEvento.Datos` no
  declara ningun campo de persona (ADR-005 verificado en el tipo, no solo en el comentario).

### Verificado con
```
mvn test                          5 pruebas, 0 fallos

Flujo completo Solicitudes -> Indicadores:
  outbox_evento PUBLICADO: 27   hecho_transicion: 27   evento_procesado: 27   (en lockstep)
  GET /resumen   {"porEstado":{"CERRADA":3,"REGISTRADA":5},"porCategoria":{...}}
  GET /tendencia {"porDia":{"2026-09-03":8}}
  SOLICITANTE contra /resumen -> 403 (la vista analitica es para quien atiende y supervisa)

bash docs/evidencias/verificar-a5-idempotencia.sh -> docs/evidencias/salida-a5-idempotencia.txt
  Reenvio de un evento YA PROCESADO, con su eventId original, vía la API de RabbitMQ
  (equivalente a "Publish message" en la consola :15672):
    resumen antes == resumen despues
    hecho_transicion: 21 -> 21 (no crecio)
    evento_procesado: 21 -> 21 (la clave primaria rechazo el duplicado)
    DLQ: sin crecer con este reenvio (el duplicado se confirma, no es un error)
    traza: "Evento SolicitudRegistrada ya estaba proyectado ... Se confirma sin alterar los conteos"
  RESULTADO: A5 SE CUMPLE

Payload ilegible -> DLQ:
  Publicado un mensaje que no es JSON valido.
  DLQ paso de N a N+1 en UN solo intento (log: una linea de error, "Retries exhausted"
  10ms despues, sin backoff de 1s/2s).
```

### Defectos encontrados y corregidos
1. **La idempotencia no funcionaba, aunque lo parecia.** `EventoProcesadoRepository` heredaba
   `save()` de `JpaRepository`. Para una entidad con identificador ASIGNADO por fuera —aqui el
   `eventId` lo genera el productor, la base no lo autogenera— Spring Data no puede saber si la
   fila es nueva, asi que `save()` degenera en `merge()`: hace SELECT y, si existe, UPDATE. Nunca
   se produce la violacion de clave primaria que el patron necesita.

   **El sintoma era enganoso.** Al reenviar un evento duplicado, `evento_procesado` no crecia (lo
   que parecia correcto) mientras `hecho_transicion` SI se insertaba de nuevo: la idempotencia
   aparentaba funcionar sin hacerlo. Se detecto ejecutando el escenario A5 con datos reales, no
   inspeccionando el codigo. Corregido con un `INSERT` nativo explicito (`insertarMarca`), cuya
   semantica es inequivoca: o inserta, o revienta la transaccion.

2. **Un mensaje ilegible se reintentaba 3 veces con backoff completo antes de llegar a la DLQ.**
   El javadoc del consumidor afirmaba que `AmqpRejectAndDontRequeueException` evitaba el
   reintento; es falso. El interceptor de retry de Spring AMQP envuelve cualquier excepcion que
   salga del listener y solo decide "sin reencolar" cuando ya agoto los intentos — la excepcion
   por si sola no acorta el camino. Se via en los logs: tres lineas de error separadas exactamente
   por los intervalos de backoff configurados (1s, luego 2s).

   Un JSON malformado no se arregla reintentandolo: es un fallo deterministico, no transitorio.
   Corregido con un `RetryOperationsInterceptor` propio (`RabbitConfiguration`) cuya
   `SimpleRetryPolicy` excluye explicitamente `AmqpRejectAndDontRequeueException`, instalado
   sobre la factoria via `ContainerCustomizer` porque `SimpleRabbitListenerContainerFactory` no
   expone `setAdviceChain` directamente. Verificado: ahora una linea de error, "Retries
   exhausted" 10ms despues, sin demora.

   Antes de escribir esta correccion se verificaron los nombres reales de clase contra el jar de
   `spring-rabbit` descargado (`javap` sobre las clases extraidas), en vez de asumirlos de
   memoria: `RetryInterceptorBuilder` y `SimpleRabbitListenerContainerFactory` viven en
   `org.springframework.amqp.rabbit.config`, no en `.retry` ni `.listener` como se habria
   asumido.

### Decisiones tomadas
- **`dim_fecha` se puebla de forma perezosa**, no por un rango sembrado en la migracion. Un rango
  fijo se agota: el primer evento que llegara despues de la ultima fecha sembrada habria violado
  la clave foranea y terminado en la DLQ sin razon de negocio.
- **La topologia de RabbitMQ se declara tambien desde el consumidor**, duplicando la declaracion
  del productor a proposito. Las declaraciones AMQP son idempotentes mientras coincidan; con solo
  el productor declarandola, levantar Indicadores en solitario habria fallado por una cola
  inexistente.
- **El catalogo de categorias se replica en `dim_categoria`**, con una fila `DESCONOCIDA` de
  respaldo. Es lo normal en un esquema en estrella y evita que una categoria aun no replicada
  tumbe al consumidor.

### Riesgos abiertos
- Ninguno nuevo. El riesgo de `GeneradorCodigoAdapter` bajo concurrencia alta, documentado en la
  fase 3, sigue vigente y no lo toca esta fase.

### Pendiente
- Fase 5: Frontend completo sobre la base federada de la fase 2.

---

## Fase 5 — Interfaz completa sobre la base federada — 3 de septiembre de 2026

### Completado
- **Autenticación Authorization Code + PKCE** con `oidc-client-ts`, token en memoria
  (`InMemoryWebStorage` envuelto en `WebStorageStateStore`, nunca `localStorage`/
  `sessionStorage`), renovación silenciosa automática, y un `authBridge` expuesto por Module
  Federation para que el remoto lea la sesión del shell sin duplicar el login.
- **Capa de datos con RTK Query**: `createApi` + `fetchBaseQuery`, con `responseSchema` +
  `catchSchemaFailure` (integración nativa Standard Schema de RTK Query 2.x) validando con Zod
  **toda** respuesta del backend antes de que entre al estado. `Idempotency-Key` en la creación.
- **Cinco vistas**: bandeja (filtros y paginación en la URL, no en estado local — un enlace
  filtrado es compartible y sobrevive a un reload), creación (react-hook-form + zodResolver),
  detalle (línea de tiempo + acciones condicionadas por rol y por estado), y el resumen
  analítico federado en `mfe-indicadores`.
- **`EstadoVista`** (paquete `shared`, documentado en Storybook): encapsula los cuatro estados
  exigidos — cargando (`role="status"`), vacío, error con reintento (`role="alert"`) y
  autorización insuficiente (`role="alert"`) — para que ninguna vista los reimplemente distinto.
- **`EstadoChip`** (también en Storybook): color por estado/prioridad, única fuente de verdad
  visual para ambos dominios.
- **30 pruebas Vitest**: guardas de rol (`RutaProtegida`), validación Zod de formularios,
  renderizado de la línea de tiempo, y los cinco estados de `EstadoVista`.
- **Paquete `shared`** (`pnpm-workspace.yaml`) para que dominio, esquemas y componentes de
  presentación se compartan entre `shell` y `mfe-indicadores` sin publicarse a un registro.

### Verificado con
```
cd apps/frontend/shell && corepack pnpm exec tsc --noEmit         0 errores
cd apps/frontend/mfe-indicadores && corepack pnpm exec tsc --noEmit  0 errores
cd apps/frontend/shell && corepack pnpm test
  Test Files  4 passed (4)
  Tests  30 passed (30)
cd apps/frontend/shell && corepack pnpm exec storybook build      Storybook build completed successfully
cd apps/frontend/shell && corepack pnpm run build                 Rspack compiled (1 warning: tamaño de bundle)
cd apps/frontend/mfe-indicadores && corepack pnpm run build        Federated types created correctly

Navegador real, sesión analista1 (ficticio):
  A6 — reload en /solicitudes/{id}: la sesión se recupera (POST /token real) y se
       reobtienen tanto la lista como el detalle desde el backend (GET .../solicitudes y
       GET .../solicitudes/{id} -> 200), no hay dato cacheado localmente que sobreviva.
  Navegación directa a /indicadores como SOLICITANTE (rol insuficiente): se muestra
       "Autorización insuficiente" con role="alert", nunca una pantalla en blanco.
  Teclado: Tab recorre filtros -> enlaces de la tabla -> Enter activa el enlace enfocado
       (navega). Foco trampeado dentro del diálogo "Agregar observación", Escape lo
       cierra y devuelve el foco al botón que lo abrió.
```

### Defectos encontrados y corregidos
1. **Module Federation duplicaba la instancia de `UserManager` entre shell y remoto.** El
   `shared` de Module Federation solo aplica a paquetes de NPM declarados; `oidc-client-ts`
   nunca se agregó ahí, así que el módulo expuesto `authBridge` recibía su propia copia
   empaquetada de `authService.ts` y creaba un segundo `UserManager` que jamás veía el login
   real. Diagnosticado inspeccionando `window.__FEDERATION__.__INSTANCES__` y el contenido
   servido del chunk expuesto directamente desde la consola del navegador. Corregido con un
   singleton verdadero vía `globalThis[Symbol.for(...)]`, que sobrevive a cualquier copia
   duplicada del módulo.
2. **Carrera de hidratación en `authBridge` incluso tras el fix anterior.** `getUser()` es
   asíncrono; el primer `read` síncrono desde `useSesion.ts` en el remoto llegaba antes de que
   la promesa resolviera. Suscribirse a `userLoaded` no alcanzaba porque ese evento ya había
   disparado en el pasado y no se repite. Corregido exponiendo `authBridge.listo: Promise<void>`
   y esperándolo antes de la primera lectura.
3. **El cierre de sesión no terminaba la sesión real de Keycloak.** `cerrarSesion` llamaba solo
   `removeUser()`; la cookie SSO de Keycloak seguía viva, así que el siguiente
   `signinRedirect()` reautenticaba en silencio al mismo usuario. Corregido con
   `signoutRedirect()`. Esto expuso una segunda carrera: `signoutRedirect()` dispara
   `userUnloaded` antes de que su propia navegación termine, y `RutaProtegida` alcanzaba a
   lanzar un `signinRedirect()` competidor que ganaba la carrera (visible en el log de red como
   un logout `net::ERR_ABORTED` seguido de inmediato por un `/callback?code=...` nuevo).
   Corregido con un `useRef` síncrono (`saliendoRef`) que `RutaProtegida` consulta antes de
   redirigir — un `state` de React llega demasiado tarde para esta carrera.
4. **Recargar en una ruta profunda (`/solicitudes/:id`) producía 404** (`Refused to execute
   script... wrong MIME type`). `HtmlRspackPlugin` escribía `<script src="main.js">` relativo,
   que se resolvía contra la ruta profunda en vez de la raíz. Es exactamente la clase de
   defecto que el escenario A6 existe para atrapar, y no había aparecido antes porque toda
   prueba previa usó navegación cliente, nunca una recarga real de navegador. Corregido con
   `<base href="/" />` en ambas apps.
5. **Fallo silencioso en el flujo de renovación silenciosa** ("IFrame timed out without a
   response", bloqueando la carga inicial de la app). `CallbackPage` y el bootstrap standalone
   llamaban `signinRedirectCallback()` sin condición, pero esa misma ruta también se carga
   dentro del iframe oculto del renewal silencioso, donde la petición es de otro tipo.
   Corregido usando `signinCallback()`, que despacha internamente según el tipo de solicitud
   guardado.
6. **Carrera entre el token y Redux bloqueaba la primera llamada a la API del remoto (401).**
   React ejecuta los efectos del hijo antes que los del padre en el montaje, así que el fetch
   de RTK Query en `ContenidoIndicadores` podía disparar antes de que el efecto del padre
   despachara `tokenActualizado`. Un primer intento de arreglo con una bandera booleana de una
   sola vez fue insuficiente: el doble-invocado de StrictMode podía fijarla en `true` durante un
   render temprano con el token aún nulo. Corregido comparando el último token despachado
   contra el de la sesión actual en cada render (`tokenDespachado === sesion.token`), no una
   bandera que se fija una sola vez.
7. **Estado anónimo federado indistinguible del estado de carga** ("Verificando sesión…" en
   ambos), lo que habría escondido un bloqueo real detrás de un mensaje de "todo en orden
   momentáneo". Corregido mostrando `estado="error"` con reintento para esa rama.
8. **El foco no entraba al campo de texto al abrir `DialogoTexto`, pese al `autoFocus` de MUI.**
   Con `multiline`, `TextareaAutosize` remonta el nodo tras medir su altura, y ese remonte
   ocurre después de que el `Dialog` ya había puesto el foco ahí — el foco terminaba en el
   contenedor del diálogo, no en el campo. Detectado navegando el diálogo por teclado en el
   navegador real, no por inspección de código (el patrón parece correcto a simple vista).
   Corregido con `inputRef` + `slotProps.transition.onEntered` (verificado contra
   `Dialog.d.ts` de `@mui/material` 7.3.11 antes de usarlo), que fija el foco cuando la
   transición de entrada ya terminó, después de cualquier remonte de `TextareaAutosize`.
9. **`store.ts` de `mfe-indicadores` rompía la generación de tipos federados** (`TS4023:
   Exported variable 'store' has or is using name 'EstadoAuth' ... but cannot be named`),
   porque `authSlice.ts` no exportaba esa interfaz. No es un error de compilación normal —
   `tsc --noEmit` pasaba porque nadie fuera del archivo nombra el tipo explícitamente— pero sí
   rompía `@module-federation/dts-plugin` al generar las declaraciones para el módulo expuesto.
   Corregido exportando `EstadoAuth`.
10. Storybook 10.6 con `builder-webpack5` no trae ya un loader de JSX propio: el soporte nativo
    de TypeScript de Webpack 5.110 (activo por defecto en Node ≥ 22.6) declara explícitamente
    que no soporta `.tsx`. Corregido agregando `swc-loader` (coherente con el resto del
    proyecto, que ya usa SWC vía rspack) en el `webpackFinal` de `.storybook/main.ts`, y
    desactivando `reactDocgen` porque ese generador de props fallaba aparte
    (`callback(): The callback was already called`) al analizar las props de unión
    discriminada de `EstadoChip`.

### Decisiones tomadas
- **El token vive solo en memoria**, nunca en `localStorage`/`sessionStorage` (regla
  `[BLOQUEANTE]` de CLAUDE.md): un espejo mínimo en Redux (`authSlice`, un solo campo) existe
  únicamente para que `prepareHeaders` de RTK Query pueda leerlo de forma síncrona.
- **`authBridge` singleton por `globalThis`/`Symbol.for`**, no por el mecanismo `shared` de
  Module Federation: ese mecanismo solo cubre paquetes de NPM declarados, nunca módulos locales
  de la aplicación.
- **`RutaProtegida` valida el rol en el cliente solo para UX** (ocultar/mostrar); el backend
  vuelve a decidir siempre (regla `[BLOQUEANTE]` #8) — así se documenta explícitamente en
  `DetalleSolicitudPage.tsx`: qué botón se muestra es una predicción de usabilidad, no una
  promesa de éxito.
- **Sin librería de gráficos** en `mfe-indicadores`: `BarraSimple` es una barra propia con
  `Box` de MUI. No estaba en el blueprint y el resumen analítico no lo necesita.

### Riesgos abiertos
- El bundle de `shell` y `mfe-indicadores` supera el límite recomendado de tamaño de asset
  (~400-450 KiB por chunk) en el build de producción de rspack. No bloquea el reto pero es
  candidato a `React.lazy` adicional o a revisar qué trae MUI si el reto pidiera optimizar carga.
- El mismo patrón sin exportar (`interface EstadoAuth` no exportada) existe también en
  `shell/src/store/authSlice.ts`; no falla porque `shell` no expone `store` vía Module
  Federation, así que se deja igual (CLAUDE.md regla 14: no se refactoriza lo que funciona y
  está fuera de la tarea actual).

### Pendiente
- Fase 6: cierre y entregables — Karate (A1, A3, recorrido REGISTRADA→EN_ATENCION→RESUELTA),
  JaCoCo, Dockerfiles multi-stage, Helm (validado por contenedor), `.gitlab-ci.yml`, diagramas
  C4 y de secuencia, los nueve ADRs, README con la estructura exigida, y `USO_DE_IA.md`.

---

## Fase 6 — Cierre y entregables — 3 de septiembre de 2026

### Completado
- **Dockerfiles multi-stage** para los cuatro componentes, usuario no root en los cuatro,
  `HEALTHCHECK` contra Actuator en los backends y contra `/` (nginx) en los frontend.
  `compose.yaml` de la raíz ampliado con los cuatro servicios de aplicación, encadenados por
  `depends_on: condition: service_healthy` sobre la infraestructura ya existente.
- **`docker compose up --build` verificado de punta a punta (A7)**: los 7 servicios llegan a
  `(healthy)`, y se probó el flujo real en un navegador contra las imágenes de producción
  (nginx, no el *dev server*): bandeja, detalle, y el MFE de Indicadores federado cargando
  `remoteEntry.js` de un origen a otro vía nginx.
- **JaCoCo activado de verdad**: el plugin estaba declarado en `pluginManagement` desde antes
  pero nunca se ejecutaba (sin `executions`). Se movió a `<plugins>` con `prepare-agent` +
  `report`, con exclusiones honestas (configuración, DTOs del borde REST, entidades JPA, la
  clase de arranque) en ambos servicios. Evidencia real capturada en
  `docs/evidencias/cobertura-jacoco.md`: `domain` 80%, `application` 64%,
  `indicadores-service` 27%, `infrastructure` y `bootstrap` sin cifra (0 pruebas / nada que
  medir tras excluir configuración), documentado como limitación real, no oculta.
- **Tres pruebas de caso de uso que faltaban**: `TomarSolicitudServiceTest`,
  `TransicionarSolicitudServiceTest`, `AgregarObservacionServiceTest` — completan la cobertura
  de la capa de aplicación con el mismo patrón de dobles en memoria que ya usaba
  `RegistrarSolicitudServiceTest` (no Mockito).
- **Suite Karate** (`apps/e2e-karate`): A1 (registro válido + idempotencia HTTP), A3 (rol
  insuficiente sin efectos persistidos) y el recorrido completo
  REGISTRADA→EN_ATENCION→RESUELTA con A4 (transición inválida) al final del mismo feature.
  Obtiene tokens del cliente `karate-e2e` por *direct grant* contra el Keycloak real del stack.
- **Chart de Helm** validado con `helm lint` y `helm template` (contenedor `alpine/helm`, sin
  instalar nada en el anfitrión) contra `values.yaml` base, `values-dev.yaml` y
  `values-qa.yaml`: Deployment con probes de liveness/readiness separadas, Service, ConfigMap
  por backend, referencia a Secret externo, requests/limits.
- **`.gitlab-ci.yml`**: `lint → build → test → coverage → package (Kaniko) → helm-validate →
  deploy (manual)`, con caché de `~/.m2` y `node_modules`.
- **Diagramas C4** (contexto, contenedores) y de **secuencia** del flujo principal con eventos,
  en Mermaid, en `docs/c4/`. Verificados renderizando de verdad con `mermaid.js` en el
  navegador (no solo revisando la sintaxis a ojo): los tres produjeron SVG válido y poblado.
- **Los once ADRs** de BLUEPRINT.md §13 (ADR-001 a ADR-011), uno por archivo en `docs/adr/`,
  formato contexto · decisión · alternativas · consecuencias.
- **README.md y `USO_DE_IA.md`** completados con la estructura exigida; sin placeholders
  `⟨…⟩` pendientes.

### Verificado con
```
docker compose up -d && docker compose ps
  7/7 servicios (healthy), incluidos los 4 nuevos de aplicación

mvnd clean verify   (solicitudes-service)
  Tests run: 26 (domain) + 21 (application) + 9 (ArchUnit) = 56, 0 fallos
  JaCoCo: domain 80%, application 64%

mvnd clean verify   (indicadores-service)
  Tests run: 5, 0 fallos
  JaCoCo: 27%

docker run --rm -v "$PWD/deploy/helm":/apps alpine/helm lint /apps/solicitudes-gov
  1 chart(s) linted, 0 chart(s) failed

docker run --rm -v "$PWD/deploy/helm":/apps alpine/helm template ... -f values-dev.yaml
docker run --rm -v "$PWD/deploy/helm":/apps alpine/helm template ... -f values-qa.yaml
  10 recursos generados en cada uno, YAML valido

mvnd test   (apps/e2e-karate), contra el stack real vía la red de compose:
  Tests run: 8, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 79.72 s
  ver docs/evidencias/salida-karate.txt
```

### Defectos encontrados y corregidos
1. **JaCoCo nunca se ejecutaba** pese a estar "configurado": el plugin vivía en
   `pluginManagement` sin ninguna `execution`, así que `mvn verify` jamás lo invocaba. El
   síntoma no era un error — simplemente no existía `target/site/jacoco/`. Corregido moviéndolo
   a `<plugins>` reales con `prepare-agent` y `report`.
2. **Los tres tests nuevos de casos de uso fallaban por un evento fantasma en el fixture.**
   `TomarSolicitudServiceTest` y `TransicionarSolicitudServiceTest` construían el estado
   inicial invocando los métodos de dominio directamente (`Solicitud.registrar(...)`,
   `.tomar(...)`, `.resolver(...)`) para dejar la solicitud en el estado que cada prueba
   necesitaba. Esas llamadas acumulan eventos en la lista interna del agregado que nunca se
   drenan hasta que alguien llama `drenarEventos()`; como el fixture nunca lo hacía, cuando el
   servicio bajo prueba ejecutaba SU propia transición y drenaba, arrastraba también los
   eventos del *setup* — una prueba que esperaba un evento publicado veía dos, tres o cuatro.
   Corregido llamando `drenarEventos()` al final de cada fixture, simulando que esas
   operaciones previas ya fueron persistidas y publicadas en transacciones anteriores, que es
   lo que ocurre en producción.
3. **El `pom.xml` de `apps/e2e-karate` tenía XML inválido**: el comentario de cabecera incluía
   el texto `docker compose up --build`, y XML prohíbe `--` dentro de un comentario en
   cualquier posición que no sea el cierre `-->`. Maven fallaba con
   `Non-parseable POM ... in comment after two dashes`. No se detectó al escribir el archivo
   porque el editor no valida XML; se detectó al intentar ejecutar Karate por primera vez.
   Corregido reformulando el comentario sin el doble guión.
4. **Directorio corrupto `deploy/helm;C`** apareció en el árbol de trabajo, resultado de un
   comando con una ruta mal escapada en una sesión anterior. Vacío, sin rastro en git; se
   eliminó directamente.
5. **Karate fallaba en dos capas distintas al ejecutarlo por primera vez contra el stack real**,
   ninguna visible por inspección del `.feature`:
   - `karate-config.js` construía los cuatro tokens llamando
     `karate.call('classpath:...', {tokenUrl, usuario, password})` desde la función de
     arranque (`fn()`), antes de que exista un scenario propio. En ese contexto de arranque el
     objeto de argumentos **no se inyecta**, ni como variables sueltas ni como `__arg`
     (se probaron ambas formas documentadas y ambas fallan con `ReferenceError` en ese punto
     concreto). Corregido con `karate.set(nombre, valor)` antes de cada `karate.call()`, que sí
     opera de forma fiable sobre el contexto de ejecución actual.
   - Con eso resuelto, **todas las peticiones seguían devolviendo 401** pese a que el token se
     obtenía sin error. Causa real: Keycloak (`KC_HOSTNAME_STRICT=false`) graba el claim `iss`
     del token según el header `Host` de la petición que lo generó. Karate corre dentro de la
     red de `compose` y llega a Keycloak por el nombre interno `keycloak:8080`, así que el
     token quedaba con `iss=http://keycloak:8080/...`; los backends validan contra
     `http://localhost:8080/...` (el issuer que ve el navegador — el mismo problema que ya
     documentan `application.yml` de ambos servicios, aquí reproducido desde el lado del
     cliente de pruebas). Corregido forzando `header Host = 'localhost:8080'` en la petición a
     Keycloak dentro de `obtener-token.feature`, sin cambiar a qué dirección se conecta
     realmente Karate.
   - Un tercer defecto menor en el camino: `karate-config.js` llamaba
     `classpath:obtener-token.feature` sin el prefijo de subcarpeta; el archivo vive en
     `classpath:solicitudes/obtener-token.feature`. Corregido la ruta.
6. **`docker compose up`/`build` chocaba con contenedores huérfanos** de pruebas manuales de
   sesiones anteriores (`solicitudes-app` en 8081, `indicadores-app` en 8082, más dos
   `rspack serve` de la Fase 5 ocupando 3000/3001), y en un punto de esta sesión **los 7
   contenedores del stack aparecieron `Exited (255)` simultáneamente** — la marca de tiempo
   idéntica en los siete descarta un fallo de aplicación y apunta a un reinicio de Docker
   Desktop/WSL2. En ambos casos la resolución fue la misma: identificar qué ocupaba el puerto o
   confirmar que el stack completo había muerto a la vez, liberar/limpiar, y volver a
   `docker compose up -d` — el propio A7 aplicado como procedimiento de recuperación, no solo
   como demostración inicial.

### Decisiones tomadas
- **Se redactan los once ADRs de la tabla del blueprint (§13, ADR-001 a ADR-011)**, no nueve:
  `PROMPT_MAESTRO.md` menciona "los nueve del blueprint" en la instrucción de arranque de esta
  fase, pero la propia tabla del blueprint —fuente de verdad declarada en `CLAUDE.md`— lista
  once. La discrepancia es un desfase de conteo entre documentos (ADR-010 y ADR-011 se
  agregaron en el registro de correcciones v1.0→v1.1, después de que se escribiera el conteo
  original), no una instrucción deliberada de omitir dos decisiones ya tomadas y ya
  referenciadas desde el propio código.
- **`swc-loader` en el Dockerfile de Storybook y `minimum-release-age=0` en `.npmrc`** (ambos
  ya aplicados en la Fase 5/6 de frontend) se mantienen: son necesidades técnicas para que el
  build sea reproducible en un host limpio, no atajos de conveniencia.
- **JaCoCo no excluye adaptadores, mappers ni manejadores de error sin prueba** solo para subir
  la cifra reportada. Se documenta la cobertura real, baja donde no hay prueba todavía, en vez
  de inflarla — coherente con la advertencia explícita del blueprint contra la cobertura
  inflada por código trivial.

### Riesgos abiertos
- `infrastructure` (Solicitudes) no tiene ninguna prueba propia; toda su cobertura depende de
  Karate (extremo a extremo) hasta que existan pruebas de integración dedicadas. Documentado
  como limitación en el README, no oculto.
- El bundle de `shell`/`mfe-indicadores` sigue por encima del límite recomendado de tamaño de
  asset (heredado de la Fase 5, sin cambios en esta fase).

### Pendiente
- Ninguno dentro del alcance de la Fase 6. Lo que quedó deliberadamente fuera (Prometheus/
  Grafana en Compose, Testcontainers, despliegue Helm contra un clúster real, purga de
  `outbox_evento`, ejecución del pipeline contra un GitLab real) está declarado como
  limitación explícita en el README §11, no como trabajo olvidado.

---

## Cierre visual — 3 de septiembre de 2026

### Completado
- **Tema visual compartido** (`apps/frontend/shared/src/tema.ts`), consumido por el shell y por
  el remoto. Vive en `shared` a proposito: dos `createTheme` distintos producen dos escalas de
  color y de sombra, y la diferencia se nota justo en la frontera federada.
- Paleta institucional con profundidad (azul en degradado, grises calidos), tipografia con
  jerarquia real (tracking negativo en titulos, cifras tabulares), sombras en dos capas
  (contacto + difusion) y radios consistentes.
- **Barra superior** con degradado, subtitulo de producto y el rol como pastilla en vez de
  texto suelto sobre la barra.
- **Tabla de la bandeja** con encabezado diferenciado, filas con mas aire y `maxWidth="lg"`:
  con el ancho por defecto las columnas de fecha se separaban del resto.
- **Chips de estado y prioridad** en estilo "soft" (fondo tenue + borde del mismo tono) en vez
  del relleno saturado de MUI: con varias filas, los chips solidos competian entre si.
- **Barras del resumen analitico** con degradado, ancho minimo visible (antes un valor pequeño
  frente al maximo quedaba en 1-2 px y parecia cero) y etiquetas presentadas como texto
  (`ATENCION_CIUDADANA` -> `Atencion ciudadana`): el identificador tecnico de la dimension no
  debe llegar al usuario final.
- **Tiempo medio hasta resolucion** convertido en metrica destacada; como parrafo con un
  divisor huerfano encima se leia como nota al pie, no como el indicador de negocio que es.
- Alturas igualadas (`height: 100%`) en las tarjetas del detalle y de indicadores: las columnas
  quedaban con altura dispar segun el contenido.

### Verificado con
```
corepack pnpm exec tsc --noEmit   (shell y mfe-indicadores)   0 errores
corepack pnpm test                (shell)                     30/30
docker compose up -d --build shell mfe-indicadores            7/7 (healthy)
Revision visual en navegador: bandeja, detalle e indicadores federados
```

### Decisiones tomadas
- **El tema morado del remoto en modo standalone se retira.** En la fase 2 era una sonda
  deliberada: si el remoto federado aparecia morado, significaba que traia su propio Emotion en
  vez de heredar el del host. Esa sonda ya cumplio su funcion y quedo verificada; mantenerla
  ahora solo produciria dos identidades visuales para el mismo producto.
- **Sin libreria de graficos, otra vez.** Las barras mejoradas siguen siendo `Box` con
  degradado: el salto visual no justificaba una dependencia nueva fuera del blueprint.
