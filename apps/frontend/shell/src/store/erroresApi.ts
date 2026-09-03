import type { SerializedError } from '@reduxjs/toolkit';
import type { FetchBaseQueryError } from '@reduxjs/toolkit/query/react';
import { problemDetailSchema, type ProblemDetail } from '@shared/esquemas/solicitud';

/**
 * Traduce un error de RTK Query al Problem Details que el backend emitio,
 * si lo hay. Todos los errores 4xx/5xx del backend tienen esta forma
 * (ManejadorGlobalDeErrores, RFC 9457); esta funcion es el unico lugar del
 * frontend que sabe leerla, para que ningun componente tenga que repetir el
 * `if ('data' in error && ...)`.
 */
export function extraerProblemDetail(
  error: FetchBaseQueryError | SerializedError | undefined,
): ProblemDetail | null {
  if (!error || !('data' in error)) {
    return null;
  }
  const resultado = problemDetailSchema.safeParse(error.data);
  return resultado.success ? resultado.data : null;
}

/** Mensaje listo para mostrar, con una redaccion razonable cuando no hay Problem Details. */
export function mensajeDeError(error: FetchBaseQueryError | SerializedError | undefined): string {
  const problema = extraerProblemDetail(error);
  if (problema) {
    return problema.detail;
  }
  if (error && 'status' in error && error.status === 'FETCH_ERROR') {
    return 'No se pudo contactar al servidor. Verifique su conexión.';
  }
  return 'Ocurrió un error inesperado.';
}
