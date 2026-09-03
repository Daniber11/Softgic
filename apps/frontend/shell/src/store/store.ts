import { configureStore } from '@reduxjs/toolkit';
import { setupListeners } from '@reduxjs/toolkit/query';
import { authReducer } from './authSlice';
import { apiSolicitudes } from './apiSolicitudes';

export const store = configureStore({
  reducer: {
    auth: authReducer,
    [apiSolicitudes.reducerPath]: apiSolicitudes.reducer,
  },
  middleware: (obtenerDefault) => obtenerDefault().concat(apiSolicitudes.middleware),
});

// Habilita refetchOnFocus/refetchOnReconnect: si el usuario vuelve a la
// pestana tras un rato, RTK Query revalida en lugar de mostrar datos
// potencialmente obsoletos indefinidamente.
setupListeners(store.dispatch);

export type RootState = ReturnType<typeof store.getState>;
export type AppDispatch = typeof store.dispatch;
