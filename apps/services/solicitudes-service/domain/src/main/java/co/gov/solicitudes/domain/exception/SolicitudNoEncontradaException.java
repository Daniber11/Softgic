package co.gov.solicitudes.domain.exception;

/** No existe la solicitud, o el actor no tiene derecho a saber que existe. */
public final class SolicitudNoEncontradaException extends DominioException {

  private static final long serialVersionUID = 1L;
  private static final String CODIGO = "SOLICITUD_NO_ENCONTRADA";

  public SolicitudNoEncontradaException(String mensaje) {
    super(CODIGO, mensaje);
  }
}
