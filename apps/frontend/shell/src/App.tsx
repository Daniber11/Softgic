// =============================================================================
//  App del shell.
//
//  Fase 2: solo demuestra que el host monta el remoto. El enrutamiento, la
//  sesion OIDC y las vistas reales llegan en la fase 5.
// =============================================================================

import * as React from 'react';
import { AppBar, Box, CircularProgress, Container, Divider, Stack, Toolbar, Typography } from '@mui/material';

import { LimiteDeError } from './LimiteDeError';

// La carga diferida es obligatoria, no una optimizacion: el modulo remoto no
// existe hasta que se descarga remoteEntry.js desde el otro origen.
const IndicadoresApp = React.lazy(() => import('mfeIndicadores/IndicadoresApp'));

/**
 * Reintenta la carga del remoto recargando la pagina.
 *
 * SE PROBO UN REINTENTO EN CALIENTE Y NO ES FIABLE. Cuando la carga de un
 * remoto falla, el fallo queda memoizado en tres niveles independientes:
 *
 *   1. React.lazy cachea el rechazo de su factoria.
 *   2. El runtime de Module Federation cachea la entrada del remoto.
 *   3. El module cache del bundler cachea la factoria del modulo fallido.
 *
 * Se resolvieron los dos primeros —creando un React.lazy nuevo por cada intento
 * y llamando a registerRemotes con force: true— y el tercero siguio devolviendo
 * el modulo invalido: React fallaba con el error #306, "element type is
 * invalid", que ademas no dice nada sobre la causa. Recargar es la unica forma
 * de limpiar los tres, y en la fase 2 no hay estado de aplicacion que perder.
 *
 * Queda anotado para la fase 5: si mas adelante se quiere conservar el estado,
 * la salida no es insistir por aqui, sino montar el remoto en una ruta propia y
 * recargar solo esa ruta.
 */
function reintentarCargaDelRemoto(): void {
  window.location.reload();
}

export default function App(): React.JSX.Element {
  return (
    <Box>
      <AppBar position="static">
        <Toolbar>
          <Typography variant="h6" component="h1">
            Solicitudes Operacionales — Shell
          </Typography>
        </Toolbar>
      </AppBar>

      <Container sx={{ py: 4 }}>
        <Stack spacing={3}>
          <Box>
            <Typography variant="h6" component="h2" gutterBottom>
              Contenido propio del host
            </Typography>
            <Typography color="text.secondary">
              Este bloque lo renderiza el shell. El de abajo llega por federacion desde
              otro origen y otro proceso de build.
            </Typography>
          </Box>

          <Divider />

          <LimiteDeError
            nombreRemoto="mfeIndicadores"
            onReintentar={reintentarCargaDelRemoto}
          >
            <React.Suspense
              fallback={
                <Stack direction="row" spacing={2} alignItems="center" data-testid="cargando-remoto">
                  <CircularProgress size={24} />
                  <Typography>Cargando el microfrontend de indicadores…</Typography>
                </Stack>
              }
            >
              <IndicadoresApp modo="federado" />
            </React.Suspense>
          </LimiteDeError>
        </Stack>
      </Container>
    </Box>
  );
}
