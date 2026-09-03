package co.gov.solicitudes.domain.exception;

/**
 * El actor no tiene el rol que la accion exige.
 *
 * <p>Tercera capa de defensa: aunque el filtro de seguridad y el borde de aplicacion ya lo
 * validan, el agregado lo vuelve a comprobar. Asi la regla sobrevive si manana llega un comando
 * por un canal distinto de REST.
 */
public final class AccionNoPermitidaException extends DominioException {

  private static final long serialVersionUID = 1L;
  private static final String CODIGO = "ACCION_NO_PERMITIDA";

  public AccionNoPermitidaException(String mensaje) {
    super(CODIGO, mensaje);
  }
}
