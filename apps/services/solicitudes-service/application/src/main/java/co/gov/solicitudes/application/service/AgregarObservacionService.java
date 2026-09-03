package co.gov.solicitudes.application.service;

import co.gov.solicitudes.application.command.AgregarObservacionCommand;
import co.gov.solicitudes.application.port.in.AgregarObservacionUseCase;
import co.gov.solicitudes.application.port.out.RelojPort;
import co.gov.solicitudes.application.port.out.SolicitudRepositoryPort;
import co.gov.solicitudes.domain.exception.SolicitudNoEncontradaException;
import co.gov.solicitudes.domain.model.Solicitud;
import java.util.Objects;
import java.util.UUID;

/**
 * Agregar una observacion no es una transicion de estado y no emite evento.
 *
 * <p>Se deja explicito porque la tentacion de publicar un ObservacionAgregada existe: no aporta al
 * modelo analitico, que mide transiciones, y agregaria trafico sin consumidor.
 */
public final class AgregarObservacionService implements AgregarObservacionUseCase {

  private final SolicitudRepositoryPort solicitudes;
  private final RelojPort reloj;

  public AgregarObservacionService(SolicitudRepositoryPort solicitudes, RelojPort reloj) {
    this.solicitudes = Objects.requireNonNull(solicitudes);
    this.reloj = Objects.requireNonNull(reloj);
  }

  @Override
  public Solicitud agregar(AgregarObservacionCommand comando) {
    Solicitud solicitud =
        solicitudes
            .buscarPorId(comando.solicitudId())
            .orElseThrow(
                () -> new SolicitudNoEncontradaException("La solicitud indicada no existe."));

    solicitud.agregarObservacion(UUID.randomUUID(), comando.texto(), comando.autor(), reloj.ahora());
    return solicitudes.guardar(solicitud);
  }
}
