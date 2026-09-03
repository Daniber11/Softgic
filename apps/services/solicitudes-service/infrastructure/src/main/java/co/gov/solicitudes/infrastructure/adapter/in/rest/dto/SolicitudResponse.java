package co.gov.solicitudes.infrastructure.adapter.in.rest.dto;

import co.gov.solicitudes.domain.model.CambioEstado;
import co.gov.solicitudes.domain.model.EstadoSolicitud;
import co.gov.solicitudes.domain.model.Observacion;
import co.gov.solicitudes.domain.model.Solicitud;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Representacion de una solicitud para el cliente.
 *
 * <p>Es un tercer tipo, distinto del agregado y de la entidad JPA. Devolver el agregado
 * expondria metodos de negocio como parte del contrato HTTP; devolver la entidad JPA acoplaria el
 * API al esquema de la base y arrastraria colecciones perezosas hasta el serializador.
 *
 * <p>La version resumida omite historial y observaciones: la bandeja lista decenas de filas y no
 * necesita el expediente completo de cada una.
 */
public record SolicitudResponse(
    UUID id,
    String codigo,
    String asunto,
    String descripcion,
    UUID categoriaId,
    String prioridad,
    String estado,
    String solicitanteId,
    String analistaId,
    Instant creadaEn,
    Instant actualizadaEn,
    List<CambioEstadoResponse> historial,
    List<ObservacionResponse> observaciones) {

  /** Version completa, para el detalle. */
  public static SolicitudResponse detalle(Solicitud solicitud) {
    return new SolicitudResponse(
        solicitud.id().valor(),
        solicitud.codigo().valor(),
        solicitud.asunto(),
        solicitud.descripcion(),
        solicitud.categoriaId().valor(),
        solicitud.prioridad().name(),
        solicitud.estado().name(),
        solicitud.solicitanteId(),
        solicitud.analista().orElse(null),
        solicitud.creadaEn(),
        solicitud.actualizadaEn(),
        solicitud.historial().stream().map(CambioEstadoResponse::desde).toList(),
        solicitud.observaciones().stream().map(ObservacionResponse::desde).toList());
  }

  /** Version resumida, para la bandeja. */
  public static SolicitudResponse resumen(Solicitud solicitud) {
    return new SolicitudResponse(
        solicitud.id().valor(),
        solicitud.codigo().valor(),
        solicitud.asunto(),
        solicitud.descripcion(),
        solicitud.categoriaId().valor(),
        solicitud.prioridad().name(),
        solicitud.estado().name(),
        solicitud.solicitanteId(),
        solicitud.analista().orElse(null),
        solicitud.creadaEn(),
        solicitud.actualizadaEn(),
        null,
        null);
  }

  /** Entrada del historial, tal como la pinta la linea de tiempo del detalle. */
  public record CambioEstadoResponse(
      UUID id,
      String estadoOrigen,
      String estadoDestino,
      String actorId,
      String actorRol,
      String motivo,
      Instant ocurridoEn) {

    static CambioEstadoResponse desde(CambioEstado cambio) {
      return new CambioEstadoResponse(
          cambio.id(),
          cambio.origen().map(EstadoSolicitud::name).orElse(null),
          cambio.destino().name(),
          cambio.actor().id(),
          cambio.actor().rol().name(),
          cambio.motivo().orElse(null),
          cambio.ocurridoEn());
    }
  }

  public record ObservacionResponse(
      UUID id, String texto, String actorId, String actorRol, Instant ocurridoEn) {

    static ObservacionResponse desde(Observacion observacion) {
      return new ObservacionResponse(
          observacion.id(),
          observacion.texto(),
          observacion.autor().id(),
          observacion.autor().rol().name(),
          observacion.ocurridoEn());
    }
  }
}
