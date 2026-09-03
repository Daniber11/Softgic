package co.gov.solicitudes.domain.event;

import java.time.Instant;
import java.util.UUID;

/** Un analista tomo la solicitud y paso a EN_ATENCION. */
public record SolicitudTomada(
    UUID agregadoId, String codigo, UUID categoriaId, String analistaId, Instant ocurridoEn)
    implements EventoDominio {

  @Override
  public String tipo() {
    return "SolicitudTomada";
  }

  @Override
  public String routingKey() {
    return "solicitud.tomada";
  }
}
