package co.gov.solicitudes.application.exception;

/**
 * Otra transaccion modifico la misma solicitud entre la lectura y la escritura.
 *
 * <p>Se traduce a 409, no a 422, y la distincion importa: 409 significa que el estado cambio bajo
 * los pies del cliente y reintentar tiene sentido; 422 significa que lo pedido nunca fue posible.
 * Este es el escenario A2, dos analistas tomando la misma solicitud a la vez.
 *
 * <p>Vive en la capa de aplicacion y no en el dominio porque la concurrencia es una preocupacion
 * de la persistencia, no una regla de negocio. El dominio ni siquiera sabe que hay una base de
 * datos.
 */
public final class ConflictoConcurrenciaException extends RuntimeException {

  private static final long serialVersionUID = 1L;
  private static final String CODIGO = "CONFLICTO_CONCURRENCIA";

  public ConflictoConcurrenciaException(String mensaje, Throwable causa) {
    super(mensaje, causa);
  }

  public String codigo() {
    return CODIGO;
  }
}
