package co.gov.solicitudes.application.service;

import co.gov.solicitudes.application.port.in.ConsultarSolicitudesQuery;
import co.gov.solicitudes.application.command.FiltroSolicitudes;
import co.gov.solicitudes.application.port.out.SolicitudRepositoryPort;
import co.gov.solicitudes.application.result.Pagina;
import co.gov.solicitudes.domain.model.Actor;
import co.gov.solicitudes.domain.model.Rol;
import co.gov.solicitudes.domain.model.Solicitud;
import java.util.Objects;

/**
 * Bandeja de solicitudes.
 *
 * <p><b>El filtrado por pertenencia se impone aqui, no se acepta del cliente.</b> Si el
 * controlador tradujera un parametro "mias=true", bastaria con no enviarlo para ver las
 * solicitudes de todo el mundo. El caso de uso sobrescribe ese criterio segun el rol del token, de
 * modo que no hay peticion capaz de saltarselo.
 */
public final class ConsultarSolicitudesService implements ConsultarSolicitudesQuery {

  private static final int TAMANIO_MAXIMO_PAGINA = 100;
  private static final int TAMANIO_POR_DEFECTO = 20;

  private final SolicitudRepositoryPort solicitudes;

  public ConsultarSolicitudesService(SolicitudRepositoryPort solicitudes) {
    this.solicitudes = Objects.requireNonNull(solicitudes);
  }

  @Override
  public Pagina<Solicitud> consultar(
      FiltroSolicitudes filtro, int pagina, int tamanio, Actor consultante) {

    FiltroSolicitudes filtroEfectivo = aplicarRestriccionPorRol(filtro, consultante);
    return solicitudes.buscar(filtroEfectivo, Math.max(pagina, 0), acotarTamanio(tamanio));
  }

  private FiltroSolicitudes aplicarRestriccionPorRol(
      FiltroSolicitudes filtro, Actor consultante) {

    // ANALISTA y SUPERVISOR ven la bandeja completa; el SOLICITANTE, solo lo suyo.
    String restriccion = consultante.tieneRol(Rol.SOLICITANTE) ? consultante.id() : null;

    return new FiltroSolicitudes(
        filtro.estado(),
        filtro.categoriaId(),
        filtro.prioridad(),
        filtro.desde(),
        filtro.hasta(),
        restriccion);
  }

  private int acotarTamanio(int tamanio) {
    if (tamanio <= 0) {
      return TAMANIO_POR_DEFECTO;
    }
    return Math.min(tamanio, TAMANIO_MAXIMO_PAGINA);
  }
}
