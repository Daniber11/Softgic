package co.gov.indicadores.service;

import co.gov.indicadores.consumer.SobreEvento;
import co.gov.indicadores.consumer.TipoDeEvento;
import co.gov.indicadores.persistence.DimCategoriaRepository;
import co.gov.indicadores.persistence.EventoProcesadoRepository;
import co.gov.indicadores.persistence.HechoTransicion;
import co.gov.indicadores.persistence.HechoTransicionRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Proyecta un evento de dominio sobre el modelo de lectura.
 *
 * <p><b>Escenario A5: consumo idempotente.</b> Todo el metodo {@link #proyectar} corre en una
 * unica transaccion que hace dos cosas: inserta el eventId en {@code evento_procesado} y escribe
 * la fila del hecho. Si el evento ya se proyecto, el INSERT viola la clave primaria y la
 * transaccion completa se revierte, incluida la fila del hecho. El conteo no se altera.
 *
 * <p>Se inserta primero y se comprueba despues, no al reves. Consultar "existe ya?" y luego
 * insertar deja una ventana entre ambas operaciones en la que dos consumidores concurrentes
 * pueden pasar los dos. La clave primaria no tiene esa ventana: es atomica por construccion.
 */
@Service
public class ProyeccionService {

  private static final Logger LOG = LoggerFactory.getLogger(ProyeccionService.class);

  /** Categoria de respaldo cuando el catalogo replicado no conoce la del evento. */
  private static final UUID CATEGORIA_DESCONOCIDA =
      UUID.fromString("00000000-0000-0000-0000-000000000000");

  private final HechoTransicionRepository hechos;
  private final EventoProcesadoRepository procesados;
  private final DimCategoriaRepository categorias;
  private final Clock clock;

  public ProyeccionService(
      HechoTransicionRepository hechos,
      EventoProcesadoRepository procesados,
      DimCategoriaRepository categorias,
      Clock clock) {
    this.hechos = hechos;
    this.procesados = procesados;
    this.categorias = categorias;
    this.clock = clock;
  }

  /**
   * Proyecta el evento. Devuelve false si ya estaba proyectado.
   *
   * @throws org.springframework.dao.DataIntegrityViolationException si el evento es duplicado; el
   *     consumidor la traduce en un ack silencioso
   */
  @Transactional
  public void proyectar(SobreEvento sobre) {
    Optional<TipoDeEvento> tipo = TipoDeEvento.desde(sobre.type());
    if (tipo.isEmpty()) {
      // Un tipo desconocido no es un error: puede venir de una version mas nueva
      // del productor. Se marca como procesado para no reintentarlo y se ignora.
      LOG.info("Evento de tipo desconocido, se ignora sin proyectar: {}", sobre.type());
      marcarProcesado(sobre);
      return;
    }

    // La marca va PRIMERO. Si el evento es duplicado, esto revienta aqui y nada
    // de lo que sigue llega a ejecutarse.
    marcarProcesado(sobre);

    hechos.save(construirHecho(sobre, tipo.get()));

    LOG.debug(
        "Evento {} proyectado para el agregado {}. correlationId={}",
        sobre.type(),
        sobre.aggregateId(),
        sobre.correlationId());
  }

  /**
   * Deja constancia de que el evento se proyecto.
   *
   * <p>Usa un INSERT explicito, no {@code save()}: con identificador asignado, {@code save()}
   * degenera en un UPDATE y el duplicado pasaria inadvertido. El detalle esta explicado en
   * {@link EventoProcesadoRepository#insertarMarca}.
   */
  private void marcarProcesado(SobreEvento sobre) {
    procesados.insertarMarca(sobre.eventId(), sobre.type(), Instant.now(clock));
  }

  private HechoTransicion construirHecho(SobreEvento sobre, TipoDeEvento tipo) {
    LocalDate fecha = sobre.occurredAt().atZone(ZoneOffset.UTC).toLocalDate();
    int fechaKey = aClaveDeFecha(fecha);
    hechos.asegurarFecha(fechaKey, fecha.toString());

    return HechoTransicion.de(
        sobre.aggregateId(),
        sobre.data().codigo(),
        fechaKey,
        resolverCategoria(sobre),
        tipo.estadoOrigenKey(),
        tipo.estadoDestinoKey(),
        tipo.rolKey(),
        calcularDuracionMinutos(sobre),
        sobre.occurredAt());
  }

  /** AAAAMMDD: legible al inspeccionar los datos y ordenable como entero. */
  private int aClaveDeFecha(LocalDate fecha) {
    return fecha.getYear() * 10_000 + fecha.getMonthValue() * 100 + fecha.getDayOfMonth();
  }

  private int resolverCategoria(SobreEvento sobre) {
    UUID categoriaId = sobre.data().categoriaId();
    return categorias
        .findByCategoriaId(categoriaId)
        .or(
            () -> {
              LOG.warn(
                  "Categoria {} no replicada en dim_categoria; se usa DESCONOCIDA.", categoriaId);
              return categorias.findByCategoriaId(CATEGORIA_DESCONOCIDA);
            })
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "dim_categoria no contiene la fila DESCONOCIDA; la migracion V1 no se aplico."))
        .getCategoriaKey();
  }

  /**
   * Minutos transcurridos desde la transicion anterior del mismo expediente.
   *
   * <p>Nulo en la primera: no hay nada desde lo cual medir. Devolver cero seria mas comodo y
   * arruinaria cualquier promedio, porque metria un valor real que nunca ocurrio.
   */
  private Integer calcularDuracionMinutos(SobreEvento sobre) {
    return hechos
        .ultimaTransicionDe(sobre.aggregateId())
        .map(anterior -> (int) Duration.between(anterior, sobre.occurredAt()).toMinutes())
        .orElse(null);
  }
}
