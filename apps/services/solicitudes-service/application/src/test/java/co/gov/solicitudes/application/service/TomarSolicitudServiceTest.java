package co.gov.solicitudes.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.gov.solicitudes.application.command.FiltroSolicitudes;
import co.gov.solicitudes.application.command.TomarSolicitudCommand;
import co.gov.solicitudes.application.port.out.EventoPublicadorPort;
import co.gov.solicitudes.application.port.out.SolicitudRepositoryPort;
import co.gov.solicitudes.application.result.Pagina;
import co.gov.solicitudes.domain.event.EventoDominio;
import co.gov.solicitudes.domain.event.SolicitudTomada;
import co.gov.solicitudes.domain.exception.SolicitudNoEncontradaException;
import co.gov.solicitudes.domain.exception.TransicionInvalidaException;
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
 * Escenario A2: aqui no se prueba concurrencia -eso vive en el test de integracion contra
 * Hibernate, donde el bloqueo optimista real puede fallar-, sino la orquestacion: que tomar
 * mueve el estado, entrega el evento, y que buscar una solicitud inexistente o tomar una que ya
 * no esta en REGISTRADA se propaga como la excepcion de dominio correspondiente sin tocar el
 * repositorio.
 */
@DisplayName("Caso de uso: tomar solicitud")
class TomarSolicitudServiceTest {

  private static final Instant AHORA = Instant.parse("2026-09-03T10:00:00Z");
  private static final Actor ANALISTA = new Actor("u-analista", Rol.ANALISTA);

  private RepositorioEnMemoria repositorio;
  private PublicadorEnMemoria publicador;
  private TomarSolicitudService servicio;

  @BeforeEach
  void prepararEscenario() {
    repositorio = new RepositorioEnMemoria();
    publicador = new PublicadorEnMemoria();
    servicio = new TomarSolicitudService(repositorio, publicador, () -> AHORA);
  }

  private static Solicitud solicitudRegistrada() {
    return Solicitud.registrar(
        new SolicitudId(UUID.randomUUID()),
        new CodigoSolicitud("SOL-2026-000001"),
        "Impresora sin tinta",
        "La impresora del piso 3 no imprime.",
        new CategoriaId(UUID.randomUUID()),
        Prioridad.MEDIA,
        new Actor("u-solicitante", Rol.SOLICITANTE),
        AHORA);
  }

  @Test
  @DisplayName("debeMoverLaSolicitudAEnAtencionYAsignarElAnalista")
  void debeMoverLaSolicitudAEnAtencionYAsignarElAnalista() {
    Solicitud registrada = solicitudRegistrada();
    repositorio.guardar(registrada);

    Solicitud resultado = servicio.tomar(new TomarSolicitudCommand(registrada.id(), ANALISTA));

    assertThat(resultado.estado()).isEqualTo(EstadoSolicitud.EN_ATENCION);
  }

  @Test
  @DisplayName("debeEntregarElEventoSolicitudTomadaAlOutbox")
  void debeEntregarElEventoSolicitudTomadaAlOutbox() {
    Solicitud registrada = solicitudRegistrada();
    repositorio.guardar(registrada);

    servicio.tomar(new TomarSolicitudCommand(registrada.id(), ANALISTA));

    assertThat(publicador.publicados).hasSize(1).first().isInstanceOf(SolicitudTomada.class);
  }

  @Test
  @DisplayName("debeRechazarUnaSolicitudInexistenteSinPublicarNada")
  void debeRechazarUnaSolicitudInexistenteSinPublicarNada() {
    SolicitudId idInexistente = new SolicitudId(UUID.randomUUID());

    assertThatThrownBy(() -> servicio.tomar(new TomarSolicitudCommand(idInexistente, ANALISTA)))
        .isInstanceOf(SolicitudNoEncontradaException.class);

    assertThat(publicador.publicados).isEmpty();
  }

  @Test
  @DisplayName("debePropagarLaTransicionInvalidaSiYaFueTomadaSinPublicarNada")
  void debePropagarLaTransicionInvalidaSiYaFueTomadaSinPublicarNada() {
    Solicitud registrada = solicitudRegistrada();
    registrada.tomar(ANALISTA, AHORA);
    repositorio.guardar(registrada);

    Actor otroAnalista = new Actor("u-otro-analista", Rol.ANALISTA);
    assertThatThrownBy(
            () -> servicio.tomar(new TomarSolicitudCommand(registrada.id(), otroAnalista)))
        .isInstanceOf(TransicionInvalidaException.class);

    assertThat(publicador.publicados).isEmpty();
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
