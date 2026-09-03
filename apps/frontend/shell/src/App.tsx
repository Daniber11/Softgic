import * as React from 'react';
import { Navigate, Route, Routes } from 'react-router-dom';
import { CircularProgress, Stack, Typography } from '@mui/material';
import { AuthProvider } from './auth/AuthProvider';
import { CallbackPage } from './auth/CallbackPage';
import { RutaProtegida } from './auth/RutaProtegida';
import { LimiteDeError } from './LimiteDeError';
import { Layout } from './componentes/Layout';
import { BandejaPage } from './paginas/BandejaPage';
import { CrearSolicitudPage } from './paginas/CrearSolicitudPage';
import { DetalleSolicitudPage } from './paginas/DetalleSolicitudPage';

// La carga diferida es obligatoria, no una optimizacion: el modulo remoto no
// existe hasta que se descarga remoteEntry.js desde el otro origen.
const IndicadoresApp = React.lazy(() => import('mfeIndicadores/IndicadoresApp'));

/**
 * Reintenta la carga del remoto recargando la pagina.
 *
 * Documentado en la fase 2: el fallo de un remoto se memoiza en tres capas
 * independientes (React.lazy, el runtime de Module Federation, y el module
 * cache del bundler) y solo una recarga completa las limpia todas. Se
 * conserva la misma estrategia aqui.
 */
function reintentarCargaDelRemoto(): void {
  window.location.reload();
}

function VistaIndicadores(): React.JSX.Element {
  return (
    <LimiteDeError nombreRemoto="mfeIndicadores" onReintentar={reintentarCargaDelRemoto}>
      <React.Suspense
        fallback={
          <Stack direction="row" spacing={2} alignItems="center">
            <CircularProgress size={24} />
            <Typography>Cargando el microfrontend de indicadores…</Typography>
          </Stack>
        }
      >
        <IndicadoresApp modo="federado" />
      </React.Suspense>
    </LimiteDeError>
  );
}

export default function App(): React.JSX.Element {
  return (
    <AuthProvider>
      <Routes>
        <Route path="/callback" element={<CallbackPage />} />

        <Route element={<Layout />}>
          <Route
            path="/"
            element={
              <RutaProtegida>
                <BandejaPage />
              </RutaProtegida>
            }
          />
          <Route
            path="/solicitudes/nueva"
            element={
              <RutaProtegida rolesPermitidos={['SOLICITANTE']}>
                <CrearSolicitudPage />
              </RutaProtegida>
            }
          />
          <Route
            path="/solicitudes/:id"
            element={
              <RutaProtegida>
                <DetalleSolicitudPage />
              </RutaProtegida>
            }
          />
          <Route
            path="/indicadores"
            element={
              <RutaProtegida rolesPermitidos={['ANALISTA', 'SUPERVISOR']}>
                <VistaIndicadores />
              </RutaProtegida>
            }
          />
        </Route>

        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </AuthProvider>
  );
}
