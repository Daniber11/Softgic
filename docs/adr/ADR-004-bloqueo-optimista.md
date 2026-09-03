# ADR-004 — Bloqueo optimista sobre pesimista para la concurrencia (A2)

## Contexto

Dos analistas pueden intentar tomar la misma solicitud `REGISTRADA` casi al mismo tiempo (el
escenario de aceptación A2). Solo uno debe tener éxito; el otro debe recibir un error que
indique que el estado cambió bajo sus pies, no una excepción genérica de base de datos.

Una versión anterior de este documento (v1.0) describía **dos** mecanismos a la vez: `@Version`
de Hibernate **y** un `UPDATE ... WHERE estado = 'REGISTRADA'` con verificación de filas
afectadas. Tener dos soluciones al mismo problema es indefendible frente a un evaluador: no se
puede responder con precisión cuál de las dos está protegiendo realmente la operación. Este
ADR documenta la corrección aplicada en v1.1.

## Decisión

Se usa **únicamente `@Version`** (bloqueo optimista de Hibernate) sobre la entidad
`SolicitudEntity`. Cuando dos transacciones leen la misma fila y ambas intentan actualizarla,
la segunda escritura falla porque el número de versión ya no coincide: Hibernate lanza
`OptimisticLockingFailureException`, que el adaptador REST traduce a `409 Conflicto`.

## Alternativas consideradas

- **`UPDATE ... WHERE estado = 'REGISTRADA'` con comprobación de filas afectadas**, sin
  `@Version`. Descartada por una razón de fondo: esa cláusula `WHERE` es una regla de negocio
  —"solo se puede tomar desde REGISTRADA"— escrita en SQL, es decir, la máquina de estados
  filtrándose desde el agregado `Solicitud` hacia la capa de infraestructura. Esa regla tiene un
  único dueño legítimo, el agregado; duplicarla en una sentencia condicional garantiza que algún
  día las dos copias diverjan en silencio (por ejemplo, si se agrega un nuevo estado intermedio
  y se actualiza el agregado pero no la cláusula SQL, o viceversa).
- **Ambos mecanismos juntos** (la versión 1.0 de este blueprint). Descartada: además de la razón
  anterior, el `UPDATE` condicional protege solo la transición cubierta explícitamente por su
  cláusula `WHERE` —tomar—, mientras que `@Version` protege **todas** las transiciones
  (resolver, devolver, cerrar) sin tener que repetir la condición a mano en cada una.
- **Bloqueo pesimista** (`SELECT ... FOR UPDATE`). Descartado: bajo la contención esperada en
  este sistema —decenas de analistas, no miles de escrituras concurrentes por segundo— el costo
  de mantener filas bloqueadas durante toda la transacción no se justifica, y el comportamiento
  determinista y fácil de testear con dos hilos favorece al optimista para este caso de uso.

## Consecuencias

- `@Version` protege de forma uniforme las cuatro transiciones del ciclo de vida sin necesidad
  de repetir la regla de negocio en SQL.
- El comportamiento es determinista y se demuestra con un test de integración de dos hilos
  compitiendo por la misma fila.
- El cliente que recibe `409` debe reintentar (típicamente, refrescando el detalle de la
  solicitud); la UI comunica esto en vez de mostrar un error genérico.
- Documentado también como corrección C2 en el registro de correcciones del blueprint
  (v1.0 → v1.1).
