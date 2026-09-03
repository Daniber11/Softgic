import * as React from 'react';
import { Link as RouterLink, Outlet, useLocation } from 'react-router-dom';
import {
  AppBar,
  Box,
  Button,
  Container,
  Stack,
  Tab,
  Tabs,
  Toolbar,
  Typography,
} from '@mui/material';
import { ETIQUETA_ROL } from '@shared/dominio/tipos';
import { useAuth } from '../auth/AuthProvider';

interface Pestania {
  readonly ruta: string;
  readonly etiqueta: string;
  readonly visiblePara?: readonly ('SOLICITANTE' | 'ANALISTA' | 'SUPERVISOR')[];
}

const PESTANIAS: readonly Pestania[] = [
  { ruta: '/', etiqueta: 'Bandeja' },
  { ruta: '/solicitudes/nueva', etiqueta: 'Nueva solicitud', visiblePara: ['SOLICITANTE'] },
  { ruta: '/indicadores', etiqueta: 'Indicadores', visiblePara: ['ANALISTA', 'SUPERVISOR'] },
];

/**
 * Marco comun de la aplicacion autenticada: barra superior, navegacion por
 * pestanias filtrada por rol, y el punto donde se monta cada vista via
 * `<Outlet />`.
 *
 * El filtrado de pestanias por rol es usabilidad (no mostrar una opcion que
 * de todas formas terminaria en "autorizacion insuficiente"), no seguridad:
 * la seguridad real vive en el servidor y en RutaProtegida.
 */
export function Layout(): React.JSX.Element {
  const auth = useAuth();
  const location = useLocation();

  const pestaniasVisibles = PESTANIAS.filter(
    (p) => !p.visiblePara || p.visiblePara.some((r) => auth.roles.includes(r)),
  );

  // El Tabs de MUI exige que `value` calce exactamente con uno de sus Tab.
  // Para rutas con parametros (/solicitudes/:id) ninguna pestania calza, y
  // eso es correcto: no hay una pestania de "detalle" que resaltar.
  const valorActivo = pestaniasVisibles.some((p) => p.ruta === location.pathname)
    ? location.pathname
    : false;

  return (
    <Box>
      <AppBar position="static">
        <Toolbar>
          <Typography variant="h6" component="h1" sx={{ flexGrow: 1 }}>
            Solicitudes Operacionales
          </Typography>
          <Stack direction="row" spacing={2} alignItems="center">
            {auth.roles.length > 0 ? (
              <Typography variant="body2" aria-label={`Rol actual: ${auth.roles.map((r) => ETIQUETA_ROL[r]).join(', ')}`}>
                {auth.roles.map((r) => ETIQUETA_ROL[r]).join(', ')}
              </Typography>
            ) : null}
            <Button color="inherit" onClick={() => void auth.cerrarSesion()}>
              Cerrar sesión
            </Button>
          </Stack>
        </Toolbar>
        <Tabs
          value={valorActivo}
          textColor="inherit"
          indicatorColor="secondary"
          aria-label="Navegación principal"
        >
          {pestaniasVisibles.map((p) => (
            <Tab key={p.ruta} label={p.etiqueta} value={p.ruta} component={RouterLink} to={p.ruta} />
          ))}
        </Tabs>
      </AppBar>

      <Container component="main" sx={{ py: 4 }}>
        <Outlet />
      </Container>
    </Box>
  );
}
