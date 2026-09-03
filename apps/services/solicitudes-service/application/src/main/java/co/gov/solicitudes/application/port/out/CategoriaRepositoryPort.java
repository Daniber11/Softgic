package co.gov.solicitudes.application.port.out;

import co.gov.solicitudes.domain.model.Categoria;
import co.gov.solicitudes.domain.model.CategoriaId;
import java.util.List;
import java.util.Optional;

/** Acceso al catalogo de categorias. */
public interface CategoriaRepositoryPort {

  List<Categoria> listarActivas();

  Optional<Categoria> buscarPorId(CategoriaId id);
}
