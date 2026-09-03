package co.gov.solicitudes.domain.model;

import co.gov.solicitudes.domain.exception.ValidacionDominioException;
import java.util.regex.Pattern;

/**
 * Codigo legible de la solicitud, del tipo SOL-2026-000123.
 *
 * <p>Existe para que el ciudadano y el analista hablen del mismo expediente sin recitar un UUID.
 * El formato se valida en el tipo: un codigo mal formado no llega a construirse.
 */
public record CodigoSolicitud(String valor) {

  private static final Pattern FORMATO = Pattern.compile("^SOL-[0-9]{4}-[0-9]{6}$");

  public CodigoSolicitud {
    if (valor == null || !FORMATO.matcher(valor).matches()) {
      throw new ValidacionDominioException(
          "El codigo de la solicitud debe tener el formato SOL-AAAA-NNNNNN.");
    }
  }

  @Override
  public String toString() {
    return valor;
  }
}
