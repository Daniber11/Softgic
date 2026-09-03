package co.gov.solicitudes.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatNoException;

import co.gov.solicitudes.domain.event.EventoDominio;
import co.gov.solicitudes.domain.event.SolicitudCerrada;
import co.gov.solicitudes.domain.event.SolicitudDevuelta;
import co.gov.solicitudes.domain.event.SolicitudRegistrada;
import co.gov.solicitudes.domain.event.SolicitudResuelta;
import co.gov.solicitudes.domain.event.SolicitudTomada;
import co.gov.solicitudes.domain.exception.AccionNoPermitidaException;
import co.gov.solicitudes.domain.exception.TransicionInvalidaException;
import co.gov.solicitudes.domain.exception.ValidacionDominioException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Pruebas del agregado Solicitud.
 *
 * <p>Son documentacion ejecutable: cada nombre describe una regla de negocio. No hay mocks
 * porque no hay nada que simular; el dominio no habla con nadie.
 */
@DisplayName("Agregado Solicitud")
class SolicitudTest {

  private static final Instant AHORA = Instant.parse("2026-09-03T10:00:00Z");
  private static final CategoriaId CATEGORIA = new CategoriaId(UUID.randomUUID());
  private static final CodigoSolicitud CODIGO = new CodigoSolicitud("SOL-2026-000001");

  private static final Actor SOLICITANTE = new Actor("u-solicitante", Rol.SOLICITANTE);
  private static final Actor ANALISTA = new Actor("u-analista", Rol.ANALISTA);
  private static final Actor OTRO_ANALISTA = new Actor("u-analista-2", Rol.ANALISTA);
  private static final Actor SUPERVISOR = new Actor("u-supervisor", Rol.SUPERVISOR);

  private static Solicitud registrada() {
    return Solicitud.registrar(
        new SolicitudId(UUID.randomUUID()),
        CODIGO,
        "Asunto de prueba",
        "Descripcion de prueba",
        CATEGORIA,
        Prioridad.MEDIA,
        SOLICITANTE,
        AHORA);
  }

  // ---------------------------------------------------------------------------
  //  Registro
  // ---------------------------------------------------------------------------

  @Nested
  @DisplayName("al registrarse")
  class AlRegistrarse {

    @Test
    @DisplayName("debeQuedarEnEstadoRegistradaYEmitirSolicitudRegistrada")
    void debeQuedarEnEstadoRegistradaYEmitirSolicitudRegistrada() {
      Solicitud solicitud = registrada();

      assertThat(solicitud.estado()).isEqualTo(EstadoSolicitud.REGISTRADA);
      assertThat(solicitud.analista()).isEmpty();
      assertThat(solicitud.eventos()).hasSize(1).first().isInstanceOf(SolicitudRegistrada.class);
    }

    @Test
    @DisplayName("debeRegistrarElCambioDeEstadoInicialSinEstadoOrigen")
    void debeRegistrarElCambioDeEstadoInicialSinEstadoOrigen() {
      Solicitud solicitud = registrada();

      assertThat(solicitud.historial()).hasSize(1);
      CambioEstado inicial = solicitud.historial().getFirst();
      assertThat(inicial.origen()).isEmpty();
      assertThat(inicial.destino()).isEqualTo(EstadoSolicitud.REGISTRADA);
      assertThat(inicial.actor()).isEqualTo(SOLICITANTE);
    }

    @Test
    @DisplayName("debeRechazarQueUnAnalistaRegistreUnaSolicitud")
    void debeRechazarQueUnAnalistaRegistreUnaSolicitud() {
      assertThatThrownBy(
              () ->
                  Solicitud.registrar(
                      new SolicitudId(UUID.randomUUID()),
                      CODIGO,
                      "Asunto",
                      "Descripcion",
                      CATEGORIA,
                      Prioridad.ALTA,
                      ANALISTA,
                      AHORA))
          .isInstanceOf(AccionNoPermitidaException.class);
    }

    @Test
    @DisplayName("debeRechazarAsuntoVacio")
    void debeRechazarAsuntoVacio() {
      assertThatThrownBy(
              () ->
                  Solicitud.registrar(
                      new SolicitudId(UUID.randomUUID()),
                      CODIGO,
                      "   ",
                      "Descripcion",
                      CATEGORIA,
                      Prioridad.BAJA,
                      SOLICITANTE,
                      AHORA))
          .isInstanceOf(ValidacionDominioException.class);
    }

    @Test
    @DisplayName("debeRechazarDescripcionVacia")
    void debeRechazarDescripcionVacia() {
      assertThatThrownBy(
              () ->
                  Solicitud.registrar(
                      new SolicitudId(UUID.randomUUID()),
                      CODIGO,
                      "Asunto",
                      "",
                      CATEGORIA,
                      Prioridad.BAJA,
                      SOLICITANTE,
                      AHORA))
          .isInstanceOf(ValidacionDominioException.class);
    }
  }

  // ---------------------------------------------------------------------------
  //  Matriz de transiciones - el corazon de la maquina de estados
  // ---------------------------------------------------------------------------

  @Nested
  @DisplayName("matriz de transiciones")
  class MatrizDeTransiciones {

    @Test
    @DisplayName("debePermitirElRecorridoCompletoRegistradaEnAtencionResueltaCerrada")
    void debePermitirElRecorridoCompletoRegistradaEnAtencionResueltaCerrada() {
      Solicitud solicitud = registrada();

      solicitud.tomar(ANALISTA, AHORA);
      assertThat(solicitud.estado()).isEqualTo(EstadoSolicitud.EN_ATENCION);

      solicitud.resolver(ANALISTA, AHORA);
      assertThat(solicitud.estado()).isEqualTo(EstadoSolicitud.RESUELTA);

      solicitud.cerrar(SUPERVISOR, AHORA);
      assertThat(solicitud.estado()).isEqualTo(EstadoSolicitud.CERRADA);
    }

    @Test
    @DisplayName("debePermitirDevolverDeResueltaAEnAtencion")
    void debePermitirDevolverDeResueltaAEnAtencion() {
      Solicitud solicitud = registrada();
      solicitud.tomar(ANALISTA, AHORA);
      solicitud.resolver(ANALISTA, AHORA);

      solicitud.devolver(SUPERVISOR, "Falta evidencia", AHORA);

      assertThat(solicitud.estado()).isEqualTo(EstadoSolicitud.EN_ATENCION);
    }

    /** Escenario A4 del reto. */
    @Test
    @DisplayName("debeRechazarTransicionDeResueltaARegistrada")
    void debeRechazarTransicionDeResueltaARegistrada() {
      Solicitud solicitud = registrada();
      solicitud.tomar(ANALISTA, AHORA);
      solicitud.resolver(ANALISTA, AHORA);

      // No existe accion que regrese a REGISTRADA: tomar sobre RESUELTA es la
      // forma en que ese salto se intentaria, y debe rechazarse.
      assertThatThrownBy(() -> solicitud.tomar(ANALISTA, AHORA))
          .isInstanceOf(TransicionInvalidaException.class)
          .hasMessageContaining("RESUELTA");
    }

    @Test
    @DisplayName("debeRechazarResolverUnaSolicitudRecienRegistrada")
    void debeRechazarResolverUnaSolicitudRecienRegistrada() {
      Solicitud solicitud = registrada();

      assertThatThrownBy(() -> solicitud.resolver(ANALISTA, AHORA))
          .isInstanceOf(TransicionInvalidaException.class);
    }

    @Test
    @DisplayName("debeRechazarCerrarUnaSolicitudEnAtencion")
    void debeRechazarCerrarUnaSolicitudEnAtencion() {
      Solicitud solicitud = registrada();
      solicitud.tomar(ANALISTA, AHORA);

      assertThatThrownBy(() -> solicitud.cerrar(SUPERVISOR, AHORA))
          .isInstanceOf(TransicionInvalidaException.class);
    }

    @Test
    @DisplayName("debeRechazarCualquierTransicionSobreUnaSolicitudCerrada")
    void debeRechazarCualquierTransicionSobreUnaSolicitudCerrada() {
      Solicitud solicitud = registrada();
      solicitud.tomar(ANALISTA, AHORA);
      solicitud.resolver(ANALISTA, AHORA);
      solicitud.cerrar(SUPERVISOR, AHORA);

      assertThatThrownBy(() -> solicitud.tomar(ANALISTA, AHORA))
          .isInstanceOf(TransicionInvalidaException.class);
      assertThatThrownBy(() -> solicitud.resolver(ANALISTA, AHORA))
          .isInstanceOf(TransicionInvalidaException.class);
      assertThatThrownBy(() -> solicitud.devolver(SUPERVISOR, "motivo", AHORA))
          .isInstanceOf(TransicionInvalidaException.class);
      assertThatThrownBy(() -> solicitud.cerrar(SUPERVISOR, AHORA))
          .isInstanceOf(TransicionInvalidaException.class);
    }

    @Test
    @DisplayName("debeRechazarTomarDosVecesLaMismaSolicitud")
    void debeRechazarTomarDosVecesLaMismaSolicitud() {
      Solicitud solicitud = registrada();
      solicitud.tomar(ANALISTA, AHORA);

      assertThatThrownBy(() -> solicitud.tomar(OTRO_ANALISTA, AHORA))
          .isInstanceOf(TransicionInvalidaException.class);
    }
  }

  // ---------------------------------------------------------------------------
  //  Autorizacion en el agregado - la tercera capa de defensa
  // ---------------------------------------------------------------------------

  @Nested
  @DisplayName("autorizacion por rol")
  class AutorizacionPorRol {

    @Test
    @DisplayName("debeRechazarQueUnSolicitanteTomeUnaSolicitud")
    void debeRechazarQueUnSolicitanteTomeUnaSolicitud() {
      Solicitud solicitud = registrada();

      assertThatThrownBy(() -> solicitud.tomar(SOLICITANTE, AHORA))
          .isInstanceOf(AccionNoPermitidaException.class);
    }

    /** Escenario A3: un rol insuficiente no debe producir ningun efecto. */
    @Test
    @DisplayName("debeRechazarQueUnSolicitanteCierreYNoDebeAlterarElAgregado")
    void debeRechazarQueUnSolicitanteCierreYNoDebeAlterarElAgregado() {
      Solicitud solicitud = registrada();
      solicitud.tomar(ANALISTA, AHORA);
      solicitud.resolver(ANALISTA, AHORA);
      solicitud.drenarEventos();
      int historialAntes = solicitud.historial().size();

      assertThatThrownBy(() -> solicitud.cerrar(SOLICITANTE, AHORA))
          .isInstanceOf(AccionNoPermitidaException.class);

      assertThat(solicitud.estado()).isEqualTo(EstadoSolicitud.RESUELTA);
      assertThat(solicitud.historial()).hasSize(historialAntes);
      assertThat(solicitud.eventos()).isEmpty();
    }

    @Test
    @DisplayName("debeRechazarQueUnAnalistaCierre")
    void debeRechazarQueUnAnalistaCierre() {
      Solicitud solicitud = registrada();
      solicitud.tomar(ANALISTA, AHORA);
      solicitud.resolver(ANALISTA, AHORA);

      assertThatThrownBy(() -> solicitud.cerrar(ANALISTA, AHORA))
          .isInstanceOf(AccionNoPermitidaException.class);
    }

    @Test
    @DisplayName("debeRechazarQueUnAnalistaDevuelva")
    void debeRechazarQueUnAnalistaDevuelva() {
      Solicitud solicitud = registrada();
      solicitud.tomar(ANALISTA, AHORA);
      solicitud.resolver(ANALISTA, AHORA);

      assertThatThrownBy(() -> solicitud.devolver(ANALISTA, "motivo", AHORA))
          .isInstanceOf(AccionNoPermitidaException.class);
    }

    @Test
    @DisplayName("debeRechazarQueUnAnalistaDistintoDelAsignadoResuelva")
    void debeRechazarQueUnAnalistaDistintoDelAsignadoResuelva() {
      Solicitud solicitud = registrada();
      solicitud.tomar(ANALISTA, AHORA);

      assertThatThrownBy(() -> solicitud.resolver(OTRO_ANALISTA, AHORA))
          .isInstanceOf(AccionNoPermitidaException.class);
    }

    @Test
    @DisplayName("laValidacionDeRolDebePrecederALaDeTransicion")
    void laValidacionDeRolDebePrecederALaDeTransicion() {
      // Una solicitud REGISTRADA no puede cerrarse (transicion invalida) y ademas
      // el solicitante no tiene el rol. Debe ganar el fallo de autorizacion: es
      // lo que evita filtrar si la transicion habria sido posible.
      Solicitud solicitud = registrada();

      assertThatThrownBy(() -> solicitud.cerrar(SOLICITANTE, AHORA))
          .isInstanceOf(AccionNoPermitidaException.class);
    }
  }

  // ---------------------------------------------------------------------------
  //  Eventos de dominio
  // ---------------------------------------------------------------------------

  @Nested
  @DisplayName("eventos de dominio")
  class EventosDeDominio {

    @Test
    @DisplayName("debeAcumularUnEventoPorCadaTransicionYDrenarlosUnaSolaVez")
    void debeAcumularUnEventoPorCadaTransicionYDrenarlosUnaSolaVez() {
      Solicitud solicitud = registrada();
      solicitud.tomar(ANALISTA, AHORA);
      solicitud.resolver(ANALISTA, AHORA);
      solicitud.devolver(SUPERVISOR, "Falta evidencia", AHORA);
      solicitud.resolver(ANALISTA, AHORA);
      solicitud.cerrar(SUPERVISOR, AHORA);

      List<EventoDominio> eventos = solicitud.drenarEventos();

      assertThat(eventos)
          .hasSize(6)
          .satisfiesExactly(
              e -> assertThat(e).isInstanceOf(SolicitudRegistrada.class),
              e -> assertThat(e).isInstanceOf(SolicitudTomada.class),
              e -> assertThat(e).isInstanceOf(SolicitudResuelta.class),
              e -> assertThat(e).isInstanceOf(SolicitudDevuelta.class),
              e -> assertThat(e).isInstanceOf(SolicitudResuelta.class),
              e -> assertThat(e).isInstanceOf(SolicitudCerrada.class));

      // Drenar vacia la lista: el caso de uso los publica una vez, no dos.
      assertThat(solicitud.drenarEventos()).isEmpty();
    }

    @Test
    @DisplayName("debeExponerLosEventosComoColeccionInmutable")
    void debeExponerLosEventosComoColeccionInmutable() {
      Solicitud solicitud = registrada();

      assertThatThrownBy(() -> solicitud.eventos().clear())
          .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("debeExponerElHistorialComoColeccionInmutable")
    void debeExponerElHistorialComoColeccionInmutable() {
      Solicitud solicitud = registrada();

      assertThatThrownBy(() -> solicitud.historial().clear())
          .isInstanceOf(UnsupportedOperationException.class);
    }
  }

  // ---------------------------------------------------------------------------
  //  Observaciones
  // ---------------------------------------------------------------------------

  @Nested
  @DisplayName("observaciones")
  class Observaciones {

    @Test
    @DisplayName("debePermitirQueElAnalistaAsignadoAgregueUnaObservacion")
    void debePermitirQueElAnalistaAsignadoAgregueUnaObservacion() {
      Solicitud solicitud = registrada();
      solicitud.tomar(ANALISTA, AHORA);

      assertThatNoException()
          .isThrownBy(() -> solicitud.agregarObservacion(UUID.randomUUID(), "Avance", ANALISTA, AHORA));

      assertThat(solicitud.observaciones()).hasSize(1);
    }

    @Test
    @DisplayName("debeRechazarQueUnSolicitanteAgregueObservaciones")
    void debeRechazarQueUnSolicitanteAgregueObservaciones() {
      Solicitud solicitud = registrada();
      solicitud.tomar(ANALISTA, AHORA);

      assertThatThrownBy(
              () -> solicitud.agregarObservacion(UUID.randomUUID(), "Texto", SOLICITANTE, AHORA))
          .isInstanceOf(AccionNoPermitidaException.class);
    }

    @Test
    @DisplayName("debeRechazarObservacionVacia")
    void debeRechazarObservacionVacia() {
      Solicitud solicitud = registrada();
      solicitud.tomar(ANALISTA, AHORA);

      assertThatThrownBy(() -> solicitud.agregarObservacion(UUID.randomUUID(), "  ", ANALISTA, AHORA))
          .isInstanceOf(ValidacionDominioException.class);
    }
  }

  // ---------------------------------------------------------------------------
  //  Rehidratacion desde persistencia
  // ---------------------------------------------------------------------------

  @Nested
  @DisplayName("al rehidratarse desde persistencia")
  class AlRehidratarse {

    @Test
    @DisplayName("noDebeEmitirEventosPorqueNoOcurrioNadaNuevo")
    void noDebeEmitirEventosPorqueNoOcurrioNadaNuevo() {
      Solicitud solicitud =
          Solicitud.rehidratar(
              new SolicitudId(UUID.randomUUID()),
              CODIGO,
              "Asunto",
              "Descripcion",
              CATEGORIA,
              Prioridad.ALTA,
              EstadoSolicitud.EN_ATENCION,
              "u-solicitante",
              "u-analista",
              AHORA,
              AHORA,
              List.of(),
              List.of());

      assertThat(solicitud.eventos()).isEmpty();
      assertThat(solicitud.estado()).isEqualTo(EstadoSolicitud.EN_ATENCION);
    }

    @Test
    @DisplayName("debeConservarElEstadoYPermitirContinuarElFlujo")
    void debeConservarElEstadoYPermitirContinuarElFlujo() {
      Solicitud solicitud =
          Solicitud.rehidratar(
              new SolicitudId(UUID.randomUUID()),
              CODIGO,
              "Asunto",
              "Descripcion",
              CATEGORIA,
              Prioridad.ALTA,
              EstadoSolicitud.EN_ATENCION,
              "u-solicitante",
              ANALISTA.id(),
              AHORA,
              AHORA,
              List.of(),
              List.of());

      solicitud.resolver(ANALISTA, AHORA);

      assertThat(solicitud.estado()).isEqualTo(EstadoSolicitud.RESUELTA);
      assertThat(solicitud.eventos()).hasSize(1).first().isInstanceOf(SolicitudResuelta.class);
    }
  }
}
