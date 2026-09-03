package co.gov.solicitudes.application.port.in;

import co.gov.solicitudes.application.command.AgregarObservacionCommand;
import co.gov.solicitudes.domain.model.Solicitud;

public interface AgregarObservacionUseCase {
  Solicitud agregar(AgregarObservacionCommand comando);
}
