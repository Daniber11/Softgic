package co.gov.solicitudes.application.port.in;

import co.gov.solicitudes.application.command.TransicionarSolicitudCommand;
import co.gov.solicitudes.domain.model.Solicitud;

public interface TransicionarSolicitudUseCase {
  Solicitud transicionar(TransicionarSolicitudCommand comando);
}
