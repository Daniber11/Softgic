// =============================================================================
//  Arranque del shell.
//
//  El host es duenio del tema, del store de Redux y de la sesion OIDC. El
//  remoto, cuando se consume por federacion, no monta ninguno de los tres:
//  hereda el tema (Emotion singleton, fase 2) y la sesion (authBridge).
// =============================================================================

import * as React from 'react';
import { createRoot } from 'react-dom/client';
import { CssBaseline, ThemeProvider } from '@mui/material';
import { Provider } from 'react-redux';
import { BrowserRouter } from 'react-router-dom';
import { temaSolicitudes } from '@shared/tema';

import App from './App';
import { store } from './store/store';

const ID_RAIZ = 'raiz-shell';

// El tema vive en `shared` y lo comparten host y remoto: dos createTheme
// distintos producirian dos escalas de color y sombra, y la diferencia se
// nota justo en la frontera federada.
const temaDelHost = temaSolicitudes;

const contenedor = document.getElementById(ID_RAIZ);
if (contenedor === null) {
  throw new Error(`No se encontro el elemento raiz #${ID_RAIZ} en el documento.`);
}

createRoot(contenedor).render(
  <React.StrictMode>
    <Provider store={store}>
      <BrowserRouter>
        <ThemeProvider theme={temaDelHost}>
          <CssBaseline />
          <App />
        </ThemeProvider>
      </BrowserRouter>
    </Provider>
  </React.StrictMode>,
);
