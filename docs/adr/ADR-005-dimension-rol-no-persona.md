# ADR-005 — Dimensión rol en lugar de persona en el modelo analítico

## Contexto

El enunciado pide evitar replicar datos personales innecesarios en el modelo de lectura
analítico. Una lectura superficial de esa exigencia podría llevar a afirmar que "por el bus no
viajan datos personales", pero eso sería falso y fácilmente refutable abriendo la tabla
`outbox_evento`: el sobre del evento **sí transporta** identificadores de persona
(`solicitanteId`, `analistaId`, `supervisorId`), porque el Servicio de Indicadores necesita
saber *qué rol* actuó en cada transición, y ese dato solo puede derivarse del identificador del
actor en el momento en que el hecho ocurrió.

## Decisión

El identificador de persona viaja en el evento (transporte), pero **la proyección analítica lo
descarta**: `hecho_transicion` referencia `dim_rol`, no un identificador de persona. La
minimización de datos se aplica en el punto donde el dato se volvería **permanente**, no en el
transporte efímero.

## Alternativas consideradas

- **No incluir el identificador de persona en el evento**, para poder decir literalmente "el
  bus nunca transporta datos personales". Descartada: sin el identificador del actor, el
  consumidor no tiene forma de derivar el rol que ejecutó la transición (un mismo usuario puede,
  en teoría, tener más de un rol; el rol relevante es el que efectivamente actuó, no un rol
  genérico del sistema), y el modelo analítico perdería la dimensión de rol que el propio
  enunciado pide poder consultar.
- **Replicar el identificador de persona también en el modelo analítico** ("por si se necesita
  después"). Descartada: es exactamente la replicación innecesaria que el enunciado pide evitar,
  y no aporta a las consultas expuestas (solicitudes por estado, por categoría, tendencia
  diaria), que son agregaciones, no auditorías nominales.

## Consecuencias

- `dim_rol` reemplaza cualquier columna de persona en el esquema estrella; las consultas
  analíticas agregan por rol, categoría, estado y fecha, nunca por individuo.
- Quien audite el repositorio y solo mire `indicadores_db` no encontrará ningún identificador de
  persona; quien audite `outbox_evento` sí los encontrará, y esa asimetría es exactamente la
  redacción correcta del requisito, no una contradicción.
- Si en el futuro se necesitara una vista de "carga de trabajo por analista" (nominal), habría
  que evaluar explícitamente esa necesidad de negocio contra el principio de minimización, no
  añadir el dato "por si acaso".
- Corrección C4 del registro de correcciones del blueprint (v1.0 → v1.1): la redacción de v1.0
  ("por el bus no viajan datos personales") se sustituyó por esta más precisa.
