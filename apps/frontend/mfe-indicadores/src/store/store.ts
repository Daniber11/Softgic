import { configureStore } from '@reduxjs/toolkit';
import { setupListeners } from '@reduxjs/toolkit/query';
import { authReducer } from './authSlice';
import { apiIndicadores } from './apiIndicadores';

/**
 * Store propio del remoto, independiente del store del shell.
 *
 * Module Federation comparte la LIBRERIA de Redux como singleton (para que
 * los hooks funcionen), pero no comparte instancias de store entre host y
 * remoto: cada uno tiene el suyo, y este vive completamente encapsulado
 * dentro de `<Provider>` en IndicadoresApp.tsx, invisible para el shell.
 */
export const store = configureStore({
  reducer: {
    auth: authReducer,
    [apiIndicadores.reducerPath]: apiIndicadores.reducer,
  },
  middleware: (obtenerDefault) => obtenerDefault().concat(apiIndicadores.middleware),
});

setupListeners(store.dispatch);
