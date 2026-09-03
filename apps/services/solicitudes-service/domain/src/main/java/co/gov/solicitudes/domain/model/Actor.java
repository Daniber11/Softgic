package co.gov.solicitudes.domain.model;

import co.gov.solicitudes.domain.exception.ValidacionDominioException;
import java.util.Objects;

/**
 * Quien ejecuta una accion: su identificador y el rol con el que actua.
 *
 * <p>El identificador proviene del claim {@code sub} del token, no de un formulario. El dominio no
 * sabe que existe un token; solo exige que quien actue tenga identidad y rol.
 */
public record Actor(String id, Rol rol) {

  public Actor {
    if (id == null || id.isBlank()) {
      throw new ValidacionDominioException("El actor debe tener un identificador.");
    }
    Objects.requireNonNull(rol, "El actor debe tener un rol.");
  }

  public boolean tieneRol(Rol esperado) {
    return rol == esperado;
  }
}
