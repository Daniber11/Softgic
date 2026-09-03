// =============================================================================
//  Arranque standalone del remoto.
//
//  En modo standalone el remoto es duenio de su propio ThemeProvider. Cuando lo
//  consume el shell, NO se ejecuta este archivo: el host aporta el tema y el
//  remoto solo entrega IndicadoresApp. Esa es la razon por la que el tema vive
//  aqui y no dentro del componente expuesto.
// =============================================================================

import * as React from 'react';
import { createRoot } from 'react-dom/client';
import { Container, CssBaseline, ThemeProvider, createTheme } from '@mui/material';

import IndicadoresApp from './IndicadoresApp';

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

createRoot(contenedor).render(
  <React.StrictMode>
    <ThemeProvider theme={temaStandalone}>
      <CssBaseline />
      <Container sx={{ py: 4 }}>
        <IndicadoresApp modo="standalone" />
      </Container>
    </ThemeProvider>
  </React.StrictMode>,
);
