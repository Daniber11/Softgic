package co.gov.solicitudes.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.gov.solicitudes.application.command.FiltroSolicitudes;
import co.gov.solicitudes.application.command.TransicionarSolicitudCommand;
import co.gov.solicitudes.application.port.out.EventoPublicadorPort;
import co.gov.solicitudes.application.port.out.SolicitudRepositoryPort;
import co.gov.solicitudes.application.result.Pagina;
import co.gov.solicitudes.domain.event.EventoDominio;
import co.gov.solicitudes.domain.event.SolicitudCerrada;
import co.gov.solicitudes.domain.event.SolicitudDevuelta;
import co.gov.solicitudes.domain.event.SolicitudResuelta;
import co.gov.solicitudes.domain.exception.AccionNoPermitidaException;
import co.gov.solicitudes.domain.exception.SolicitudNoEncontradaException;
import co.gov.solicitudes.domain.exception.TransicionInvalidaException;
import co.gov.solicitudes.domain.model.Accion;
import co.gov.solicitudes.domain.model.Actor;
import co.gov.solicitudes.domain.model.CategoriaId;
import co.gov.solicitudes.domain.model.CodigoSolicitud;
import co.gov.solicitudes.domain.model.EstadoSolicitud;
import co.gov.solicitudes.domain.model.Prioridad;
import co.gov.solicitudes.domain.model.Rol;
import co.gov.solicitudes.domain.model.Solicitud;
import co.gov.solicitudes.domain.model.SolicitudId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Resolver, devolver y cerrar comparten un unico caso de uso (BLUEPRINT 5.1, ADR-006). Cubre
 * A4 en la capa de aplicacion: la propagacion de la excepcion de dominio cuando la transicion no
 * corresponde al estado actual, y que ninguna de las tres persiste ni publica si el dominio la
 * rechaza.
 */
@DisplayName("Caso de uso: transicionar solicitud (resolver, devolver, cerrar)")
class TransicionarSolicitudServiceTest {

  private static final Instant AHORA = Instant.parse("2026-09-03T10:00:00Z");
  private static final Actor ANALISTA = new Actor("u-analista", Rol.ANALISTA);
  private static final Actor SUPERVISOR = new Actor("u-supervisor", Rol.SUPERVISOR);

  private RepositorioEnMemoria repositorio;
  private PublicadorEnMemoria publicador;
  private TransicionarSolicitudService servicio;

  @BeforeEach
  void prepararEscenario() {
    repositorio = new RepositorioEnMemoria();
    publicador = new PublicadorEnMemoria();
    servicio = new TransicionarSolicitudService(repositorio, publicador, () -> AHORA);
  }

  private static Solicitud solicitudEnAtencion() {
    Solicitud solicitud =
        Solicitud.registrar(
            new SolicitudId(UUID.randomUUID()),
            new CodigoSolicitud("SOL-2026-000001"),
            "Servidor de nomina sin respuesta",
            "El servicio no responde desde las 8am.",
            new CategoriaId(UUID.randomUUID()),
            Prioridad.ALTA,
            new Actor("u-solicitante", Rol.SOLICITANTE),
            AHORA);
    solicitud.tomar(ANALISTA, AHORA);
    // Se drena aqui: el fixture representa una solicitud ya persistida y
    // publicada en operaciones previas (registro + toma), no una que el
    // propio caso de uso bajo prueba acabara de crear.
    solicitud.drenarEventos();
    return solicitud;
  }

  private static Solicitud solicitudResuelta() {
    Solicitud solicitud = solicitudEnAtencion();
    solicitud.resolver(ANALISTA, AHORA);
    solicitud.drenarEventos();
    return solicitud;
  }

  @Test
  @DisplayName("debeResolverYEntregarElEventoSolicitudResuelta")
  void debeResolverYEntregarElEventoSolicitudResuelta() {
    Solicitud enAtencion = solicitudEnAtencion();
    repositorio.guardar(enAtencion);

    Solicitud resultado =
        servicio.transicionar(
            new TransicionarSolicitudCommand(enAtencion.id(), Accion.RESOLVER, null, ANALISTA));

    assertThat(resultado.estado()).isEqualTo(EstadoSolicitud.RESUELTA);
    assertThat(publicador.publicados).hasSize(1).first().isInstanceOf(SolicitudResuelta.class);
  }

  @Test
  @DisplayName("debeDevolverConMotivoYEntregarElEventoSolicitudDevuelta")
  void debeDevolverConMotivoYEntregarElEventoSolicitudDevuelta() {
    Solicitud resuelta = solicitudResuelta();
    repositorio.guardar(resuelta);

    Solicitud resultado =
        servicio.transicionar(
            new TransicionarSolicitudCommand(
                resuelta.id(), Accion.DEVOLVER, "Falta evidencia fotografica.", SUPERVISOR));

    assertThat(resultado.estado()).isEqualTo(EstadoSolicitud.EN_ATENCION);
    assertThat(publicador.publicados).hasSize(1).first().isInstanceOf(SolicitudDevuelta.class);
  }

  @Test
  @DisplayName("debeCerrarYEntregarElEventoSolicitudCerrada")
  void debeCerrarYEntregarElEventoSolicitudCerrada() {
    Solicitud resuelta = solicitudResuelta();
    repositorio.guardar(resuelta);

    Solicitud resultado =
        servicio.transicionar(
            new TransicionarSolicitudCommand(resuelta.id(), Accion.CERRAR, null, SUPERVISOR));

    assertThat(resultado.estado()).isEqualTo(EstadoSolicitud.CERRADA);
    assertThat(publicador.publicados).hasSize(1).first().isInstanceOf(SolicitudCerrada.class);
  }

  /** Escenario A4: RESUELTA -> REGISTRADA no existe en la tabla de transiciones. */
  @Test
  @DisplayName("debeRechazarUnaTransicionQueNoExisteEnLaTablaSinPublicarNada")
  void debeRechazarUnaTransicionQueNoExisteEnLaTablaSinPublicarNada() {
    Solicitud registrada =
        Solicitud.registrar(
            new SolicitudId(UUID.randomUUID()),
            new CodigoSolicitud("SOL-2026-000002"),
            "Impresora sin tinta",
            "La impresora del piso 3 no imprime.",
            new CategoriaId(UUID.randomUUID()),
            Prioridad.BAJA,
            new Actor("u-solicitante", Rol.SOLICITANTE),
            AHORA);
    repositorio.guardar(registrada);

    assertThatThrownBy(
            () ->
                servicio.transicionar(
                    new TransicionarSolicitudCommand(
                        registrada.id(), Accion.RESOLVER, null, ANALISTA)))
        .isInstanceOf(TransicionInvalidaException.class);

    assertThat(publicador.publicados).isEmpty();
  }

  /** Escenario A3: un analista no puede cerrar, esa accion es del supervisor. */
  @Test
  @DisplayName("debeRechazarElRolIncorrectoSinPublicarNada")
  void debeRechazarElRolIncorrectoSinPublicarNada() {
    Solicitud resuelta = solicitudResuelta();
    repositorio.guardar(resuelta);

    assertThatThrownBy(
            () ->
                servicio.transicionar(
                    new TransicionarSolicitudCommand(resuelta.id(), Accion.CERRAR, null, ANALISTA)))
        .isInstanceOf(AccionNoPermitidaException.class);

    assertThat(publicador.publicados).isEmpty();
  }

  @Test
  @DisplayName("debeRechazarUnaSolicitudInexistente")
  void debeRechazarUnaSolicitudInexistente() {
    SolicitudId idInexistente = new SolicitudId(UUID.randomUUID());

    assertThatThrownBy(
            () ->
                servicio.transicionar(
                    new TransicionarSolicitudCommand(
                        idInexistente, Accion.RESOLVER, null, ANALISTA)))
        .isInstanceOf(SolicitudNoEncontradaException.class);
  }

  // ---------------------------------------------------------------------------
  //  Dobles en memoria
  // ---------------------------------------------------------------------------

  private static final class RepositorioEnMemoria implements SolicitudRepositoryPort {
    private final List<Solicitud> guardadas = new ArrayList<>();

    @Override
    public Solicitud guardar(Solicitud solicitud) {
      guardadas.removeIf(s -> s.id().equals(solicitud.id()));
      guardadas.add(solicitud);
      return solicitud;
    }

    @Override
    public Optional<Solicitud> buscarPorId(SolicitudId id) {
      return guardadas.stream().filter(s -> s.id().equals(id)).findFirst();
    }

    @Override
    public boolean existeCodigo(String codigo) {
      return guardadas.stream().anyMatch(s -> s.codigo().valor().equals(codigo));
    }

    @Override
    public Pagina<Solicitud> buscar(FiltroSolicitudes filtro, int pagina, int tamanio) {
      return new Pagina<>(List.copyOf(guardadas), pagina, tamanio, guardadas.size());
    }
  }

  private static final class PublicadorEnMemoria implements EventoPublicadorPort {
    private final List<EventoDominio> publicados = new ArrayList<>();

    @Override
    public void publicar(List<EventoDominio> eventos) {
      publicados.addAll(eventos);
    }
  }
}
