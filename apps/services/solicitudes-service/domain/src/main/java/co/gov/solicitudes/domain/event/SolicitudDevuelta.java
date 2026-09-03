package co.gov.solicitudes.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Un supervisor devolvio a EN_ATENCION una solicitud ya resuelta.
 *
 * <p>Excede el minimo exigido por el reto. Se incluye porque sin el, el modelo analitico no puede
 * distinguir un reproceso de una resolucion limpia, y el tiempo medio de atencion quedaria
 * distorsionado. Documentado como extension propia.
 */
public record SolicitudDevuelta(
    UUID agregadoId,
    String codigo,
    UUID categoriaId,
    String supervisorId,
    String motivo,
    Instant ocurridoEn)
    implements EventoDominio {

  @Override
  public String tipo() {
    return "SolicitudDevuelta";
  }

  @Override
  public String routingKey() {
    return "solicitud.devuelta";
  }
}
