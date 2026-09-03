package co.gov.solicitudes.domain.model;

import co.gov.solicitudes.domain.exception.ValidacionDominioException;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Comentario que un analista o un supervisor deja sobre una solicitud. */
public record Observacion(UUID id, String texto, Actor autor, Instant ocurridoEn) {

  private static final int LONGITUD_MAXIMA = 1000;

  public Observacion {
    Objects.requireNonNull(id, "La observacion debe tener identificador.");
    Objects.requireNonNull(autor, "La observacion debe tener autor.");
    Objects.requireNonNull(ocurridoEn, "La observacion debe tener fecha.");
    if (texto == null || texto.isBlank()) {
      throw new ValidacionDominioException("La observacion no puede estar vacia.");
    }
    if (texto.length() > LONGITUD_MAXIMA) {
      throw new ValidacionDominioException(
          "La observacion no puede exceder " + LONGITUD_MAXIMA + " caracteres.");
    }
  }
}
