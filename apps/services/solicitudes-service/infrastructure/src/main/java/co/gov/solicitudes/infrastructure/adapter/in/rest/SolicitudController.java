package co.gov.solicitudes.infrastructure.adapter.in.rest;

import co.gov.solicitudes.application.command.AgregarObservacionCommand;
import co.gov.solicitudes.application.command.RegistrarSolicitudCommand;
import co.gov.solicitudes.application.command.TomarSolicitudCommand;
import co.gov.solicitudes.application.command.TransicionarSolicitudCommand;
import co.gov.solicitudes.application.port.in.AgregarObservacionUseCase;
import co.gov.solicitudes.application.port.in.ConsultarDetalleQuery;
import co.gov.solicitudes.application.port.in.ConsultarSolicitudesQuery;
import co.gov.solicitudes.application.port.in.RegistrarSolicitudUseCase;
import co.gov.solicitudes.application.port.in.TomarSolicitudUseCase;
import co.gov.solicitudes.application.port.in.TransicionarSolicitudUseCase;
import co.gov.solicitudes.application.command.FiltroSolicitudes;
import co.gov.solicitudes.domain.model.Accion;
import co.gov.solicitudes.domain.model.Actor;
import co.gov.solicitudes.domain.model.CategoriaId;
import co.gov.solicitudes.domain.model.EstadoSolicitud;
import co.gov.solicitudes.domain.model.Prioridad;
import co.gov.solicitudes.domain.model.Solicitud;
import co.gov.solicitudes.domain.model.SolicitudId;
import co.gov.solicitudes.infrastructure.adapter.in.rest.dto.CrearSolicitudRequest;
import co.gov.solicitudes.infrastructure.adapter.in.rest.dto.ObservacionRequest;
import co.gov.solicitudes.infrastructure.adapter.in.rest.dto.PaginaResponse;
import co.gov.solicitudes.infrastructure.adapter.in.rest.dto.SolicitudResponse;
import co.gov.solicitudes.infrastructure.adapter.in.rest.dto.TransicionRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Adaptador de entrada HTTP.
 *
 * <p>Su unica responsabilidad es traducir: HTTP a comando, resultado a DTO. No hay una sola regla
 * de negocio en esta clase, y esa ausencia es el criterio para saber si el controlador esta bien
 * escrito.
 *
 * <p>Las anotaciones {@code @PreAuthorize} son la segunda capa de defensa. Duplican lo que ya hace
 * el filtro de seguridad a proposito: el filtro protege por ruta, la anotacion por operacion, de
 * modo que un cambio en el mapeo de rutas no deja una operacion desprotegida por accidente.
 */
@RestController
@RequestMapping("/api/v1/solicitudes")
@Tag(name = "Solicitudes", description = "Registro, consulta y transiciones de solicitudes")
public class SolicitudController {

  private final RegistrarSolicitudUseCase registrar;
  private final TomarSolicitudUseCase tomar;
  private final TransicionarSolicitudUseCase transicionar;
  private final AgregarObservacionUseCase agregarObservacion;
  private final ConsultarSolicitudesQuery consultarSolicitudes;
  private final ConsultarDetalleQuery consultarDetalle;
  private final ActorExtractor actorExtractor;

  public SolicitudController(
      RegistrarSolicitudUseCase registrar,
      TomarSolicitudUseCase tomar,
      TransicionarSolicitudUseCase transicionar,
      AgregarObservacionUseCase agregarObservacion,
      ConsultarSolicitudesQuery consultarSolicitudes,
      ConsultarDetalleQuery consultarDetalle,
      ActorExtractor actorExtractor) {
    this.registrar = registrar;
    this.tomar = tomar;
    this.transicionar = transicionar;
    this.agregarObservacion = agregarObservacion;
    this.consultarSolicitudes = consultarSolicitudes;
    this.consultarDetalle = consultarDetalle;
    this.actorExtractor = actorExtractor;
  }

  @PostMapping
  @PreAuthorize("hasRole('SOLICITANTE')")
  @Operation(summary = "Registra una solicitud nueva (escenario A1)")
  public ResponseEntity<SolicitudResponse> crear(
      @Valid @RequestBody CrearSolicitudRequest peticion, JwtAuthenticationToken autenticacion) {

    Actor solicitante = actorExtractor.extraer(autenticacion);

    Solicitud creada =
        registrar.registrar(
            new RegistrarSolicitudCommand(
                peticion.asunto(),
                peticion.descripcion(),
                new CategoriaId(peticion.categoriaId()),
                Prioridad.valueOf(peticion.prioridad()),
                solicitante));

    return ResponseEntity.created(URI.create("/api/v1/solicitudes/" + creada.id().valor()))
        .body(SolicitudResponse.detalle(creada));
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('SOLICITANTE','ANALISTA','SUPERVISOR')")
  @Operation(summary = "Bandeja paginada. El SOLICITANTE solo ve las propias")
  public PaginaResponse<SolicitudResponse> listar(
      @RequestParam(required = false) String estado,
      @RequestParam(required = false) UUID categoriaId,
      @RequestParam(required = false) String prioridad,
      @RequestParam(required = false) Instant desde,
      @RequestParam(required = false) Instant hasta,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      JwtAuthenticationToken autenticacion) {

    Actor consultante = actorExtractor.extraer(autenticacion);

    // soloDelSolicitante va en null a proposito: lo impone el caso de uso segun el
    // rol del token, no el cliente. Aceptarlo aqui permitiria eludirlo omitiendolo.
    FiltroSolicitudes filtro =
        new FiltroSolicitudes(
            estado == null ? null : EstadoSolicitud.valueOf(estado),
            categoriaId,
            prioridad == null ? null : Prioridad.valueOf(prioridad),
            desde,
            hasta,
            null);

    return PaginaResponse.desde(
        consultarSolicitudes.consultar(filtro, page, size, consultante),
        SolicitudResponse::resumen);
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAnyRole('SOLICITANTE','ANALISTA','SUPERVISOR')")
  @Operation(summary = "Detalle con historial y observaciones")
  public SolicitudResponse detalle(
      @PathVariable UUID id, JwtAuthenticationToken autenticacion) {

    Actor consultante = actorExtractor.extraer(autenticacion);
    return SolicitudResponse.detalle(consultarDetalle.consultar(new SolicitudId(id), consultante));
  }

  /**
   * Tomar una solicitud se modela como crear una asignacion.
   *
   * <p>Es un recurso, no una accion, y por eso responde 201. Ademas es idempotente por naturaleza:
   * si el mismo analista repite, obtiene el estado actual; si es otro, 409 (escenario A2).
   */
  @PostMapping("/{id}/asignaciones")
  @PreAuthorize("hasRole('ANALISTA')")
  @Operation(summary = "Un analista toma la solicitud (escenario A2)")
  public ResponseEntity<SolicitudResponse> asignar(
      @PathVariable UUID id, JwtAuthenticationToken autenticacion) {

    Actor analista = actorExtractor.extraer(autenticacion);
    Solicitud actualizada = tomar.tomar(new TomarSolicitudCommand(new SolicitudId(id), analista));

    return ResponseEntity.created(URI.create("/api/v1/solicitudes/" + id + "/asignaciones"))
        .body(SolicitudResponse.detalle(actualizada));
  }

  @PostMapping("/{id}/transiciones")
  @PreAuthorize("hasAnyRole('ANALISTA','SUPERVISOR')")
  @Operation(summary = "Resolver, devolver o cerrar (escenario A4 si la transicion no aplica)")
  public SolicitudResponse transicionar(
      @PathVariable UUID id,
      @Valid @RequestBody TransicionRequest peticion,
      JwtAuthenticationToken autenticacion) {

    Actor actor = actorExtractor.extraer(autenticacion);
    Solicitud actualizada =
        transicionar.transicionar(
            new TransicionarSolicitudCommand(
                new SolicitudId(id), Accion.valueOf(peticion.accion()), peticion.motivo(), actor));

    return SolicitudResponse.detalle(actualizada);
  }

  @PostMapping("/{id}/observaciones")
  @PreAuthorize("hasAnyRole('ANALISTA','SUPERVISOR')")
  @Operation(summary = "Agrega una observacion al expediente")
  public ResponseEntity<SolicitudResponse> observar(
      @PathVariable UUID id,
      @Valid @RequestBody ObservacionRequest peticion,
      JwtAuthenticationToken autenticacion) {

    Actor autor = actorExtractor.extraer(autenticacion);
    Solicitud actualizada =
        agregarObservacion.agregar(
            new AgregarObservacionCommand(new SolicitudId(id), peticion.texto(), autor));

    return ResponseEntity.created(URI.create("/api/v1/solicitudes/" + id + "/observaciones"))
        .body(SolicitudResponse.detalle(actualizada));
  }
}
