package co.gov.solicitudes.domain.exception;

/** La categoria existe pero fue retirada del catalogo y no admite solicitudes nuevas. */
public final class CategoriaInactivaException extends DominioException {

  private static final long serialVersionUID = 1L;
  private static final String CODIGO = "CATEGORIA_INACTIVA";

  public CategoriaInactivaException(String mensaje) {
    super(CODIGO, mensaje);
  }
}
