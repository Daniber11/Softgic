package co.gov.solicitudes.domain.event;

import java.time.Instant;
import java.util.UUID;

/** El analista asignado dio por resuelta la solicitud. */
public record SolicitudResuelta(
    UUID agregadoId, String codigo, UUID categoriaId, String analistaId, Instant ocurridoEn)
    implements EventoDominio {

  @Override
  public String tipo() {
    return "SolicitudResuelta";
  }

  @Override
  public String routingKey() {
    return "solicitud.resuelta";
  }
}
