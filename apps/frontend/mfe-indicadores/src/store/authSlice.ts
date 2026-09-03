import { createSlice, type PayloadAction } from '@reduxjs/toolkit';

/**
 * Mismo patron que shell/src/store/authSlice.ts: un espejo de un solo campo
 * para que RTK Query pueda leer el token de forma sincrona en
 * `prepareHeaders`. La fuente de verdad de la sesion es `auth/useSesion.ts`.
 */
export interface EstadoAuth {
  readonly token: string | null;
}

const estadoInicial: EstadoAuth = { token: null };

const authSlice = createSlice({
  name: 'auth',
  initialState: estadoInicial,
  reducers: {
    tokenActualizado(estado, accion: PayloadAction<string | null>) {
      estado.token = accion.payload;
    },
  },
});

export const { tokenActualizado } = authSlice.actions;
export const authReducer = authSlice.reducer;

export function seleccionarToken(estado: { auth: EstadoAuth }): string | null {
  return estado.auth.token;
}
