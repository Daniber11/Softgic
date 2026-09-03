# ADR-001 — RabbitMQ sobre Kafka como transporte de eventos

## Contexto

El Servicio de Solicitudes necesita publicar hechos de dominio (`SolicitudRegistrada`,
`SolicitudTomada`, etc.) para que el Servicio de Indicadores construya un modelo de lectura.
Ambos productos, RabbitMQ y Kafka, resuelven el transporte. La decisión se toma bajo una
restricción de entorno concreta: el anfitrión de desarrollo y evaluación dispone de 8 GB para
Docker, compartidos entre SQL Server (2 GB), Keycloak, dos JVM de aplicación y el propio broker.
La evaluación además incluye una demostración en vivo, donde poder **mostrar** el comportamiento
del sistema (una DLQ con mensajes, un reintento en curso) pesa tanto como que exista.

## Decisión

Se usa **RabbitMQ 3.13 (variante `management`)** como único transporte de eventos, con un
exchange topic durable, colas quorum, dead-letter exchange y publisher confirms.

## Alternativas consideradas

| Criterio | RabbitMQ | Kafka |
|---|---|---|
| Memoria en local | ~200 MB | ~1 GB |
| Configuración en Compose | Trivial | KRaft, cluster id, advertised listeners |
| Evidencia visual en demo | UI de management muestra DLQ y reintentos sin herramientas extra | Requiere Kafdrop u otra herramienta adicional |
| Replay del modelo de lectura | No nativo | Sí — ventaja real de Kafka |

Kafka se descartó por el costo de memoria (5× superior) y por la complejidad de configuración
en un Compose que ya aloja SQL Server, Keycloak y dos servicios Spring Boot en un presupuesto
acotado. La pérdida de replay nativo —la ventaja real de Kafka— se compensa: el modelo
analítico puede reconstruirse reproyectando desde `outbox_evento`, que actúa como log durable
del sistema (ver ADR-002).

## Consecuencias

- El acoplamiento a un log de eventos con retención ilimitada (replay) se sacrifica; la
  reconstrucción del modelo de lectura depende de conservar `outbox_evento`, no de la
  retención del broker.
- La UI de administración de RabbitMQ (puerto 15672) queda como evidencia visual de reintentos
  y de la dead-letter queue, útil en la entrevista técnica.
- Si el volumen de eventos creciera varios órdenes de magnitud, o si un futuro consumidor
  necesitara reprocesar semanas de historia sin tocar la base operacional, esta decisión
  debería revisarse.
