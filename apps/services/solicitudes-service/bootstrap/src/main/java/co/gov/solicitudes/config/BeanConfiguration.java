package co.gov.solicitudes.config;

import co.gov.solicitudes.application.port.in.AgregarObservacionUseCase;
import co.gov.solicitudes.application.port.in.ConsultarCategoriasQuery;
import co.gov.solicitudes.application.port.in.ConsultarDetalleQuery;
import co.gov.solicitudes.application.port.in.ConsultarSolicitudesQuery;
import co.gov.solicitudes.application.port.in.RegistrarSolicitudUseCase;
import co.gov.solicitudes.application.port.in.TomarSolicitudUseCase;
import co.gov.solicitudes.application.port.in.TransicionarSolicitudUseCase;
import co.gov.solicitudes.application.port.out.CategoriaRepositoryPort;
import co.gov.solicitudes.application.port.out.EventoPublicadorPort;
import co.gov.solicitudes.application.port.out.GeneradorCodigoPort;
import co.gov.solicitudes.application.port.out.RelojPort;
import co.gov.solicitudes.application.port.out.SolicitudRepositoryPort;
import co.gov.solicitudes.application.service.AgregarObservacionService;
import co.gov.solicitudes.application.service.ConsultarCategoriasService;
import co.gov.solicitudes.application.service.ConsultarDetalleService;
import co.gov.solicitudes.application.service.ConsultarSolicitudesService;
import co.gov.solicitudes.application.service.RegistrarSolicitudService;
import co.gov.solicitudes.application.service.TomarSolicitudService;
import co.gov.solicitudes.application.service.TransicionarSolicitudService;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Cableado explicito de los casos de uso.
 *
 * <p><b>Por que este archivo existe.</b> Los casos de uso son clases planas, sin
 * {@code @Service} ni ninguna otra anotacion de Spring: es lo que permite que el modulo
 * application no dependa del framework y que ArchUnit lo verifique. El precio es que alguien tiene
 * que instanciarlos, y ese alguien es el modulo de arranque.
 *
 * <p>El beneficio no es solo la pureza: aqui se lee de un vistazo el grafo completo de
 * dependencias del sistema. Con anotaciones repartidas por decenas de clases, esa informacion solo
 * existe en tiempo de ejecucion.
 */
@Configuration
public class BeanConfiguration {

  /**
   * Plantilla transaccional usada para envolver los casos de uso de escritura.
   *
   * <p><b>Por que no se usa {@code @Transactional} sobre estos metodos.</b> Se intento y no
   * funciona: la anotacion sobre un metodo {@code @Bean} no hace transaccional al objeto
   * devuelto. Spring proxifica los metodos anotados de un bean, no la factoria que lo produce, de
   * modo que el caso de uso quedaba sin transaccion y el outbox se habria escrito fuera de ella.
   *
   * <p>El fallo no paso inadvertido porque el adaptador del outbox declara
   * {@code Propagation.MANDATORY}: en lugar de escribir eventos sin transaccion, la primera
   * peticion fallo de inmediato senalando la causa. Esa anotacion existe justamente para esto.
   */
  @Bean
  public TransactionTemplate transactionTemplate(PlatformTransactionManager gestor) {
    return new TransactionTemplate(gestor);
  }

  /**
   * Los comandos se envuelven en una transaccion mediante un decorador.
   *
   * <p>La transaccion debe abarcar el caso de uso completo: es lo que garantiza que el agregado y
   * sus eventos en el outbox se confirmen juntos o no se confirme ninguno. Ponerla aqui, y no
   * dentro del caso de uso, mantiene la capa de aplicacion libre de Spring.
   *
   * <p>Que el decorador quepa en una lambda es consecuencia directa de que los puertos de entrada
   * tengan un solo metodo (ISP). Ademas deja la frontera transaccional visible en el mismo archivo
   * donde se lee el grafo de dependencias.
   */
  @Bean
  public RegistrarSolicitudUseCase registrarSolicitudUseCase(
      SolicitudRepositoryPort solicitudes,
      CategoriaRepositoryPort categorias,
      EventoPublicadorPort eventos,
      GeneradorCodigoPort generadorCodigo,
      RelojPort reloj,
      @Qualifier("transactionTemplate") TransactionTemplate tx) {
    var servicio =
        new RegistrarSolicitudService(solicitudes, categorias, eventos, generadorCodigo, reloj);
    return comando -> tx.execute(estado -> servicio.registrar(comando));
  }

  @Bean
  public TomarSolicitudUseCase tomarSolicitudUseCase(
      SolicitudRepositoryPort solicitudes,
      EventoPublicadorPort eventos,
      RelojPort reloj,
      @Qualifier("transactionTemplate") TransactionTemplate tx) {
    var servicio = new TomarSolicitudService(solicitudes, eventos, reloj);
    return comando -> tx.execute(estado -> servicio.tomar(comando));
  }

  @Bean
  public TransicionarSolicitudUseCase transicionarSolicitudUseCase(
      SolicitudRepositoryPort solicitudes,
      EventoPublicadorPort eventos,
      RelojPort reloj,
      @Qualifier("transactionTemplate") TransactionTemplate tx) {
    var servicio = new TransicionarSolicitudService(solicitudes, eventos, reloj);
    return comando -> tx.execute(estado -> servicio.transicionar(comando));
  }

  @Bean
  public AgregarObservacionUseCase agregarObservacionUseCase(
      SolicitudRepositoryPort solicitudes,
      RelojPort reloj,
      @Qualifier("transactionTemplate") TransactionTemplate tx) {
    var servicio = new AgregarObservacionService(solicitudes, reloj);
    return comando -> tx.execute(estado -> servicio.agregar(comando));
  }

  /**
   * Transaccion de solo lectura para las consultas.
   *
   * <p>Las lecturas tambien necesitan transaccion, y no por atomicidad. Con
   * {@code open-in-view: false} —que es la configuracion correcta, porque mantener la sesion
   * abierta durante el renderizado esconde consultas N+1 detras de la serializacion— la sesion de
   * Hibernate se cierra al salir del metodo transaccional. Sin esta plantilla, mapear una
   * coleccion perezosa fuera de ella falla con LazyInitializationException, que es exactamente lo
   * que ocurrio la primera vez que se consulto un detalle.
   *
   * <p>{@code readOnly} ademas le indica al motor que no hay escrituras, lo que evita el chequeo
   * de suciedad al cerrar la sesion.
   */
  @Bean
  public TransactionTemplate transactionTemplateSoloLectura(PlatformTransactionManager gestor) {
    TransactionTemplate plantilla = new TransactionTemplate(gestor);
    plantilla.setReadOnly(true);
    return plantilla;
  }

  @Bean
  public ConsultarSolicitudesQuery consultarSolicitudesQuery(
      SolicitudRepositoryPort solicitudes,
      @Qualifier("transactionTemplateSoloLectura") TransactionTemplate tx) {
    var servicio = new ConsultarSolicitudesService(solicitudes);
    return (filtro, pagina, tamanio, consultante) ->
        tx.execute(estado -> servicio.consultar(filtro, pagina, tamanio, consultante));
  }

  @Bean
  public ConsultarDetalleQuery consultarDetalleQuery(
      SolicitudRepositoryPort solicitudes,
      @Qualifier("transactionTemplateSoloLectura") TransactionTemplate tx) {
    var servicio = new ConsultarDetalleService(solicitudes);
    return (id, consultante) -> tx.execute(estado -> servicio.consultar(id, consultante));
  }

  @Bean
  public ConsultarCategoriasQuery consultarCategoriasQuery(
      CategoriaRepositoryPort categorias,
      @Qualifier("transactionTemplateSoloLectura") TransactionTemplate tx) {
    var servicio = new ConsultarCategoriasService(categorias);
    return () -> tx.execute(estado -> servicio.listarActivas());
  }

  /**
   * Reloj del sistema.
   *
   * <p>Se declara como bean para que una prueba de integracion pueda sustituirlo por
   * {@code Clock.fixed(...)} y afirmar sobre fechas concretas. Es la razon de que
   * {@code Instant.now()} este prohibido en el dominio y en los casos de uso.
   */
  @Bean
  public Clock clock() {
    return Clock.systemUTC();
  }
}
