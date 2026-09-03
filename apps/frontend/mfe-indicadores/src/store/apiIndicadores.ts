import { createApi, fetchBaseQuery } from '@reduxjs/toolkit/query/react';
import {
  resumenIndicadoresSchema,
  tendenciaIndicadoresSchema,
  type ResumenIndicadores,
  type TendenciaIndicadores,
} from '@shared/esquemas/indicadores';
import { seleccionarToken } from './authSlice';

/**
 * Capa de API del remoto sobre RTK Query, igual que en el shell: toda
 * respuesta se valida con Zod (via `responseSchema`, soportado de forma
 * nativa por RTK Query 2.x para esquemas Standard Schema) antes de llegar al
 * componente.
 */
export const apiIndicadores = createApi({
  reducerPath: 'apiIndicadores',
  baseQuery: fetchBaseQuery({
    baseUrl: `${__INDICADORES_API_URL__}/api/v1/indicadores`,
    prepareHeaders: (headers, { getState }) => {
      const token = seleccionarToken(getState() as { auth: { token: string | null } });
      if (token) {
        headers.set('Authorization', `Bearer ${token}`);
      }
      return headers;
    },
  }),
  catchSchemaFailure: (error) => ({
    status: 'CUSTOM_ERROR' as const,
    error: `La respuesta del servidor no cumple el contrato esperado (${error.schemaName}).`,
  }),
  endpoints: (build) => ({
    obtenerResumen: build.query<ResumenIndicadores, void>({
      query: () => 'resumen',
      responseSchema: resumenIndicadoresSchema,
    }),
    obtenerTendencia: build.query<TendenciaIndicadores, void>({
      query: () => 'tendencia',
      responseSchema: tendenciaIndicadoresSchema,
    }),
  }),
});

export const { useObtenerResumenQuery, useObtenerTendenciaQuery } = apiIndicadores;
