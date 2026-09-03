package co.gov.solicitudes.domain.exception;

/**
 * Raiz de las excepciones de negocio.
 *
 * <p>Cada una lleva un codigo estable que el adaptador REST traduce a Problem Details. El codigo
 * es parte del contrato publico: el frontend puede ramificar sobre el sin leer el mensaje, que
 * esta redactado para personas y puede cambiar.
 */
public abstract class DominioException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final String codigo;

  protected DominioException(String codigo, String mensaje) {
    super(mensaje);
    this.codigo = codigo;
  }

  public String codigo() {
    return codigo;
  }
}
