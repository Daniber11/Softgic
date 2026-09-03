import { createSlice, type PayloadAction } from '@reduxjs/toolkit';

/**
 * Copia del token dentro de Redux, exclusivamente para que RTK Query pueda
 * leerlo de forma sincrona en `prepareHeaders` (que recibe `getState()`, no
 * el contexto de React). `AuthProvider` sigue siendo la fuente de verdad de
 * la sesion para el resto de la interfaz; este slice es un espejo de un solo
 * campo, no una segunda copia de la logica de autenticacion.
 *
 * El token vive en memoria de Redux igual que en oidc-client-ts: nada aqui
 * pasa por localStorage ni sessionStorage.
 */
interface EstadoAuth {
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
