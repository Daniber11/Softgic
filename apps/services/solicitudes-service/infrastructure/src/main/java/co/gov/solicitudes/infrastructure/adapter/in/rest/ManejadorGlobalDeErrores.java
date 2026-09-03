package co.gov.solicitudes.infrastructure.adapter.in.rest;

import co.gov.solicitudes.application.exception.ConflictoConcurrenciaException;
import co.gov.solicitudes.domain.exception.AccionNoPermitidaException;
import co.gov.solicitudes.domain.exception.CategoriaInactivaException;
import co.gov.solicitudes.domain.exception.SolicitudNoEncontradaException;
import co.gov.solicitudes.domain.exception.TransicionInvalidaException;
import co.gov.solicitudes.domain.exception.ValidacionDominioException;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Traduccion de excepciones a respuestas HTTP.
 *
 * <p>Aqui se materializa la semantica de codigos del blueprint. Las dos distinciones que hay que
 * poder defender:
 *
 * <ul>
 *   <li><b>409 frente a 422.</b> 409 dice "el estado cambio bajo tus pies, vuelve a leer y
 *       reintenta"; 422 dice "lo que pediste nunca fue posible, no reintentes". Un cliente que
 *       reintenta automaticamente necesita esa diferencia para no entrar en un bucle.
 *   <li><b>404 frente a 403 al consultar.</b> Un solicitante que pide una solicitud ajena recibe
 *       404. Un 403 confirmaria que el recurso existe, que ya es informacion que no le
 *       corresponde.
 * </ul>
 *
 * <p>Ningun manejador expone la excepcion original. Un stack trace o un mensaje de Hibernate en la
 * respuesta revela el esquema, la version de la libreria y a veces datos de otras filas.
 */
@RestControllerAdvice
public class ManejadorGlobalDeErrores {

  private static final Logger LOG = LoggerFactory.getLogger(ManejadorGlobalDeErrores.class);

  /** 422: la regla de dominio no se cumple. Escenario A4. */
  @ExceptionHandler(TransicionInvalidaException.class)
  public ProblemDetail manejarTransicionInvalida(TransicionInvalidaException e) {
    return ProblemDetailsFactory.crear(
        HttpStatus.UNPROCESSABLE_ENTITY, "Transicion no permitida", e.getMessage(), e.codigo());
  }

  /** 422: invariante del modelo violado. */
  @ExceptionHandler(ValidacionDominioException.class)
  public ProblemDetail manejarValidacionDominio(ValidacionDominioException e) {
    return ProblemDetailsFactory.crear(
        HttpStatus.UNPROCESSABLE_ENTITY, "Datos invalidos", e.getMessage(), e.codigo());
  }

  /** 422: la categoria existe pero fue retirada del catalogo. */
  @ExceptionHandler(CategoriaInactivaException.class)
  public ProblemDetail manejarCategoriaInactiva(CategoriaInactivaException e) {
    return ProblemDetailsFactory.crear(
        HttpStatus.UNPROCESSABLE_ENTITY, "Categoria no disponible", e.getMessage(), e.codigo());
  }

  /** 403: el agregado rechazo la accion por rol. Tercera capa de defensa. Escenario A3. */
  @ExceptionHandler(AccionNoPermitidaException.class)
  public ProblemDetail manejarAccionNoPermitida(AccionNoPermitidaException e) {
    return ProblemDetailsFactory.crear(
        HttpStatus.FORBIDDEN, "Autorizacion insuficiente", e.getMessage(), e.codigo());
  }

  /** 403: el filtro o la anotacion rechazaron la operacion. */
  @ExceptionHandler(AccessDeniedException.class)
  public ProblemDetail manejarAccesoDenegado(AccessDeniedException e) {
    return ProblemDetailsFactory.crear(
        HttpStatus.FORBIDDEN,
        "Autorizacion insuficiente",
        "Su rol no permite ejecutar esta operacion.",
        "ACCION_NO_PERMITIDA");
  }

  /** 404: no existe, o el consultante no tiene derecho a saber que existe. */
  @ExceptionHandler(SolicitudNoEncontradaException.class)
  public ProblemDetail manejarNoEncontrada(SolicitudNoEncontradaException e) {
    return ProblemDetailsFactory.crear(
        HttpStatus.NOT_FOUND, "Solicitud no encontrada", e.getMessage(), e.codigo());
  }

  /** 409: otra transaccion gano la carrera. Escenario A2. */
  @ExceptionHandler(ConflictoConcurrenciaException.class)
  public ProblemDetail manejarConflicto(ConflictoConcurrenciaException e) {
    return ProblemDetailsFactory.crear(
        HttpStatus.CONFLICT, "Conflicto de concurrencia", e.getMessage(), e.codigo());
  }

  /** 400: error sintactico en el cuerpo. Distinto de 422, que es semantico. */
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ProblemDetail manejarCuerpoInvalido(MethodArgumentNotValidException e) {
    String detalle =
        e.getBindingResult().getFieldErrors().stream()
            .map(error -> "%s: %s".formatted(error.getField(), error.getDefaultMessage()))
            .collect(Collectors.joining("; "));

    return ProblemDetailsFactory.crear(
        HttpStatus.BAD_REQUEST, "Peticion invalida", detalle, "VALIDACION_DOMINIO");
  }

  /** 400: un UUID mal formado o un enum inexistente en la ruta o en un parametro. */
  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ProblemDetail manejarTipoInvalido(MethodArgumentTypeMismatchException e) {
    return ProblemDetailsFactory.crear(
        HttpStatus.BAD_REQUEST,
        "Peticion invalida",
        "El parametro %s tiene un formato invalido.".formatted(e.getName()),
        "VALIDACION_DOMINIO");
  }

  /** 400: valor fuera del conjunto permitido, tipicamente un enum recibido por parametro. */
  @ExceptionHandler(IllegalArgumentException.class)
  public ProblemDetail manejarArgumentoInvalido(IllegalArgumentException e) {
    return ProblemDetailsFactory.crear(
        HttpStatus.BAD_REQUEST,
        "Peticion invalida",
        "Uno de los valores enviados no es admitido.",
        "VALIDACION_DOMINIO");
  }

  /**
   * Ultima red de seguridad.
   *
   * <p>Es la unica captura amplia del sistema y esta justificada: sin ella, un fallo inesperado
   * devolveria la pagina de error por defecto con la traza. Registra el detalle completo en el log
   * del servidor, donde si corresponde, y devuelve al cliente un mensaje que no revela nada.
   */
  @ExceptionHandler(RuntimeException.class)
  public ProblemDetail manejarInesperado(RuntimeException e) {
    LOG.error("Error no controlado atendiendo la peticion", e);
    return ProblemDetailsFactory.crear(
        HttpStatus.INTERNAL_SERVER_ERROR,
        "Error interno",
        "Ocurrio un error inesperado. Reporte el correlationId al equipo tecnico.",
        "ERROR_INTERNO");
  }
}
