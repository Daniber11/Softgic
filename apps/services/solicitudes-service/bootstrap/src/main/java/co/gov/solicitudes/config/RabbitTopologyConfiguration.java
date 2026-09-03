package co.gov.solicitudes.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Topologia de RabbitMQ.
 *
 * <p>La declara el productor para que el stack quede utilizable con un solo arranque, sin pasos
 * manuales en la consola de administracion. Las declaraciones son idempotentes: si el consumidor
 * las declara igual, no hay conflicto.
 *
 * <p>La cola es <b>quorum</b> y no classic: replica el log entre nodos y sobrevive a la caida de
 * uno. En un stack local de una sola instancia no cambia nada, pero es la eleccion correcta para
 * el destino productivo y no cuesta nada tomarla desde el principio.
 */
@Configuration
public class RabbitTopologyConfiguration {

  private static final String PATRON_TODOS_LOS_EVENTOS = "solicitud.#";

  private final String exchange;
  private final String exchangeDlx;
  private final String cola;
  private final String colaDlq;

  public RabbitTopologyConfiguration(
      @Value("${solicitudes.mensajeria.exchange}") String exchange,
      @Value("${solicitudes.mensajeria.exchange-dlx}") String exchangeDlx,
      @Value("${solicitudes.mensajeria.cola-indicadores}") String cola,
      @Value("${solicitudes.mensajeria.cola-indicadores-dlq}") String colaDlq) {
    this.exchange = exchange;
    this.exchangeDlx = exchangeDlx;
    this.cola = cola;
    this.colaDlq = colaDlq;
  }

  @Bean
  public TopicExchange exchangeDeEventos() {
    return new TopicExchange(exchange, true, false);
  }

  /** Fanout: todo lo que muere va a la unica cola de descarte, sin criterio de enrutamiento. */
  @Bean
  public FanoutExchange exchangeDeDescartes() {
    return new FanoutExchange(exchangeDlx, true, false);
  }

  @Bean
  public Queue colaDeIndicadores() {
    return QueueBuilder.durable(cola).quorum().deadLetterExchange(exchangeDlx).build();
  }

  @Bean
  public Queue colaDeDescartes() {
    return QueueBuilder.durable(colaDlq).quorum().build();
  }

  @Bean
  public Binding bindingDeIndicadores() {
    return BindingBuilder.bind(colaDeIndicadores())
        .to(exchangeDeEventos())
        .with(PATRON_TODOS_LOS_EVENTOS);
  }

  @Bean
  public Binding bindingDeDescartes() {
    return BindingBuilder.bind(colaDeDescartes()).to(exchangeDeDescartes());
  }
}
