package co.gov.solicitudes.domain.model;

import java.util.Objects;
import java.util.UUID;

/** Identidad de una categoria del catalogo. */
public record CategoriaId(UUID valor) {

  public CategoriaId {
    Objects.requireNonNull(valor, "El identificador de la categoria es obligatorio.");
  }

  @Override
  public String toString() {
    return valor.toString();
  }
}
