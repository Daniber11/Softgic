package co.gov.solicitudes.domain.model;

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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Raiz del agregado.
 *
 * <p>Concentra las invariantes del expediente: quien puede hacer que, desde que estado y con que
 * consecuencias. Ninguna de esas reglas vive fuera de aqui. En particular no viven en una clausula
 * WHERE de SQL ni en un componente de React: alli solo hay usabilidad y optimizacion.
 *
 * <p>No es inmutable, y es deliberado. Un agregado modela un ciclo de vida con identidad estable;
 * forzar inmutabilidad obligaria a reconstruirlo entero en cada transicion sin ganar nada. Lo que
 * si es inmutable es todo lo que expone: las colecciones salen como vistas de solo lectura.
 */
public final class Solicitud {

  private static final int LONGITUD_MAXIMA_ASUNTO = 200;
  private static final int LONGITUD_MAXIMA_DESCRIPCION = 2000;
  private static final int LONGITUD_MAXIMA_MOTIVO = 500;

  private final SolicitudId id;
  private final CodigoSolicitud codigo;
  private final String asunto;
  private final String descripcion;
  private final CategoriaId categoriaId;
  private final Prioridad prioridad;
  private final String solicitanteId;

  private EstadoSolicitud estado;
  private String analistaId;
  private final Instant creadaEn;
  private Instant actualizadaEn;

  private final List<CambioEstado> historial;
  private final List<Observacion> observaciones;
  private final List<EventoDominio> eventos = new ArrayList<>();

  private Solicitud(
      SolicitudId id,
      CodigoSolicitud codigo,
      String asunto,
      String descripcion,
      CategoriaId categoriaId,
      Prioridad prioridad,
      EstadoSolicitud estado,
      String solicitanteId,
      String analistaId,
      Instant creadaEn,
      Instant actualizadaEn,
      List<CambioEstado> historial,
      List<Observacion> observaciones) {
    this.id = id;
    this.codigo = codigo;
    this.asunto = asunto;
    this.descripcion = descripcion;
    this.categoriaId = categoriaId;
    this.prioridad = prioridad;
    this.estado = estado;
    this.solicitanteId = solicitanteId;
    this.analistaId = analistaId;
    this.creadaEn = creadaEn;
    this.actualizadaEn = actualizadaEn;
    this.historial = new ArrayList<>(historial);
    this.observaciones = new ArrayList<>(observaciones);
  }

  // ---------------------------------------------------------------------------
  //  Factorias
  // ---------------------------------------------------------------------------

  /**
   * Crea una solicitud nueva. Es un hecho de negocio: valida el rol, registra el historial inicial
   * y emite el evento correspondiente.
   */
  public static Solicitud registrar(
      SolicitudId id,
      CodigoSolicitud codigo,
      String asunto,
      String descripcion,
      CategoriaId categoriaId,
      Prioridad prioridad,
      Actor solicitante,
      Instant ahora) {

    Objects.requireNonNull(solicitante, "El solicitante es obligatorio.");
    Objects.requireNonNull(ahora, "La marca de tiempo es obligatoria.");

    if (!solicitante.tieneRol(Rol.SOLICITANTE)) {
      throw new AccionNoPermitidaException(
          "Solo un usuario con rol SOLICITANTE puede registrar una solicitud.");
    }

    String asuntoValidado = exigirTextoNoVacio(asunto, "asunto", LONGITUD_MAXIMA_ASUNTO);
    String descripcionValidada =
        exigirTextoNoVacio(descripcion, "descripcion", LONGITUD_MAXIMA_DESCRIPCION);

    Solicitud solicitud =
        new Solicitud(
            Objects.requireNonNull(id, "El identificador es obligatorio."),
            Objects.requireNonNull(codigo, "El codigo es obligatorio."),
            asuntoValidado,
            descripcionValidada,
            Objects.requireNonNull(categoriaId, "La categoria es obligatoria."),
            Objects.requireNonNull(prioridad, "La prioridad es obligatoria."),
            EstadoSolicitud.REGISTRADA,
            solicitante.id(),
            null,
            ahora,
            ahora,
            List.of(),
            List.of());

    // El registro tambien es una entrada del historial, sin estado de origen.
    solicitud.historial.add(
        new CambioEstado(
            UUID.randomUUID(), null, EstadoSolicitud.REGISTRADA, solicitante, null, ahora));

    solicitud.eventos.add(
        new SolicitudRegistrada(
            id.valor(),
            codigo.valor(),
            categoriaId.valor(),
            prioridad,
            solicitante.id(),
            ahora));

    return solicitud;
  }

  /**
   * Reconstruye el agregado desde persistencia.
   *
   * <p>No valida reglas de negocio ni emite eventos: lo que ya esta guardado ocurrio, y volver a
   * anunciarlo duplicaria hechos. Separarla de {@link #registrar} es justamente lo que evita ese
   * error.
   */
  public static Solicitud rehidratar(
      SolicitudId id,
      CodigoSolicitud codigo,
      String asunto,
      String descripcion,
      CategoriaId categoriaId,
      Prioridad prioridad,
      EstadoSolicitud estado,
      String solicitanteId,
      String analistaId,
      Instant creadaEn,
      Instant actualizadaEn,
      List<CambioEstado> historial,
      List<Observacion> observaciones) {
    return new Solicitud(
        id,
        codigo,
        asunto,
        descripcion,
        categoriaId,
        prioridad,
        estado,
        solicitanteId,
        analistaId,
        creadaEn,
        actualizadaEn,
        historial,
        observaciones);
  }

  // ---------------------------------------------------------------------------
  //  Comportamiento
  // ---------------------------------------------------------------------------

  /** Un analista toma la solicitud y pasa a atenderla. */
  public void tomar(Actor analista, Instant ahora) {
    aplicar(Accion.TOMAR, analista, null, ahora);
    this.analistaId = analista.id();
    eventos.add(
        new SolicitudTomada(
            id.valor(), codigo.valor(), categoriaId.valor(), analista.id(), ahora));
  }

  /** El analista asignado da por resuelta la solicitud. */
  public void resolver(Actor analista, Instant ahora) {
    exigirQueSeaElAnalistaAsignado(analista);
    aplicar(Accion.RESOLVER, analista, null, ahora);
    eventos.add(
        new SolicitudResuelta(
            id.valor(), codigo.valor(), categoriaId.valor(), analista.id(), ahora));
  }

  /** Un supervisor devuelve la solicitud a atencion, con motivo obligatorio. */
  public void devolver(Actor supervisor, String motivo, Instant ahora) {
    String motivoValidado = exigirTextoNoVacio(motivo, "motivo", LONGITUD_MAXIMA_MOTIVO);
    aplicar(Accion.DEVOLVER, supervisor, motivoValidado, ahora);
    eventos.add(
        new SolicitudDevuelta(
            id.valor(),
            codigo.valor(),
            categoriaId.valor(),
            supervisor.id(),
            motivoValidado,
            ahora));
  }

  /** Un supervisor cierra la solicitud. Estado final. */
  public void cerrar(Actor supervisor, Instant ahora) {
    aplicar(Accion.CERRAR, supervisor, null, ahora);
    eventos.add(
        new SolicitudCerrada(
            id.valor(), codigo.valor(), categoriaId.valor(), supervisor.id(), ahora));
  }

  /** Agrega una observacion. No es una transicion: no cambia el estado ni emite evento. */
  public void agregarObservacion(UUID idObservacion, String texto, Actor autor, Instant ahora) {
    Objects.requireNonNull(autor, "El autor de la observacion es obligatorio.");
    if (autor.tieneRol(Rol.SOLICITANTE)) {
      throw new AccionNoPermitidaException(
          "Un SOLICITANTE no puede agregar observaciones a una solicitud.");
    }
    observaciones.add(new Observacion(idObservacion, texto, autor, ahora));
    this.actualizadaEn = ahora;
  }

  /**
   * Nucleo de toda transicion.
   *
   * <p>El orden de las dos validaciones importa y esta probado: primero el rol, despues la
   * transicion. Al reves, un usuario sin permiso podria deducir por el mensaje de error si la
   * transicion habria sido posible, que es una filtracion de informacion pequenia pero gratuita.
   */
  private void aplicar(Accion accion, Actor actor, String motivo, Instant ahora) {
    Objects.requireNonNull(actor, "El actor es obligatorio.");
    Objects.requireNonNull(ahora, "La marca de tiempo es obligatoria.");

    if (!actor.tieneRol(accion.rolRequerido())) {
      throw new AccionNoPermitidaException(
          "La accion %s requiere el rol %s.".formatted(accion, accion.rolRequerido()));
    }

    if (!estado.permite(accion)) {
      throw new TransicionInvalidaException(
          "No se permite la accion %s sobre una solicitud en estado %s."
              .formatted(accion, estado));
    }

    EstadoSolicitud origen = this.estado;
    this.estado = accion.destino();
    this.actualizadaEn = ahora;
    historial.add(
        new CambioEstado(UUID.randomUUID(), origen, accion.destino(), actor, motivo, ahora));
  }

  private void exigirQueSeaElAnalistaAsignado(Actor analista) {
    if (analistaId != null && !analistaId.equals(analista.id())) {
      throw new AccionNoPermitidaException(
          "Solo el analista que tomo la solicitud puede resolverla.");
    }
  }

  private static String exigirTextoNoVacio(String valor, String campo, int longitudMaxima) {
    if (valor == null || valor.isBlank()) {
      throw new ValidacionDominioException("El campo %s es obligatorio.".formatted(campo));
    }
    String recortado = valor.strip();
    if (recortado.length() > longitudMaxima) {
      throw new ValidacionDominioException(
          "El campo %s no puede exceder %d caracteres.".formatted(campo, longitudMaxima));
    }
    return recortado;
  }

  // ---------------------------------------------------------------------------
  //  Eventos
  // ---------------------------------------------------------------------------

  /**
   * Entrega los eventos acumulados y vacia la lista.
   *
   * <p>Vaciar es parte del contrato: el caso de uso los escribe al outbox una sola vez. Si el
   * agregado los conservara, una segunda llamada los publicaria por duplicado.
   */
  public List<EventoDominio> drenarEventos() {
    List<EventoDominio> drenados = List.copyOf(eventos);
    eventos.clear();
    return drenados;
  }

  // ---------------------------------------------------------------------------
  //  Consultas. Todas las colecciones salen como vistas de solo lectura.
  // ---------------------------------------------------------------------------

  public SolicitudId id() {
    return id;
  }

  public CodigoSolicitud codigo() {
    return codigo;
  }

  public String asunto() {
    return asunto;
  }

  public String descripcion() {
    return descripcion;
  }

  public CategoriaId categoriaId() {
    return categoriaId;
  }

  public Prioridad prioridad() {
    return prioridad;
  }

  public EstadoSolicitud estado() {
    return estado;
  }

  public String solicitanteId() {
    return solicitanteId;
  }

  public Optional<String> analista() {
    return Optional.ofNullable(analistaId);
  }

  public Instant creadaEn() {
    return creadaEn;
  }

  public Instant actualizadaEn() {
    return actualizadaEn;
  }

  public List<CambioEstado> historial() {
    return Collections.unmodifiableList(historial);
  }

  public List<Observacion> observaciones() {
    return Collections.unmodifiableList(observaciones);
  }

  public List<EventoDominio> eventos() {
    return Collections.unmodifiableList(eventos);
  }

  /** Un solicitante solo puede ver lo suyo. La regla vive aqui, no en el controlador. */
  public boolean perteneceA(String posibleSolicitanteId) {
    return solicitanteId.equals(posibleSolicitanteId);
  }
}
