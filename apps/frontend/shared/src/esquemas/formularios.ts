import { z } from 'zod';
import { ACCIONES_TRANSICION, PRIORIDADES } from '../dominio/tipos';

/**
 * Esquemas de los formularios de escritura.
 *
 * Reflejan las mismas cotas que el backend aplica en `CrearSolicitudRequest`,
 * `TransicionRequest` y `ObservacionRequest` (longitudes maximas, campos
 * obligatorios). Validar tambien aqui no es redundante con el servidor: es
 * la diferencia entre que el usuario vea el error al perder el foco del
 * campo o tenga que esperar un viaje de red para enterarse. La autorizacion
 * real, en cualquier caso, siempre se valida en el servidor.
 */

export const crearSolicitudSchema = z.object({
  asunto: z
    .string()
    .trim()
    .min(1, 'El asunto es obligatorio.')
    .max(200, 'El asunto no puede exceder 200 caracteres.'),
  descripcion: z
    .string()
    .trim()
    .min(1, 'La descripción es obligatoria.')
    .max(2000, 'La descripción no puede exceder 2000 caracteres.'),
  categoriaId: z.uuid('Seleccione una categoría.'),
  prioridad: z.enum(PRIORIDADES, { message: 'Seleccione una prioridad.' }),
});

export type CrearSolicitudFormulario = z.infer<typeof crearSolicitudSchema>;

/**
 * El motivo solo es obligatorio cuando la accion es DEVOLVER; el dominio lo
 * exige asi (Solicitud.devolver valida un texto no vacio) y el formulario
 * refleja la misma regla con `superRefine` en lugar de duplicarla como un
 * campo condicionalmente requerido en dos esquemas separados.
 */
export const transicionSchema = z
  .object({
    accion: z.enum(ACCIONES_TRANSICION),
    motivo: z.string().trim().max(500, 'El motivo no puede exceder 500 caracteres.').optional(),
  })
  .superRefine((valor, ctx) => {
    if (valor.accion === 'DEVOLVER' && !valor.motivo?.trim()) {
      ctx.addIssue({
        code: 'custom',
        path: ['motivo'],
        message: 'El motivo es obligatorio al devolver una solicitud.',
      });
    }
  });

export type TransicionFormulario = z.infer<typeof transicionSchema>;

export const observacionSchema = z.object({
  texto: z
    .string()
    .trim()
    .min(1, 'La observación no puede estar vacía.')
    .max(1000, 'La observación no puede exceder 1000 caracteres.'),
});

export type ObservacionFormulario = z.infer<typeof observacionSchema>;
