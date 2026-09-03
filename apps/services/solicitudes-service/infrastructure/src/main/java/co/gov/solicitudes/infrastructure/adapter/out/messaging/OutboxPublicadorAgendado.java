package co.gov.solicitudes.infrastructure.adapter.out.messaging;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Drena el outbox hacia RabbitMQ.
 *
 * <p>Corre en su propia transaccion, separada por completo de la del negocio. El caso de uso ya
 * confirmo; a partir de aqui publicar es un problema de reintento, no de consistencia.
 *
 * <p><b>Garantia resultante: at-least-once.</b> Si el broker confirma pero el proceso muere antes
 * de marcar la fila como publicada, el evento se reenviara. Esa duplicidad es aceptable porque el
 * consumidor es idempotente: la tabla evento_procesado, con clave primaria en el eventId, la
 * absorbe. La alternativa, marcar como publicado antes de enviar, produciria perdida de eventos,
 * que no es recuperable.
 */
@Component
public class OutboxPublicadorAgendado {

  private static final Logger LOG = LoggerFactory.getLogger(OutboxPublicadorAgendado.class);

  private final OutboxJpaRepository outbox;
  private final RabbitTemplate rabbitTemplate;
  private final Clock clock;
  private final String exchange;
  private final int tamanioLote;
  private final int intentosMaximos;

  public OutboxPublicadorAgendado(
      OutboxJpaRepository outbox,
      RabbitTemplate rabbitTemplate,
      Clock clock,
      @Value("${solicitudes.mensajeria.exchange}") String exchange,
      @Value("${solicitudes.outbox.tamanio-lote}") int tamanioLote,
      @Value("${solicitudes.outbox.intentos-maximos}") int intentosMaximos) {
    this.outbox = outbox;
    this.rabbitTemplate = rabbitTemplate;
    this.clock = clock;
    this.exchange = exchange;
    this.tamanioLote = tamanioLote;
    this.intentosMaximos = intentosMaximos;
  }

  @Scheduled(fixedDelayString = "${solicitudes.outbox.intervalo-ms}")
  @Transactional
  public void drenar() {
    List<OutboxEventoEntity> lote = outbox.tomarLotePendiente(tamanioLote);
    if (lote.isEmpty()) {
      return;
    }

    lote.forEach(this::publicarFila);
  }

  private void publicarFila(OutboxEventoEntity fila) {
    try {
      rabbitTemplate.convertAndSend(exchange, fila.getRoutingKey(), fila.getPayload());
      fila.marcarPublicado(Instant.now(clock));

    } catch (AmqpException e) {
      // Se captura AmqpException y no Exception: un fallo del broker es una
      // condicion de operacion esperable y reintentable. Cualquier otro error
      // seria un defecto y debe propagarse para que se vea.
      fila.registrarIntentoFallido(e.getMessage(), intentosMaximos);
      LOG.warn(
          "Fallo la publicacion del evento {} (id {}), intento {} de {}. correlationId={}",
          fila.getTipo(),
          fila.getId(),
          fila.getIntentos(),
          intentosMaximos,
          fila.getCorrelationId(),
          e);
    }
  }
}
