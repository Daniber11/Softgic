import { z } from 'zod';

/**
 * Esquemas del Servicio de Indicadores, verificados contra respuestas reales:
 *
 *   GET /resumen    {"porEstado":{"CERRADA":3,...},"porCategoria":{...},"promedioMinutosHastaResolucion":0}
 *   GET /tendencia  {"porDia":{"2026-09-03":6}}
 *
 * Los mapas son de clave dinamica (el estado o la fecha), por eso
 * z.record en lugar de un objeto con campos fijos.
 */

export const resumenIndicadoresSchema = z.object({
  porEstado: z.record(z.string(), z.number().int()),
  porCategoria: z.record(z.string(), z.number().int()),
  promedioMinutosHastaResolucion: z.number().int(),
});

export type ResumenIndicadores = z.infer<typeof resumenIndicadoresSchema>;

export const tendenciaIndicadoresSchema = z.object({
  porDia: z.record(z.string(), z.number().int()),
});

export type TendenciaIndicadores = z.infer<typeof tendenciaIndicadoresSchema>;
