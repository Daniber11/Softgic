package co.gov.solicitudes.application.port.out;

import co.gov.solicitudes.application.command.FiltroSolicitudes;
import co.gov.solicitudes.application.result.Pagina;
import co.gov.solicitudes.domain.model.Solicitud;
import co.gov.solicitudes.domain.model.SolicitudId;
import java.util.Optional;

/**
 * Persistencia del agregado.
 *
 * <p>Habla en terminos del dominio, no del modelo relacional: quien lo implementa se encarga de
 * traducir. El puerto es solo la interfaz; los tipos que viajan por el viven en command y result.
 */
public interface SolicitudRepositoryPort {

  /**
   * Guarda el agregado.
   *
   * @throws co.gov.solicitudes.application.exception.ConflictoConcurrenciaException si otra
   *     transaccion modifico la misma solicitud entre la lectura y la escritura
   */
  Solicitud guardar(Solicitud solicitud);

  Optional<Solicitud> buscarPorId(SolicitudId id);

  boolean existeCodigo(String codigo);

  Pagina<Solicitud> buscar(FiltroSolicitudes filtro, int pagina, int tamanio);
}
