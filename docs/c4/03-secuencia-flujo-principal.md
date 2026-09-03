# C4 — Secuencia: flujo principal con eventos

Recorrido completo del escenario de aceptación cubierto por Karate:
`REGISTRADA → EN_ATENCION → RESUELTA`, mostrando el patrón Transactional Outbox (ADR-002) en
el primer paso con detalle completo, y de forma abreviada en los siguientes dos —el mecanismo
es idéntico en las tres transiciones—.

```mermaid
sequenceDiagram
    actor Solicitante
    actor Analista
    participant Shell
    participant Solicitudes as Servicio de Solicitudes
    participant BDOp as solicitudes_db
    participant Poller as Publicador Outbox (@Scheduled, 500ms)
    participant Rabbit as RabbitMQ
    participant Indicadores as Servicio de Indicadores
    participant BDAn as indicadores_db

    Solicitante->>Shell: Completa el formulario de registro
    Shell->>Solicitudes: POST /solicitudes (Idempotency-Key, Bearer JWT)
    activate Solicitudes
    Solicitudes->>Solicitudes: Solicitud.registrar(...) — valida rol SOLICITANTE
    Solicitudes->>BDOp: BEGIN TX — INSERT solicitud + INSERT outbox_evento(PENDIENTE)
    Note right of BDOp: Agregado y evento en la MISMA transacción:<br/>nunca se persiste uno sin el otro (ADR-002)
    BDOp-->>Solicitudes: COMMIT
    Solicitudes-->>Shell: 201 Created + Location
    deactivate Solicitudes
    Shell-->>Solicitante: Solicitud SOL-2026-000123 registrada

    loop cada 500ms
        Poller->>BDOp: SELECT TOP(@lote) ... WITH (UPDLOCK, READPAST)<br/>WHERE estado='PENDIENTE'
        BDOp-->>Poller: fila del evento SolicitudRegistrada
        Poller->>Rabbit: publish (publisher confirms habilitados)
        Rabbit-->>Poller: confirmación del broker
        Poller->>BDOp: UPDATE outbox_evento SET estado='PUBLICADO'
    end

    Rabbit->>Indicadores: entrega SolicitudRegistrada (cola indicadores.solicitudes)
    activate Indicadores
    Indicadores->>BDAn: BEGIN TX — INSERT evento_procesado(eventId) + INSERT hecho_transicion
    Note right of BDAn: eventId es PK de evento_procesado:<br/>un reenvío del mismo evento viola la PK,<br/>aborta y se hace ack sin duplicar (A5)
    BDAn-->>Indicadores: COMMIT
    Indicadores-->>Rabbit: ack
    deactivate Indicadores

    Note over Solicitante,BDAn: --- Analista toma la solicitud ---
    Analista->>Shell: Click "Tomar"
    Shell->>Solicitudes: POST /solicitudes/{id}/asignaciones (Bearer JWT, rol ANALISTA)
    Solicitudes->>BDOp: BEGIN TX — UPDATE solicitud (version++) + INSERT outbox_evento
    Note right of BDOp: @Version detecta si otro analista ya tomó<br/>la misma solicitud primero → 409 (A2)
    BDOp-->>Solicitudes: COMMIT
    Solicitudes-->>Shell: 201 Created
    Poller->>Rabbit: publica SolicitudTomada (mismo patrón que arriba)
    Rabbit->>Indicadores: entrega SolicitudTomada
    Indicadores->>BDAn: proyecta el hecho (mismo patrón que arriba)

    Note over Solicitante,BDAn: --- El mismo analista resuelve ---
    Analista->>Shell: Click "Resolver"
    Shell->>Solicitudes: POST /solicitudes/{id}/transiciones {accion: RESOLVER}
    Solicitudes->>BDOp: BEGIN TX — UPDATE solicitud + INSERT outbox_evento
    BDOp-->>Solicitudes: COMMIT
    Solicitudes-->>Shell: 200 OK
    Poller->>Rabbit: publica SolicitudResuelta
    Rabbit->>Indicadores: entrega SolicitudResuelta
    Indicadores->>BDAn: proyecta el hecho
```

**Notas de lectura:**

- El publicador del outbox y el consumidor de Indicadores corren en bucles independientes del
  ciclo de petición HTTP: la respuesta al Shell (`201`/`200`) nunca espera a que el evento
  llegue a Indicadores. La vista analítica se actualiza con una latencia de hasta ~500 ms más
  el tiempo de entrega del broker.
- `Idempotency-Key` (visible solo en el primer `POST`) protege contra un reintento de red del
  **cliente**; `evento_procesado` protege contra un reenvío del **broker**. Son dos mecanismos
  de idempotencia distintos, en dos puntos distintos del flujo, y ninguno sustituye al otro.
