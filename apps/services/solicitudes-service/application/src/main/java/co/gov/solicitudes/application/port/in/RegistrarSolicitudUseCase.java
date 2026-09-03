package co.gov.solicitudes.application.port.in;

import co.gov.solicitudes.application.command.RegistrarSolicitudCommand;
import co.gov.solicitudes.domain.model.Solicitud;

/** Un puerto de entrada por comando de negocio, con un unico metodo (ISP). */
public interface RegistrarSolicitudUseCase {
  Solicitud registrar(RegistrarSolicitudCommand comando);
}
