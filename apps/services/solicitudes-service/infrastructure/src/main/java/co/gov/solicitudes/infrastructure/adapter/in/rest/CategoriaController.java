package co.gov.solicitudes.infrastructure.adapter.in.rest;

import co.gov.solicitudes.application.port.in.ConsultarCategoriasQuery;
import co.gov.solicitudes.infrastructure.adapter.in.rest.dto.CategoriaResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Catalogo de categorias. Solo lectura, como especifica el reto. */
@RestController
@RequestMapping("/api/v1/categorias")
@Tag(name = "Categorias", description = "Catalogo de categorias activas")
public class CategoriaController {

  private final ConsultarCategoriasQuery consultarCategorias;

  public CategoriaController(ConsultarCategoriasQuery consultarCategorias) {
    this.consultarCategorias = consultarCategorias;
  }

  @GetMapping
  @PreAuthorize("isAuthenticated()")
  @Operation(summary = "Lista las categorias activas para el formulario de creacion")
  public List<CategoriaResponse> listar() {
    return consultarCategorias.listarActivas().stream().map(CategoriaResponse::desde).toList();
  }
}
