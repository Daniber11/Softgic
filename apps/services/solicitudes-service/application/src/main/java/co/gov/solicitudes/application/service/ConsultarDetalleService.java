package co.gov.solicitudes.application.service;

import co.gov.solicitudes.application.port.in.ConsultarDetalleQuery;
import co.gov.solicitudes.application.port.out.SolicitudRepositoryPort;
import co.gov.solicitudes.domain.exception.SolicitudNoEncontradaException;
import co.gov.solicitudes.domain.model.Actor;
import co.gov.solicitudes.domain.model.Rol;
import co.gov.solicitudes.domain.model.Solicitud;
import co.gov.solicitudes.domain.model.SolicitudId;
import java.util.Objects;

/**
 * Detalle de una solicitud.
 *
 * <p><b>Un solicitante que pide una solicitud ajena recibe 404, no 403.</b> Un 403 confirmaria que
 * el recurso existe, que ya es informacion. Con identificadores secuenciales esa diferencia
 * permitiria enumerar el sistema; aqui son UUID, pero la regla se aplica igual porque no cuesta
 * nada y evita depender de que el identificador siga siendo opaco manana.
 */
public final class ConsultarDetalleService implements ConsultarDetalleQuery {

  private final SolicitudRepositoryPort solicitudes;

  public ConsultarDetalleService(SolicitudRepositoryPort solicitudes) {
    this.solicitudes = Objects.requireNonNull(solicitudes);
  }

  @Override
  public Solicitud consultar(SolicitudId id, Actor consultante) {
    Solicitud solicitud =
        solicitudes
            .buscarPorId(id)
            .orElseThrow(
                () -> new SolicitudNoEncontradaException("La solicitud indicada no existe."));

    if (consultante.tieneRol(Rol.SOLICITANTE) && !solicitud.perteneceA(consultante.id())) {
      throw new SolicitudNoEncontradaException("La solicitud indicada no existe.");
    }

    return solicitud;
  }
}
