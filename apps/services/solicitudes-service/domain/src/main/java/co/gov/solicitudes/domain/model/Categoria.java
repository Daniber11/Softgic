package co.gov.solicitudes.domain.model;

import co.gov.solicitudes.domain.exception.ValidacionDominioException;
import java.util.Objects;

/** Entrada del catalogo de categorias. Solo las activas admiten solicitudes nuevas. */
public record Categoria(CategoriaId id, String codigo, String nombre, boolean activa) {

  public Categoria {
    Objects.requireNonNull(id, "La categoria debe tener identificador.");
    if (codigo == null || codigo.isBlank()) {
      throw new ValidacionDominioException("La categoria debe tener codigo.");
    }
    if (nombre == null || nombre.isBlank()) {
      throw new ValidacionDominioException("La categoria debe tener nombre.");
    }
  }
}
