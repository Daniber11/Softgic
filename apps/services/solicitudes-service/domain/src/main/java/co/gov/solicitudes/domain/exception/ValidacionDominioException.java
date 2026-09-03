package co.gov.solicitudes.domain.exception;

/** Un invariante del modelo no se cumple: un campo obligatorio vacio, un valor fuera de rango. */
public final class ValidacionDominioException extends DominioException {

  private static final long serialVersionUID = 1L;
  private static final String CODIGO = "VALIDACION_DOMINIO";

  public ValidacionDominioException(String mensaje) {
    super(CODIGO, mensaje);
  }
}
