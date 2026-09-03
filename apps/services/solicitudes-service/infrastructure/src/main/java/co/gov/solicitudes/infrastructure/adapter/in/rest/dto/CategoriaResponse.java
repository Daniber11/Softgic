package co.gov.solicitudes.infrastructure.adapter.in.rest.dto;

import co.gov.solicitudes.domain.model.Categoria;
import java.util.UUID;

/** Entrada del catalogo tal como la ve el cliente. */
public record CategoriaResponse(UUID id, String codigo, String nombre) {

  public static CategoriaResponse desde(Categoria categoria) {
    return new CategoriaResponse(categoria.id().valor(), categoria.codigo(), categoria.nombre());
  }
}
