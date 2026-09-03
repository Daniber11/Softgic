package co.gov.solicitudes.application.service;

import co.gov.solicitudes.application.command.TomarSolicitudCommand;
import co.gov.solicitudes.application.port.in.TomarSolicitudUseCase;
import co.gov.solicitudes.application.port.out.EventoPublicadorPort;
import co.gov.solicitudes.application.port.out.RelojPort;
import co.gov.solicitudes.application.port.out.SolicitudRepositoryPort;
import co.gov.solicitudes.domain.exception.SolicitudNoEncontradaException;
import co.gov.solicitudes.domain.model.Solicitud;
import java.util.Objects;

/**
 * Escenario A2: dos analistas compitiendo por la misma solicitud.
 *
 * <p>Aqui no hay ninguna comprobacion explicita de concurrencia, y es a proposito. La proteccion
 * la da el bloqueo optimista del agregado: el segundo guardar afecta cero filas y el adaptador
 * lanza ConflictoConcurrenciaException, que el borde REST traduce a 409. Escribir aqui un "si ya
 * tiene analista, error" seria una condicion de carrera disfrazada de validacion.
 */
public final class TomarSolicitudService implements TomarSolicitudUseCase {

  private final SolicitudRepositoryPort solicitudes;
  private final EventoPublicadorPort eventos;
  private final RelojPort reloj;

  public TomarSolicitudService(
      SolicitudRepositoryPort solicitudes, EventoPublicadorPort eventos, RelojPort reloj) {
    this.solicitudes = Objects.requireNonNull(solicitudes);
    this.eventos = Objects.requireNonNull(eventos);
    this.reloj = Objects.requireNonNull(reloj);
  }

  @Override
  public Solicitud tomar(TomarSolicitudCommand comando) {
    Solicitud solicitud =
        solicitudes
            .buscarPorId(comando.solicitudId())
            .orElseThrow(
                () -> new SolicitudNoEncontradaException("La solicitud indicada no existe."));

    solicitud.tomar(comando.analista(), reloj.ahora());

    Solicitud guardada = solicitudes.guardar(solicitud);
    eventos.publicar(solicitud.drenarEventos());
    return guardada;
  }
}
