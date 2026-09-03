package co.gov.solicitudes.application.service;

import co.gov.solicitudes.application.port.in.ConsultarCategoriasQuery;
import co.gov.solicitudes.application.port.out.CategoriaRepositoryPort;
import co.gov.solicitudes.domain.model.Categoria;
import java.util.List;
import java.util.Objects;

/** Catalogo de categorias activas para el formulario de creacion. */
public final class ConsultarCategoriasService implements ConsultarCategoriasQuery {

  private final CategoriaRepositoryPort categorias;

  public ConsultarCategoriasService(CategoriaRepositoryPort categorias) {
    this.categorias = Objects.requireNonNull(categorias);
  }

  @Override
  public List<Categoria> listarActivas() {
    return categorias.listarActivas();
  }
}
