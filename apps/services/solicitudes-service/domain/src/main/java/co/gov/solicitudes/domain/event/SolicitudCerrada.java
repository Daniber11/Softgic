package co.gov.solicitudes.domain.event;

import java.time.Instant;
import java.util.UUID;

/** Un supervisor cerro la solicitud. Estado final del flujo. */
public record SolicitudCerrada(
    UUID agregadoId, String codigo, UUID categoriaId, String supervisorId, Instant ocurridoEn)
    implements EventoDominio {

  @Override
  public String tipo() {
    return "SolicitudCerrada";
  }

  @Override
  public String routingKey() {
    return "solicitud.cerrada";
  }
}
