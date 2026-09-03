// =============================================================================
//  Arranque del shell.
//
//  El host es duenio del tema. El remoto, cuando se consume por federacion, NO
//  monta su propio ThemeProvider: hereda este. Ese es el contrato que prueba
//  esta fase, porque solo se cumple si Emotion resuelve una unica instancia.
// =============================================================================

import * as React from 'react';
import { createRoot } from 'react-dom/client';
import { CssBaseline, ThemeProvider, createTheme } from '@mui/material';

import App from './App';

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
    <ThemeProvider theme={temaDelHost}>
      <CssBaseline />
      <App />
    </ThemeProvider>
  </React.StrictMode>,
);
