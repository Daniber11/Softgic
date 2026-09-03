package co.gov.solicitudes.infrastructure.idempotencia;

import java.util.Optional;

/**
 * Almacen de respuestas ya emitidas para una llave de idempotencia.
 *
 * <p><b>Por que existe esta interfaz.</b> El filtro que interpreta la cabecera vive en el
 * adaptador REST y la tabla vive en el adaptador de persistencia, pero una prueba de ArchUnit
 * prohibe que el adaptador de entrada dependa del de salida: es la regla que impide devolver una
 * entidad JPA como respuesta HTTP. Esta interfaz, en terreno neutral, deja que el filtro exprese
 * lo que necesita sin conocer como se guarda.
 *
 * <p>Es el mismo principio de puertos y adaptadores aplicado dentro de la infraestructura. No es
 * un puerto de aplicacion: {@code Idempotency-Key} es una preocupacion del transporte HTTP y no
 * tiene por que llegar al nucleo (ADR-010).
 */
public interface RegistroIdempotencia {

  /** Devuelve lo registrado para la llave, si existe y no ha expirado. */
  Optional<RespuestaRegistrada> buscar(String llave);

  void registrar(String llave, String hashCuerpo, int estadoHttp, String respuesta);

  /** Respuesta emitida en su momento, para devolverla identica ante un reintento. */
  record RespuestaRegistrada(String hashCuerpo, int estadoHttp, String cuerpo) {}
}
