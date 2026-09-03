package co.gov.indicadores.config;

import java.time.Clock;
import java.util.Map;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.amqp.RabbitProperties;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.interceptor.RetryOperationsInterceptor;
import org.springframework.retry.policy.SimpleRetryPolicy;

/**
 * Topologia declarada tambien desde el consumidor, y politica de reintento del listener.
 *
 * <p>Que ambos servicios declaren la topologia no es duplicacion accidental: las declaraciones de
 * AMQP son idempotentes mientras coincidan, y asi cualquiera de los dos puede arrancar primero sin
 * que el otro tenga que existir. Si solo la declarara el productor, levantar Indicadores en
 * solitario fallaria por una cola inexistente.
 *
 * <p>La cola es <b>quorum</b> y tiene DLX. Un mensaje que agota los reintentos, o que se rechaza
 * por ilegible, termina en la cola de descartes en lugar de reencolarse para siempre.
 */
@Configuration
public class RabbitConfiguration {

  private static final String PATRON_TODOS_LOS_EVENTOS = "solicitud.#";

  private final String exchange;
  private final String exchangeDlx;
  private final String cola;
  private final String colaDlq;

  public RabbitConfiguration(
      @Value("${indicadores.mensajeria.exchange}") String exchange,
      @Value("${indicadores.mensajeria.exchange-dlx}") String exchangeDlx,
      @Value("${indicadores.mensajeria.cola}") String cola,
      @Value("${indicadores.mensajeria.cola-dlq}") String colaDlq) {
    this.exchange = exchange;
    this.exchangeDlx = exchangeDlx;
    this.cola = cola;
    this.colaDlq = colaDlq;
  }

  @Bean
  public TopicExchange exchangeDeEventos() {
    return new TopicExchange(exchange, true, false);
  }

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

  /**
   * Politica de reintento del listener, con una exclusion deliberada.
   *
   * <p><b>Defecto real detectado al verificar el escenario de la DLQ.</b> Sin esta exclusion, un
   * mensaje con JSON ilegible se reintentaba tres veces con el backoff completo (1s, 2s...) antes
   * de llegar a la cola de descartes: se vio en los logs, con marcas de tiempo separadas
   * exactamente por esos intervalos. El interceptor de reintento de Spring AMQP envuelve
   * cualquier excepcion que salga del listener, incluida
   * {@link AmqpRejectAndDontRequeueException}, y solo decide "sin reencolar" cuando ya se agotaron
   * los intentos: por si sola, esa excepcion no evita el reintento, solo evita que el mensaje
   * vuelva a la cola al final.
   *
   * <p>Un JSON malformado nunca va a parsear mejor en el segundo intento ni en el tercero: es un
   * fallo deterministico, no transitorio. Excluirlo de la politica de reintento hace que vaya
   * directo a la DLQ, que es el comportamiento que el codigo ya afirmaba tener.
   *
   * <p>Los fallos que si ameritan reintento —una base de datos momentaneamente inaccesible, RabbitMQ
   * reconectando— siguen la politica configurada en application.yml sin cambios.
   */
  @Bean
  public RetryOperationsInterceptor retryOperationsInterceptor(RabbitProperties propiedades) {
    RabbitProperties.ListenerRetry retry = propiedades.getListener().getSimple().getRetry();

    SimpleRetryPolicy politica =
        new SimpleRetryPolicy(
            retry.getMaxAttempts(),
            Map.of(AmqpRejectAndDontRequeueException.class, false),
            true);

    ExponentialBackOffPolicy backOff = new ExponentialBackOffPolicy();
    backOff.setInitialInterval(retry.getInitialInterval().toMillis());
    backOff.setMultiplier(retry.getMultiplier());
    backOff.setMaxInterval(retry.getMaxInterval().toMillis());

    return RetryInterceptorBuilder.stateless()
        .retryPolicy(politica)
        .backOffPolicy(backOff)
        .recoverer(new RejectAndDontRequeueRecoverer())
        .build();
  }

  /**
   * Reemplaza la factoria autoconfigurada por Spring Boot para instalar la politica de reintento
   * de arriba.
   *
   * <p>{@code SimpleRabbitListenerContainerFactory} no expone {@code setAdviceChain} directamente:
   * la cadena de interceptores se instala sobre el contenedor ya creado, mediante un
   * {@code ContainerCustomizer}. Se llama a {@code configurer.configure(...)} primero para
   * conservar el resto de ajustes que vienen de {@code application.yml} —modo de acknowledge,
   * prefetch, requeue-rejected—, y despues se sobreescribe el customizer para instalar el
   * interceptor propio en lugar del que Boot habria instalado por las propiedades de retry.
   */
  @Bean
  public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
      SimpleRabbitListenerContainerFactoryConfigurer configurer,
      ConnectionFactory connectionFactory,
      RetryOperationsInterceptor retryOperationsInterceptor) {

    SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
    configurer.configure(factory, connectionFactory);
    factory.setContainerCustomizer(
        contenedor -> contenedor.setAdviceChain(retryOperationsInterceptor));
    return factory;
  }

  /** Reloj inyectable: permite fijarlo en pruebas y evita Instant.now() disperso. */
  @Bean
  public Clock clock() {
    return Clock.systemUTC();
  }
}
