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

  const etiquetaRoles = auth.roles.map((r) => ETIQUETA_ROL[r]).join(', ');

  return (
    // minHeight 100dvh + fondo del tema: sin esto, en pantallas con poco
    // contenido el area bajo el contenido queda blanca y la pagina parece
    // cortada a la mitad.
    <Box sx={{ minHeight: '100dvh', bgcolor: 'background.default' }}>
      <AppBar position="sticky" elevation={0}>
        <Toolbar sx={{ gap: 2 }}>
          <Stack sx={{ flexGrow: 1, minWidth: 0 }}>
            <Typography variant="h6" component="h1" sx={{ lineHeight: 1.2 }}>
              Solicitudes Operacionales
            </Typography>
            <Typography variant="caption" sx={{ color: 'rgba(255,255,255,0.72)' }}>
              Plataforma de gestión y trazabilidad
            </Typography>
          </Stack>

          <Stack direction="row" spacing={1.5} alignItems="center">
            {auth.roles.length > 0 ? (
              // El rol como "pastilla" y no como texto suelto: es un dato de
              // identidad, y suelto sobre la barra se leia como una etiqueta
              // decorativa mas.
              <Box
                aria-label={`Rol actual: ${etiquetaRoles}`}
                sx={{
                  px: 1.5,
                  py: 0.5,
                  borderRadius: 999,
                  bgcolor: 'rgba(255,255,255,0.14)',
                  border: '1px solid rgba(255,255,255,0.22)',
                  fontSize: '0.8rem',
                  fontWeight: 600,
                  whiteSpace: 'nowrap',
                }}
              >
                {etiquetaRoles}
              </Box>
            ) : null}
            <Button
              color="inherit"
              onClick={() => void auth.cerrarSesion()}
              sx={{
                borderRadius: 999,
                border: '1px solid rgba(255,255,255,0.28)',
                '&:hover': { bgcolor: 'rgba(255,255,255,0.12)' },
              }}
            >
              Cerrar sesión
            </Button>
          </Stack>
        </Toolbar>

        <Tabs
          value={valorActivo}
          textColor="inherit"
          aria-label="Navegación principal"
          sx={{
            px: 1,
            borderTop: '1px solid rgba(255,255,255,0.14)',
            '& .MuiTabs-indicator': { height: 3, borderRadius: '3px 3px 0 0', bgcolor: '#7FD1C1' },
          }}
        >
          {pestaniasVisibles.map((p) => (
            <Tab key={p.ruta} label={p.etiqueta} value={p.ruta} component={RouterLink} to={p.ruta} />
          ))}
        </Tabs>
      </AppBar>

      {/* maxWidth lg: con el ancho por defecto la tabla de la bandeja quedaba
          estirada y las columnas de fecha se separaban del resto. */}
      <Container component="main" maxWidth="lg" sx={{ py: { xs: 3, md: 5 } }}>
        <Outlet />
      </Container>
    </Box>
  );
}
