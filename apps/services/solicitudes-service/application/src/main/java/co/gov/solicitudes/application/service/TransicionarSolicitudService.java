package co.gov.solicitudes.application.service;

import co.gov.solicitudes.application.command.TransicionarSolicitudCommand;
import co.gov.solicitudes.application.port.in.TransicionarSolicitudUseCase;
import co.gov.solicitudes.application.port.out.EventoPublicadorPort;
import co.gov.solicitudes.application.port.out.RelojPort;
import co.gov.solicitudes.application.port.out.SolicitudRepositoryPort;
import co.gov.solicitudes.domain.exception.SolicitudNoEncontradaException;
import co.gov.solicitudes.domain.model.Solicitud;
import java.time.Instant;
import java.util.Objects;

/**
 * Resolver, devolver y cerrar.
 *
 * <p>El switch sobre la accion es exhaustivo sobre un enum: agregar una accion nueva rompe la
 * compilacion aqui, que es exactamente donde debe romperse.
 */
public final class TransicionarSolicitudService implements TransicionarSolicitudUseCase {

  private final SolicitudRepositoryPort solicitudes;
  private final EventoPublicadorPort eventos;
  private final RelojPort reloj;

  public TransicionarSolicitudService(
      SolicitudRepositoryPort solicitudes, EventoPublicadorPort eventos, RelojPort reloj) {
    this.solicitudes = Objects.requireNonNull(solicitudes);
    this.eventos = Objects.requireNonNull(eventos);
    this.reloj = Objects.requireNonNull(reloj);
  }

  @Override
  public Solicitud transicionar(TransicionarSolicitudCommand comando) {
    Solicitud solicitud =
        solicitudes
            .buscarPorId(comando.solicitudId())
            .orElseThrow(
                () -> new SolicitudNoEncontradaException("La solicitud indicada no existe."));

    Instant ahora = reloj.ahora();
    switch (comando.accion()) {
      case RESOLVER -> solicitud.resolver(comando.actor(), ahora);
      case DEVOLVER -> solicitud.devolver(comando.actor(), comando.motivo(), ahora);
      case CERRAR -> solicitud.cerrar(comando.actor(), ahora);
      case TOMAR ->
          throw new IllegalArgumentException(
              "TOMAR se expresa como la creacion de una asignacion, no como una transicion.");
    }

    Solicitud guardada = solicitudes.guardar(solicitud);
    eventos.publicar(solicitud.drenarEventos());
    return guardada;
  }
}
