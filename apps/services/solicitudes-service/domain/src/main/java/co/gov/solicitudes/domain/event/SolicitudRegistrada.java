package co.gov.solicitudes.domain.event;

import co.gov.solicitudes.domain.model.Prioridad;
import java.time.Instant;
import java.util.UUID;

/** Se creo una solicitud nueva en estado REGISTRADA. */
public record SolicitudRegistrada(
    UUID agregadoId,
    String codigo,
    UUID categoriaId,
    Prioridad prioridad,
    String solicitanteId,
    Instant ocurridoEn)
    implements EventoDominio {

  @Override
  public String tipo() {
    return "SolicitudRegistrada";
  }

  @Override
  public String routingKey() {
    return "solicitud.registrada";
  }
}
