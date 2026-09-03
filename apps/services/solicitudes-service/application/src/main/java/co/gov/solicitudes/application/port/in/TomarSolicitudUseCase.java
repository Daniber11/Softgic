package co.gov.solicitudes.application.port.in;

import co.gov.solicitudes.application.command.TomarSolicitudCommand;
import co.gov.solicitudes.domain.model.Solicitud;

public interface TomarSolicitudUseCase {
  Solicitud tomar(TomarSolicitudCommand comando);
}
