package co.gov.indicadores.consumer;

import co.gov.indicadores.config.CorrelacionContexto;
import co.gov.indicadores.service.ProyeccionService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/**
 * Consumidor de los eventos publicados por el Servicio de Solicitudes.
 *
 * <p>Aqui se decide, para cada fallo, si el mensaje se reintenta o se descarta. Esa decision es lo
 * que separa una cola sana de una que acumula veneno:
 *
 * <ul>
 *   <li><b>Duplicado</b> ({@link DataIntegrityViolationException}): se confirma sin reintentar. El
 *       evento ya se proyecto; reintentarlo fallaria igual para siempre. Es el escenario A5.
 *   <li><b>Payload ilegible</b> ({@link JsonProcessingException}): se descarta a la DLQ de
 *       inmediato, sin gastar los tres reintentos. Un JSON malformado no se arregla solo.
 *   <li><b>Cualquier otro fallo</b>: se deja propagar para que Spring AMQP aplique los reintentos
 *       con retroceso exponencial y, agotados, lo envie a la DLQ. Son los fallos transitorios, como
 *       una base de datos momentaneamente inaccesible.
 * </ul>
 */
@Component
public class SolicitudEventoConsumer {

  private static final Logger LOG = LoggerFactory.getLogger(SolicitudEventoConsumer.class);

  private final ObjectMapper objectMapper;
  private final ProyeccionService proyeccion;

  public SolicitudEventoConsumer(ObjectMapper objectMapper, ProyeccionService proyeccion) {
    this.objectMapper = objectMapper;
    this.proyeccion = proyeccion;
  }

  @RabbitListener(queues = "${indicadores.mensajeria.cola}")
  public void recibir(String mensaje) {
    SobreEvento sobre = deserializar(mensaje);

    // La correlacion viaja en el sobre y se traslada al MDC: asi una misma
    // operacion se puede seguir en los logs de los dos servicios con un solo
    // identificador, desde la peticion HTTP hasta la fila del hecho.
    CorrelacionContexto.establecer(sobre.correlationId());
    try {
      proyeccion.proyectar(sobre);

    } catch (DataIntegrityViolationException e) {
      // Escenario A5. No es un error de operacion: es la garantia funcionando.
      // Se confirma el mensaje para que el broker no lo reintente indefinidamente.
      LOG.info(
          "Evento {} ya estaba proyectado (eventId {}). Se confirma sin alterar los conteos.",
          sobre.type(),
          sobre.eventId());

    } finally {
      CorrelacionContexto.limpiar();
    }
  }

  /**
   * Un mensaje ilegible va directo a la DLQ, sin gastar reintentos.
   *
   * <p>{@link org.springframework.amqp.AmqpRejectAndDontRequeueException} evita que el mensaje
   * vuelva a la cola de origen; sin ella se reencolaria sin fin y bloquearia el consumo de todos
   * los demas. Pero esa excepcion por si sola NO evita el reintento: el interceptor de Spring AMQP
   * envuelve cualquier fallo del listener y solo decide "sin reencolar" cuando ya agoto los
   * intentos configurados.
   *
   * <p>Que este tipo de fallo llegue a la DLQ en el primer intento, sin retroceder con el backoff
   * de 1s, 2s..., depende de que {@code RetryOperationsInterceptor} en
   * {@link co.gov.indicadores.config.RabbitConfiguration#retryOperationsInterceptor} excluya esta
   * excepcion de su politica de reintento. Un JSON malformado no se arregla reintentandolo.
   */
  private SobreEvento deserializar(String mensaje) {
    try {
      return objectMapper.readValue(mensaje, SobreEvento.class);
    } catch (JsonProcessingException e) {
      LOG.error("Mensaje ilegible, se envia a la cola de descartes: {}", e.getOriginalMessage());
      throw new org.springframework.amqp.AmqpRejectAndDontRequeueException(
          "El mensaje no cumple el contrato del sobre de evento.", e);
    }
  }

}
