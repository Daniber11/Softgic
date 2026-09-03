package co.gov.solicitudes.application.port.in;

import co.gov.solicitudes.domain.model.Actor;
import co.gov.solicitudes.domain.model.Solicitud;
import co.gov.solicitudes.domain.model.SolicitudId;

public interface ConsultarDetalleQuery {
  Solicitud consultar(SolicitudId id, Actor consultante);
}
