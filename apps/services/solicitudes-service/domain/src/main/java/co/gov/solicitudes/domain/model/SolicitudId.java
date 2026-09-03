package co.gov.solicitudes.domain.model;

import java.util.Objects;
import java.util.UUID;

/** Identidad del agregado. Existe para que un UUID cualquiera no pueda pasar por una solicitud. */
public record SolicitudId(UUID valor) {

  public SolicitudId {
    Objects.requireNonNull(valor, "El identificador de la solicitud es obligatorio.");
  }

  public static SolicitudId nuevo() {
    return new SolicitudId(UUID.randomUUID());
  }

  @Override
  public String toString() {
    return valor.toString();
  }
}
