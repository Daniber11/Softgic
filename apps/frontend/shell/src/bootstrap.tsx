// =============================================================================
//  Arranque del shell.
//
//  El host es duenio del tema, del store de Redux y de la sesion OIDC. El
//  remoto, cuando se consume por federacion, no monta ninguno de los tres:
//  hereda el tema (Emotion singleton, fase 2) y la sesion (authBridge).
// =============================================================================

import * as React from 'react';
import { createRoot } from 'react-dom/client';
import { CssBaseline, ThemeProvider, createTheme } from '@mui/material';
import { Provider } from 'react-redux';
import { BrowserRouter } from 'react-router-dom';

import App from './App';
import { store } from './store/store';

const ID_RAIZ = 'raiz-shell';

// Azul institucional del host. El remoto en modo standalone usa morado; si el
// remoto federado apareciera morado, habria dos instancias de Emotion.
const temaDelHost = createTheme({
  palette: {
    primary: { main: '#1565c0' },
  },
});

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
