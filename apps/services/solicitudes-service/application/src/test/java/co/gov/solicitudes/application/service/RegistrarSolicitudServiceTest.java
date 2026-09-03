package co.gov.solicitudes.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.gov.solicitudes.application.command.RegistrarSolicitudCommand;
import co.gov.solicitudes.application.port.out.CategoriaRepositoryPort;
import co.gov.solicitudes.application.port.out.EventoPublicadorPort;
import co.gov.solicitudes.application.port.out.GeneradorCodigoPort;
import co.gov.solicitudes.application.port.out.RelojPort;
import co.gov.solicitudes.application.command.FiltroSolicitudes;
import co.gov.solicitudes.application.port.out.SolicitudRepositoryPort;
import co.gov.solicitudes.application.result.Pagina;
import co.gov.solicitudes.domain.event.EventoDominio;
import co.gov.solicitudes.domain.event.SolicitudRegistrada;
import co.gov.solicitudes.domain.exception.AccionNoPermitidaException;
import co.gov.solicitudes.domain.exception.CategoriaInactivaException;
import co.gov.solicitudes.domain.model.Actor;
import co.gov.solicitudes.domain.model.Categoria;
import co.gov.solicitudes.domain.model.CategoriaId;
import co.gov.solicitudes.domain.model.CodigoSolicitud;
import co.gov.solicitudes.domain.model.EstadoSolicitud;
import co.gov.solicitudes.domain.model.Prioridad;
import co.gov.solicitudes.domain.model.Rol;
import co.gov.solicitudes.domain.model.Solicitud;
import co.gov.solicitudes.domain.model.SolicitudId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Escenario A1.
 *
 * <p>Se usan dobles en memoria en lugar de Mockito. Un fake que se comporta como un repositorio de
 * verdad permite afirmar sobre el resultado —que la solicitud quedo guardada, que el evento se
 * entrego— en vez de sobre las llamadas que se hicieron, que es lo que hace fragiles a las pruebas
 * con mocks.
 */
@DisplayName("Caso de uso: registrar solicitud")
class RegistrarSolicitudServiceTest {

  private static final Instant AHORA = Instant.parse("2026-09-03T10:00:00Z");
  private static final CategoriaId CATEGORIA_ACTIVA = new CategoriaId(UUID.randomUUID());
  private static final CategoriaId CATEGORIA_INACTIVA = new CategoriaId(UUID.randomUUID());
  private static final Actor SOLICITANTE = new Actor("u-solicitante", Rol.SOLICITANTE);

  private RepositorioEnMemoria repositorio;
  private PublicadorEnMemoria publicador;
  private RegistrarSolicitudService servicio;

  @BeforeEach
  void prepararEscenario() {
    repositorio = new RepositorioEnMemoria();
    publicador = new PublicadorEnMemoria();

    CatalogoEnMemoria catalogo = new CatalogoEnMemoria();
    catalogo.agregar(new Categoria(CATEGORIA_ACTIVA, "SOPORTE_TECNICO", "Soporte tecnico", true));
    catalogo.agregar(new Categoria(CATEGORIA_INACTIVA, "ARCHIVADO", "Archivado", false));

    servicio =
        new RegistrarSolicitudService(
            repositorio,
            catalogo,
            publicador,
            () -> new CodigoSolicitud("SOL-2026-000001"),
            () -> AHORA);
  }

  private static RegistrarSolicitudCommand comandoValido(CategoriaId categoria, Actor actor) {
    return new RegistrarSolicitudCommand(
        "Impresora sin tinta", "La impresora del piso 3 no imprime.", categoria, Prioridad.MEDIA, actor);
  }

  @Test
  @DisplayName("debePersistirLaSolicitudEnEstadoRegistrada")
  void debePersistirLaSolicitudEnEstadoRegistrada() {
    Solicitud resultado = servicio.registrar(comandoValido(CATEGORIA_ACTIVA, SOLICITANTE));

    assertThat(resultado.estado()).isEqualTo(EstadoSolicitud.REGISTRADA);
    assertThat(resultado.codigo().valor()).isEqualTo("SOL-2026-000001");
    assertThat(repositorio.guardadas).hasSize(1);
  }

  @Test
  @DisplayName("debeEntregarElEventoSolicitudRegistradaAlOutbox")
  void debeEntregarElEventoSolicitudRegistradaAlOutbox() {
    servicio.registrar(comandoValido(CATEGORIA_ACTIVA, SOLICITANTE));

    assertThat(publicador.publicados).hasSize(1).first().isInstanceOf(SolicitudRegistrada.class);
  }

  @Test
  @DisplayName("debeCrearLaEntradaInicialDelHistorial")
  void debeCrearLaEntradaInicialDelHistorial() {
    Solicitud resultado = servicio.registrar(comandoValido(CATEGORIA_ACTIVA, SOLICITANTE));

    assertThat(resultado.historial()).hasSize(1);
    assertThat(resultado.historial().getFirst().origen()).isEmpty();
  }

  @Test
  @DisplayName("debeFecharLaSolicitudConElRelojInyectadoYNoConLaHoraDelSistema")
  void debeFecharLaSolicitudConElRelojInyectadoYNoConLaHoraDelSistema() {
    Solicitud resultado = servicio.registrar(comandoValido(CATEGORIA_ACTIVA, SOLICITANTE));

    assertThat(resultado.creadaEn()).isEqualTo(AHORA);
  }

  @Test
  @DisplayName("debeRechazarUnaCategoriaInactivaSinPersistirNiPublicar")
  void debeRechazarUnaCategoriaInactivaSinPersistirNiPublicar() {
    assertThatThrownBy(() -> servicio.registrar(comandoValido(CATEGORIA_INACTIVA, SOLICITANTE)))
        .isInstanceOf(CategoriaInactivaException.class);

    assertThat(repositorio.guardadas).isEmpty();
    assertThat(publicador.publicados).isEmpty();
  }

  @Test
  @DisplayName("debeRechazarUnaCategoriaInexistente")
  void debeRechazarUnaCategoriaInexistente() {
    assertThatThrownBy(
            () ->
                servicio.registrar(
                    comandoValido(new CategoriaId(UUID.randomUUID()), SOLICITANTE)))
        .isInstanceOf(CategoriaInactivaException.class);
  }

  /** Escenario A3 en la capa de aplicacion: rol insuficiente, ningun efecto. */
  @Test
  @DisplayName("debeRechazarQueUnAnalistaRegistreSinPersistirNiPublicar")
  void debeRechazarQueUnAnalistaRegistreSinPersistirNiPublicar() {
    Actor analista = new Actor("u-analista", Rol.ANALISTA);

    assertThatThrownBy(() -> servicio.registrar(comandoValido(CATEGORIA_ACTIVA, analista)))
        .isInstanceOf(AccionNoPermitidaException.class);

    assertThat(repositorio.guardadas).isEmpty();
    assertThat(publicador.publicados).isEmpty();
  }

  // ---------------------------------------------------------------------------
  //  Dobles en memoria
  // ---------------------------------------------------------------------------

  private static final class RepositorioEnMemoria implements SolicitudRepositoryPort {
    private final List<Solicitud> guardadas = new ArrayList<>();

    @Override
    public Solicitud guardar(Solicitud solicitud) {
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

  private static final class CatalogoEnMemoria implements CategoriaRepositoryPort {
    private final Map<CategoriaId, Categoria> porId = new HashMap<>();

    void agregar(Categoria categoria) {
      porId.put(categoria.id(), categoria);
    }

    @Override
    public List<Categoria> listarActivas() {
      return porId.values().stream().filter(Categoria::activa).toList();
    }

    @Override
    public Optional<Categoria> buscarPorId(CategoriaId id) {
      return Optional.ofNullable(porId.get(id));
    }
  }

  private static final class PublicadorEnMemoria implements EventoPublicadorPort {
    private final List<EventoDominio> publicados = new ArrayList<>();

    @Override
    public void publicar(List<EventoDominio> eventos) {
      publicados.addAll(eventos);
    }
  }

  /** Comprueba que los puertos funcionales se pueden satisfacer con una lambda (LSP). */
  @Test
  @DisplayName("losPuertosDeRelojYGeneradorDebenSerSustituiblesPorUnaLambda")
  void losPuertosDeRelojYGeneradorDebenSerSustituiblesPorUnaLambda() {
    RelojPort reloj = () -> AHORA;
    GeneradorCodigoPort generador = () -> new CodigoSolicitud("SOL-2026-000002");

    assertThat(reloj.ahora()).isEqualTo(AHORA);
    assertThat(generador.siguiente().valor()).isEqualTo("SOL-2026-000002");
  }
}
