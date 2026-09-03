package co.gov.solicitudes.infrastructure.adapter.out.persistence;

import co.gov.solicitudes.application.exception.ConflictoConcurrenciaException;
import co.gov.solicitudes.application.command.FiltroSolicitudes;
import co.gov.solicitudes.application.port.out.SolicitudRepositoryPort;
import co.gov.solicitudes.application.result.Pagina;
import co.gov.solicitudes.domain.model.Solicitud;
import co.gov.solicitudes.domain.model.SolicitudId;
import java.util.Optional;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

/**
 * Implementacion JPA del puerto de persistencia.
 *
 * <p><b>Aqui se resuelve el escenario A2.</b> Cuando dos analistas toman la misma solicitud a la
 * vez, ambos leen version = N. El primero en guardar la sube a N+1; el UPDATE del segundo lleva
 * WHERE version = N, afecta cero filas y Hibernate lanza OptimisticLockingFailureException. Se
 * traduce a una excepcion de aplicacion que el borde REST convierte en 409.
 *
 * <p>La traduccion ocurre en esta frontera y no mas adentro: la capa de aplicacion no debe conocer
 * las excepciones de Spring, igual que no conoce las de JDBC.
 */
@Repository
public class SolicitudRepositoryAdapter implements SolicitudRepositoryPort {

  private static final String CAMPO_ORDEN_POR_DEFECTO = "creadaEn";

  private final SolicitudJpaRepository repositorio;
  private final SolicitudMapper mapper;

  public SolicitudRepositoryAdapter(SolicitudJpaRepository repositorio, SolicitudMapper mapper) {
    this.repositorio = repositorio;
    this.mapper = mapper;
  }

  @Override
  public Solicitud guardar(Solicitud solicitud) {
    // Se recupera la entidad gestionada para conservar su campo version. Guardar
    // una entidad nueva con el mismo id perderia el bloqueo optimista y con el,
    // la deteccion del conflicto.
    SolicitudEntity existente = repositorio.findById(solicitud.id().valor()).orElse(null);
    SolicitudEntity entidad = mapper.aEntidad(solicitud, existente);

    try {
      return mapper.aDominio(repositorio.saveAndFlush(entidad));
    } catch (OptimisticLockingFailureException e) {
      throw new ConflictoConcurrenciaException(
          "La solicitud fue modificada por otra operacion. Vuelva a consultarla y reintente.", e);
    }
  }

  @Override
  public Optional<Solicitud> buscarPorId(SolicitudId id) {
    return repositorio.findById(id.valor()).map(mapper::aDominio);
  }

  @Override
  public boolean existeCodigo(String codigo) {
    return repositorio.existsByCodigo(codigo);
  }

  @Override
  public Pagina<Solicitud> buscar(FiltroSolicitudes filtro, int pagina, int tamanio) {
    Page<SolicitudEntity> resultado =
        repositorio.buscarConFiltros(
            filtro.estado() == null ? null : filtro.estado().name(),
            filtro.categoriaId(),
            filtro.prioridad() == null ? null : filtro.prioridad().name(),
            filtro.desde(),
            filtro.hasta(),
            filtro.soloDelSolicitante(),
            PageRequest.of(pagina, tamanio, Sort.by(Sort.Direction.DESC, CAMPO_ORDEN_POR_DEFECTO)));

    return new Pagina<>(
        resultado.getContent().stream().map(mapper::aDominioResumen).toList(),
        resultado.getNumber(),
        resultado.getSize(),
        resultado.getTotalElements());
  }
}
