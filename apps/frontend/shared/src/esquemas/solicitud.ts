import { z } from 'zod';
import { ESTADOS_SOLICITUD, PRIORIDADES, ROLES } from '../dominio/tipos';

/**
 * Esquemas de validacion de las respuestas del Servicio de Solicitudes.
 *
 * Toda respuesta del backend se valida con Zod antes de entrar al estado
 * (CLAUDE.md, estandares de frontend). Los campos y su forma exacta —incluido
 * que `historial` y `observaciones` llegan como `null` en el listado y como
 * arreglo en el detalle— se verificaron contra respuestas reales del servicio
 * corriendo, no se adivinaron desde el DTO de Java.
 */

const cambioEstadoSchema = z.object({
  id: z.uuid(),
  estadoOrigen: z.enum(ESTADOS_SOLICITUD).nullable(),
  estadoDestino: z.enum(ESTADOS_SOLICITUD),
  actorId: z.string(),
  actorRol: z.enum(ROLES),
  motivo: z.string().nullable(),
  ocurridoEn: z.iso.datetime(),
});

export type CambioEstado = z.infer<typeof cambioEstadoSchema>;

const observacionSchema = z.object({
  id: z.uuid(),
  texto: z.string(),
  actorId: z.string(),
  actorRol: z.enum(ROLES),
  ocurridoEn: z.iso.datetime(),
});

export type Observacion = z.infer<typeof observacionSchema>;

/**
 * Cubre las dos formas que emite el backend con un solo esquema: el listado
 * deja `historial`/`observaciones` en `null` (SolicitudResponse.resumen), el
 * detalle los llena (SolicitudResponse.detalle). Son el mismo tipo Java, y
 * aqui tambien lo son.
 */
export const solicitudSchema = z.object({
  id: z.uuid(),
  codigo: z.string(),
  asunto: z.string(),
  descripcion: z.string(),
  categoriaId: z.uuid(),
  prioridad: z.enum(PRIORIDADES),
  estado: z.enum(ESTADOS_SOLICITUD),
  solicitanteId: z.string(),
  analistaId: z.string().nullable(),
  creadaEn: z.iso.datetime(),
  actualizadaEn: z.iso.datetime(),
  historial: z.array(cambioEstadoSchema).nullable(),
  observaciones: z.array(observacionSchema).nullable(),
});

export type Solicitud = z.infer<typeof solicitudSchema>;

export function paginaDeSolicitudesSchema() {
  return z.object({
    content: z.array(solicitudSchema),
    page: z.number().int(),
    size: z.number().int(),
    totalElements: z.number().int(),
    totalPages: z.number().int(),
  });
}

export type PaginaDeSolicitudes = z.infer<ReturnType<typeof paginaDeSolicitudesSchema>>;

export const categoriaSchema = z.object({
  id: z.uuid(),
  codigo: z.string(),
  nombre: z.string(),
});

export type Categoria = z.infer<typeof categoriaSchema>;

export const categoriasSchema = z.array(categoriaSchema);

/**
 * Cuerpo de error RFC 9457, tal como lo emite ManejadorGlobalDeErrores.
 *
 * `codigo` es el contrato estable con el que la interfaz decide que hacer
 * (por ejemplo, mostrar el motivo en el campo del formulario si es
 * VALIDACION_DOMINIO, o un aviso de "otro ya la tomo" si es
 * CONFLICTO_CONCURRENCIA). `detail` es para mostrar al usuario, no para
 * tomar decisiones de codigo: esta redactado para personas y puede cambiar.
 */
export const problemDetailSchema = z.object({
  type: z.string(),
  title: z.string(),
  status: z.number().int(),
  detail: z.string(),
  instance: z.string().optional(),
  codigo: z.string(),
  correlationId: z.string(),
  timestamp: z.string(),
});

export type ProblemDetail = z.infer<typeof problemDetailSchema>;
