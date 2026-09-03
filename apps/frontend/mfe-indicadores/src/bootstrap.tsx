// =============================================================================
//  Arranque standalone del remoto.
//
//  En modo standalone el remoto es duenio de su propio ThemeProvider Y de su
//  propia sesion OIDC (userManagerStandalone). Cuando lo consume el shell, NO
//  se ejecuta este archivo: el host aporta el tema y la sesion, y el remoto
//  solo entrega IndicadoresApp.
// =============================================================================

import * as React from 'react';
import { createRoot } from 'react-dom/client';
import { Container, CssBaseline, ThemeProvider, createTheme } from '@mui/material';

import IndicadoresApp from './IndicadoresApp';
import { userManagerStandalone } from './auth/authServiceStandalone';
import { EstadoVista } from '@shared/componentes/EstadoVista';

const ID_RAIZ = 'raiz-indicadores';

// Color deliberadamente distinto al del shell: si el remoto federado se pintara
// con este morado en lugar del azul del host, seria la prueba visual de que hay
// dos instancias de Emotion compitiendo.
const temaStandalone = createTheme({
  palette: {
    primary: { main: '#6a1b9a' },
  },
});

const contenedor = document.getElementById(ID_RAIZ);
if (contenedor === null) {
  throw new Error(`No se encontro el elemento raiz #${ID_RAIZ} en el documento.`);
}

/**
 * Standalone no trae react-router: es una demostracion de un unico remoto,
 * no una aplicacion con varias rutas. Basta distinguir la ruta /callback a
 * mano para completar el intercambio de codigo de PKCE y volver a la raiz.
 */
function RaizStandalone(): React.JSX.Element {
  const [enCallback, setEnCallback] = React.useState(window.location.pathname === '/callback');
  const [errorCallback, setErrorCallback] = React.useState<string | null>(null);

  React.useEffect(() => {
    if (!enCallback) {
      return;
    }
    // signinCallback(), no signinRedirectCallback(): esta misma ruta /callback
    // tambien se carga dentro del iframe oculto del renuevo silencioso
    // (automaticSilentRenew y el signinSilent() de useSesion.ts al montar).
    // Mismo defecto y misma correccion que en shell/src/auth/CallbackPage.tsx.
    userManagerStandalone
      .signinCallback()
      .then(() => {
        window.history.replaceState({}, '', '/');
        setEnCallback(false);
      })
      .catch((error: unknown) => {
        setErrorCallback(error instanceof Error ? error.message : 'No se pudo completar el inicio de sesión.');
      });
  }, [enCallback]);

  if (errorCallback) {
    return <EstadoVista estado="error" mensaje={errorCallback} onReintentar={() => window.location.assign('/')} />;
  }

  if (enCallback) {
    return <EstadoVista estado="cargando" etiqueta="Completando inicio de sesión…" />;
  }

  return <IndicadoresApp modo="standalone" />;
}

createRoot(contenedor).render(
  <React.StrictMode>
    <ThemeProvider theme={temaStandalone}>
      <CssBaseline />
      <Container sx={{ py: 4 }}>
        <RaizStandalone />
      </Container>
    </ThemeProvider>
  </React.StrictMode>,
);
