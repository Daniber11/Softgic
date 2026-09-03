package co.gov.solicitudes.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.gov.solicitudes.application.command.AgregarObservacionCommand;
import co.gov.solicitudes.application.command.FiltroSolicitudes;
import co.gov.solicitudes.application.port.out.SolicitudRepositoryPort;
import co.gov.solicitudes.application.result.Pagina;
import co.gov.solicitudes.domain.exception.AccionNoPermitidaException;
import co.gov.solicitudes.domain.exception.SolicitudNoEncontradaException;
import co.gov.solicitudes.domain.model.Actor;
import co.gov.solicitudes.domain.model.CategoriaId;
import co.gov.solicitudes.domain.model.CodigoSolicitud;
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
 * Agregar una observacion no es una transicion (Solicitud.agregarObservacion, comentario en el
 * propio metodo): no emite evento de dominio. Lo que este caso de uso orquesta es la busqueda del
 * agregado y la persistencia; el rechazo por rol lo decide el agregado, no el servicio.
 */
@DisplayName("Caso de uso: agregar observacion")
class AgregarObservacionServiceTest {

  private static final Instant AHORA = Instant.parse("2026-09-03T10:00:00Z");

  private RepositorioEnMemoria repositorio;
  private AgregarObservacionService servicio;

  @BeforeEach
  void prepararEscenario() {
    repositorio = new RepositorioEnMemoria();
    servicio = new AgregarObservacionService(repositorio, () -> AHORA);
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
  @DisplayName("debeAgregarLaObservacionAlExpediente")
  void debeAgregarLaObservacionAlExpediente() {
    Solicitud registrada = solicitudRegistrada();
    repositorio.guardar(registrada);
    Actor analista = new Actor("u-analista", Rol.ANALISTA);

    Solicitud resultado =
        servicio.agregar(
            new AgregarObservacionCommand(registrada.id(), "Se reinicio el servicio.", analista));

    assertThat(resultado.observaciones()).hasSize(1);
    assertThat(resultado.observaciones().getFirst().texto()).isEqualTo("Se reinicio el servicio.");
  }

  @Test
  @DisplayName("debeRechazarQueUnSolicitanteObserveSinPersistir")
  void debeRechazarQueUnSolicitanteObserveSinPersistir() {
    Solicitud registrada = solicitudRegistrada();
    repositorio.guardar(registrada);
    Actor solicitante = new Actor("u-solicitante-2", Rol.SOLICITANTE);

    assertThatThrownBy(
            () ->
                servicio.agregar(
                    new AgregarObservacionCommand(registrada.id(), "Insisto.", solicitante)))
        .isInstanceOf(AccionNoPermitidaException.class);

    assertThat(repositorio.buscarPorId(registrada.id()).orElseThrow().observaciones()).isEmpty();
  }

  @Test
  @DisplayName("debeRechazarUnaSolicitudInexistente")
  void debeRechazarUnaSolicitudInexistente() {
    SolicitudId idInexistente = new SolicitudId(UUID.randomUUID());
    Actor analista = new Actor("u-analista", Rol.ANALISTA);

    assertThatThrownBy(
            () ->
                servicio.agregar(
                    new AgregarObservacionCommand(idInexistente, "Comentario.", analista)))
        .isInstanceOf(SolicitudNoEncontradaException.class);
  }

  // ---------------------------------------------------------------------------
  //  Doble en memoria
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
}
