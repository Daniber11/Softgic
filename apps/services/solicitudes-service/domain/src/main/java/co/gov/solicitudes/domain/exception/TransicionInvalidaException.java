package co.gov.solicitudes.domain.exception;

/**
 * La accion solicitada no es posible desde el estado actual.
 *
 * <p>Se traduce a 422: lo pedido nunca fue posible, reintentar no cambiaria nada. Es distinto de
 * un 409, que significa que el estado cambio y reintentar si tiene sentido.
 */
public final class TransicionInvalidaException extends DominioException {

  private static final long serialVersionUID = 1L;
  private static final String CODIGO = "TRANSICION_INVALIDA";

  public TransicionInvalidaException(String mensaje) {
    super(CODIGO, mensaje);
  }
}
