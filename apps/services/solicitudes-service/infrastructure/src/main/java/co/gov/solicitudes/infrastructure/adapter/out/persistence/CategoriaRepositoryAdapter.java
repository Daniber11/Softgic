package co.gov.solicitudes.infrastructure.adapter.out.persistence;

import co.gov.solicitudes.application.port.out.CategoriaRepositoryPort;
import co.gov.solicitudes.domain.model.Categoria;
import co.gov.solicitudes.domain.model.CategoriaId;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** Acceso al catalogo de categorias. */
@Repository
public class CategoriaRepositoryAdapter implements CategoriaRepositoryPort {

  private final CategoriaJpaRepository repositorio;
  private final SolicitudMapper mapper;

  public CategoriaRepositoryAdapter(CategoriaJpaRepository repositorio, SolicitudMapper mapper) {
    this.repositorio = repositorio;
    this.mapper = mapper;
  }

  @Override
  public List<Categoria> listarActivas() {
    return repositorio.findByActivaTrueOrderByNombreAsc().stream().map(mapper::aDominio).toList();
  }

  @Override
  public Optional<Categoria> buscarPorId(CategoriaId id) {
    return repositorio.findById(id.valor()).map(mapper::aDominio);
  }
}
