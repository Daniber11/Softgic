# ADR-002 — Transactional Outbox sobre publicación directa o CDC

## Contexto

Cada caso de uso que muta el agregado `Solicitud` también debe anunciar el hecho ocurrido
(`SolicitudRegistrada`, `SolicitudTomada`, ...) para que el Servicio de Indicadores lo
proyecte. Publicar directamente al broker desde el caso de uso tiene un problema de
consistencia de dos caras: publicar **antes** del commit de base de datos puede anunciar un
hecho que la transacción luego revierte; publicar **después** del commit puede perder el
evento si el proceso muere en el instante entre el commit y el envío al broker. Ninguna de las
dos secuencias garantiza que "lo persistido" y "lo publicado" coincidan siempre.

## Decisión

Se implementa el patrón **Transactional Outbox**: el caso de uso, en una única transacción de
base de datos, persiste el agregado y escribe los eventos pendientes en la tabla
`outbox_evento`. Un publicador agendado (`@Scheduled`, cada 500 ms) toma lotes con
`SELECT TOP (@lote) ... WITH (UPDLOCK, READPAST) WHERE estado = 'PENDIENTE'`, los envía al
broker con publisher confirms habilitados, y solo tras la confirmación marca la fila como
`PUBLICADO`.

## Alternativas consideradas

- **Publicación directa desde el caso de uso**, antes o después del commit. Descartada por el
  problema de consistencia descrito arriba: ambas variantes tienen una ventana en la que la
  base de datos y el broker pueden discrepar sobre qué ocurrió.
- **Change Data Capture (CDC)** sobre el log de transacciones de SQL Server (Debezium u
  homólogo). Ofrece la misma garantía sin una tabla intermedia ni un poller propio, pero exige
  desplegar y operar un conector adicional (Debezium, Kafka Connect o equivalente), fuera del
  presupuesto de tiempo e infraestructura de este reto, y añade una pieza más al arranque que
  el escenario A7 tendría que encadenar.
- **Publicar y reintentar con at-most-once** (aceptar la pérdida ocasional de un evento).
  Descartada: el modelo analítico existe precisamente para ofrecer trazabilidad confiable; un
  evento perdido en silencio contradice el propósito del sistema.

## Consecuencias

- Garantía resultante: **at-least-once**. Nunca se publica un hecho no confirmado por la
  transacción de base de datos, y nunca se pierde un hecho ya confirmado. La duplicidad
  ocasional (un evento reenviado tras un fallo de confirmación) la absorbe el consumidor
  mediante consumo idempotente (ver ADR de referencia en el contrato de eventos, §6.5 del
  blueprint, y el escenario A5).
- `UPDLOCK, READPAST` permite que varias instancias del Servicio de Solicitudes tomen lotes
  distintos del outbox sin bloquearse entre sí, condición necesaria para que el patrón escale
  horizontalmente en SQL Server.
- Se introduce una latencia de publicación de hasta 500 ms (el intervalo del poller), aceptable
  para un modelo de lectura analítico que no exige tiempo real.
- La tabla `outbox_evento` crece indefinidamente si no se purgan las filas `PUBLICADO`; no hay
  purga automatizada en el alcance actual (ver limitaciones en el README).
