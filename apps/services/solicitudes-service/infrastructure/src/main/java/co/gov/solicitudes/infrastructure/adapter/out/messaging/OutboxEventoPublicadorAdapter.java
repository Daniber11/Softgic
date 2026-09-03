package co.gov.solicitudes.infrastructure.adapter.out.messaging;

import co.gov.solicitudes.application.port.out.EventoPublicadorPort;
import co.gov.solicitudes.domain.event.EventoDominio;
import co.gov.solicitudes.infrastructure.config.CorrelacionContexto;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Escritura de los eventos de dominio en el outbox.
 *
 * <p><b>Este adaptador NO habla con RabbitMQ.</b> Solo escribe filas, y lo hace obligatoriamente
 * dentro de la transaccion que ya abrio el caso de uso ({@code Propagation.MANDATORY}). Esa
 * anotacion es la garantia ejecutable del patron: si alguien invocara esto fuera de una
 * transaccion, fallaria de inmediato en lugar de escribir un evento que podria quedar huerfano.
 *
 * <p>Aqui es tambien donde el hecho de negocio se envuelve en el sobre —eventId, version,
 * correlationId, producer—. El dominio emite el hecho desnudo y no conoce ninguno de esos campos,
 * porque pertenecen al transporte.
 */
@Component
public class OutboxEventoPublicadorAdapter implements EventoPublicadorPort {

  private static final int VERSION_CONTRATO_EVENTO = 1;
  private static final String TIPO_AGREGADO = "Solicitud";
  private static final String PRODUCTOR = "solicitudes-service";

  private final OutboxJpaRepository outbox;
  private final ObjectMapper objectMapper;

  public OutboxEventoPublicadorAdapter(OutboxJpaRepository outbox, ObjectMapper objectMapper) {
    this.outbox = outbox;
    this.objectMapper = objectMapper;
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void publicar(List<EventoDominio> eventos) {
    eventos.stream().map(this::aFilaDeOutbox).forEach(outbox::save);
  }

  private OutboxEventoEntity aFilaDeOutbox(EventoDominio evento) {
    String correlationId = CorrelacionContexto.actual();

    return OutboxEventoEntity.pendiente(
        UUID.randomUUID(),
        evento.tipo(),
        VERSION_CONTRATO_EVENTO,
        evento.agregadoId(),
        TIPO_AGREGADO,
        evento.routingKey(),
        serializarSobre(evento, correlationId),
        correlationId,
        evento.ocurridoEn());
  }

  /** Construye el sobre completo. El campo data lleva el hecho tal como lo emitio el dominio. */
  private String serializarSobre(EventoDominio evento, String correlationId) {
    SobreEvento sobre =
        new SobreEvento(
            UUID.randomUUID(),
            evento.tipo(),
            VERSION_CONTRATO_EVENTO,
            evento.ocurridoEn().toString(),
            evento.agregadoId(),
            TIPO_AGREGADO,
            correlationId,
            correlationId,
            PRODUCTOR,
            evento);

    try {
      return objectMapper.writeValueAsString(sobre);
    } catch (JsonProcessingException e) {
      // Un evento que no se puede serializar es un defecto de programacion, no una
      // condicion de operacion: se propaga y revierte la transaccion completa. Es
      // preferible a persistir el agregado con un evento perdido.
      throw new IllegalStateException(
          "No se pudo serializar el evento %s del agregado %s."
              .formatted(evento.tipo(), evento.agregadoId()),
          e);
    }
  }

  /**
   * Sobre comun de todos los eventos.
   *
   * <p>Vive en infraestructura, no en el dominio: es el contrato de transporte con el consumidor.
   * causationId apunta al mismo valor que correlationId porque, en este flujo, la peticion HTTP es
   * a la vez el origen de la traza y la causa directa del evento.
   */
  private record SobreEvento(
      UUID eventId,
      String type,
      int version,
      String occurredAt,
      UUID aggregateId,
      String aggregateType,
      String correlationId,
      String causationId,
      String producer,
      EventoDominio data) {}
}
