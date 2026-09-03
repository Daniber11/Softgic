package co.gov.solicitudes.application.port.in;

import co.gov.solicitudes.application.command.FiltroSolicitudes;
import co.gov.solicitudes.application.result.Pagina;
import co.gov.solicitudes.domain.model.Actor;
import co.gov.solicitudes.domain.model.Solicitud;

/** Consulta de la bandeja. El filtrado por pertenencia lo impone el caso de uso, no el cliente. */
public interface ConsultarSolicitudesQuery {
  Pagina<Solicitud> consultar(
      FiltroSolicitudes filtro, int pagina, int tamanio, Actor consultante);
}
