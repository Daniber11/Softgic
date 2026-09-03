import { createApi, fetchBaseQuery } from '@reduxjs/toolkit/query/react';
import {
  categoriasSchema,
  paginaDeSolicitudesSchema,
  problemDetailSchema,
  solicitudSchema,
  type Categoria,
  type PaginaDeSolicitudes,
  type Solicitud,
} from '@shared/esquemas/solicitud';
import type { AccionTransicion, EstadoSolicitud, Prioridad } from '@shared/dominio/tipos';
import { seleccionarToken } from './authSlice';

/**
 * Capa de API del frontend sobre RTK Query (BLUEPRINT 4, Facade).
 *
 * Cada respuesta se valida con `responseSchema` antes de llegar al
 * componente: es la libreria, no un `transformResponse` escrito a mano, la
 * que aplica el esquema Zod (RTK Query 2.x soporta Standard Schema de forma
 * nativa, y Zod 4 lo implementa). Si el backend cambiara la forma de una
 * respuesta sin que este archivo se actualizara, la vista veria un error
 * claro en vez de un `undefined` silencioso en algun campo.
 */

interface FiltrosSolicitudes {
  readonly estado?: EstadoSolicitud;
  readonly categoriaId?: string;
  readonly prioridad?: Prioridad;
  readonly desde?: string;
  readonly hasta?: string;
  readonly page?: number;
  readonly size?: number;
}

interface CrearSolicitudBody {
  readonly asunto: string;
  readonly descripcion: string;
  readonly categoriaId: string;
  readonly prioridad: Prioridad;
}

interface TransicionBody {
  readonly id: string;
  readonly accion: AccionTransicion;
  readonly motivo?: string;
}

interface ObservacionBody {
  readonly id: string;
  readonly texto: string;
}

export const apiSolicitudes = createApi({
  reducerPath: 'apiSolicitudes',
  baseQuery: fetchBaseQuery({
    baseUrl: `${__SOLICITUDES_API_URL__}/api/v1`,
    prepareHeaders: (headers, { getState }) => {
      const token = seleccionarToken(getState() as { auth: { token: string | null } });
      if (token) {
        headers.set('Authorization', `Bearer ${token}`);
      }
      return headers;
    },
  }),
  tagTypes: ['Solicitud', 'Categoria'],

  // Si el esquema no calza -un campo que desaparecio, un tipo que cambio-,
  // esto lo convierte en un error de consulta normal (isError: true) en
  // lugar de una excepcion sin capturar que tumbaria el arbol de React.
  catchSchemaFailure: (error) => ({
    status: 'CUSTOM_ERROR' as const,
    error: `La respuesta del servidor no cumple el contrato esperado (${error.schemaName}).`,
  }),

  endpoints: (build) => ({
    listarSolicitudes: build.query<PaginaDeSolicitudes, FiltrosSolicitudes>({
      query: (filtros) => ({ url: 'solicitudes', params: filtros }),
      responseSchema: paginaDeSolicitudesSchema(),
      providesTags: (resultado) =>
        resultado
          ? [
              ...resultado.content.map((s) => ({ type: 'Solicitud' as const, id: s.id })),
              { type: 'Solicitud' as const, id: 'LISTA' },
            ]
          : [{ type: 'Solicitud' as const, id: 'LISTA' }],
    }),

    obtenerSolicitud: build.query<Solicitud, string>({
      query: (id) => `solicitudes/${id}`,
      responseSchema: solicitudSchema,
      providesTags: (_r, _e, id) => [{ type: 'Solicitud', id }],
    }),

    listarCategorias: build.query<Categoria[], void>({
      query: () => 'categorias',
      responseSchema: categoriasSchema,
      providesTags: [{ type: 'Categoria', id: 'LISTA' }],
    }),

    crearSolicitud: build.mutation<Solicitud, CrearSolicitudBody>({
      query: (cuerpo) => ({
        url: 'solicitudes',
        method: 'POST',
        body: cuerpo,
        // Une la creacion a un identificador de un solo uso: si la peticion
        // se reintenta por un corte de red, el backend devuelve el mismo
        // expediente en lugar de crear uno duplicado (BLUEPRINT 5.4).
        headers: { 'Idempotency-Key': crypto.randomUUID() },
      }),
      responseSchema: solicitudSchema,
      invalidatesTags: [{ type: 'Solicitud', id: 'LISTA' }],
    }),

    tomarSolicitud: build.mutation<Solicitud, string>({
      query: (id) => ({ url: `solicitudes/${id}/asignaciones`, method: 'POST' }),
      responseSchema: solicitudSchema,
      invalidatesTags: (_r, _e, id) => [
        { type: 'Solicitud', id },
        { type: 'Solicitud', id: 'LISTA' },
      ],
    }),

    transicionar: build.mutation<Solicitud, TransicionBody>({
      query: ({ id, accion, motivo }) => ({
        url: `solicitudes/${id}/transiciones`,
        method: 'POST',
        body: { accion, motivo },
      }),
      responseSchema: solicitudSchema,
      invalidatesTags: (_r, _e, { id }) => [
        { type: 'Solicitud', id },
        { type: 'Solicitud', id: 'LISTA' },
      ],
    }),

    agregarObservacion: build.mutation<Solicitud, ObservacionBody>({
      query: ({ id, texto }) => ({
        url: `solicitudes/${id}/observaciones`,
        method: 'POST',
        body: { texto },
      }),
      responseSchema: solicitudSchema,
      invalidatesTags: (_r, _e, { id }) => [{ type: 'Solicitud', id }],
    }),
  }),
});

export const {
  useListarSolicitudesQuery,
  useObtenerSolicitudQuery,
  useListarCategoriasQuery,
  useCrearSolicitudMutation,
  useTomarSolicitudMutation,
  useTransicionarMutation,
  useAgregarObservacionMutation,
} = apiSolicitudes;

export { problemDetailSchema };
