import * as React from 'react';
import { Provider, useDispatch } from 'react-redux';
import {
  Button,
  Grid,
  Paper,
  Stack,
  Typography,
} from '@mui/material';
import { EstadoVista } from '@shared/componentes/EstadoVista';
import { useSesion } from './auth/useSesion';
import { BarraSimple } from './componentes/BarraSimple';
import { store } from './store/store';
import { tokenActualizado } from './store/authSlice';
import { useObtenerResumenQuery, useObtenerTendenciaQuery } from './store/apiIndicadores';

interface IndicadoresAppProps {
  /** Modo en el que se monta el remoto. Determina la fuente de la sesion. */
  readonly modo: 'federado' | 'standalone';
}

const ROLES_CON_ACCESO = ['ANALISTA', 'SUPERVISOR'] as const;

/**
 * Vista analitica: resumen por estado y categoria, tiempo medio de atencion,
 * y tendencia diaria de registros.
 *
 * Es el punto de entrada federado, y trae su propio `<Provider>` de Redux
 * encapsulado (ver store/store.ts): el shell no sabe ni necesita saber que
 * este remoto usa Redux por dentro.
 */
export default function IndicadoresApp({ modo }: IndicadoresAppProps): React.JSX.Element {
  return (
    <Provider store={store}>
      <IndicadoresAppInterno modo={modo} />
    </Provider>
  );
}

function IndicadoresAppInterno({ modo }: IndicadoresAppProps): React.JSX.Element {
  const sesion = useSesion(modo);
  const dispatch = useDispatch();
  // Defecto real, verificado en el navegador: sin esta sincronizacion, el
  // primer GET a /resumen salia sin cabecera Authorization y el servidor
  // respondia 401 pese a existir una sesion valida (el boton "Reintentar"
  // si funcionaba, confirmando que era una carrera del primer intento, no
  // un problema del token en si).
  //
  // React ejecuta los efectos de los hijos ANTES que los del padre en el
  // montaje inicial: el efecto de ContenidoIndicadores que dispara las
  // consultas de RTK Query corria antes que este
  // `dispatch(tokenActualizado(...))`. Un primer intento con una bandera
  // "ya se desapcho una vez" no bastaba: en modo estricto, React invoca
  // este efecto dos veces, y la primera invocacion podia dispararse
  // mientras `sesion.token` TODAVIA era null (antes de que useSesion
  // resolviera), dejando la bandera en true de forma prematura. Cuando el
  // token real llegaba despues, ContenidoIndicadores ya tenia luz verde
  // para montar sin esperar el nuevo despacho.
  //
  // La comparacion de valores, en cambio, se reevalua cada vez que
  // `sesion.token` cambia: solo se considera sincronizado cuando lo
  // ULTIMO despachado coincide con lo ACTUAL, sin importar cuantas veces
  // se dispare el efecto ni en que momento.
  // undefined de arranque, distinto de cualquier string y de null, para que
  // la comparacion de abajo nunca coincida por accidente antes del primer
  // despacho real.
  const [tokenDespachado, setTokenDespachado] = React.useState<string | null | undefined>(undefined);

  React.useEffect(() => {
    dispatch(tokenActualizado(sesion.token));
    setTokenDespachado(sesion.token);
  }, [dispatch, sesion.token]);

  const tokenSincronizado = tokenDespachado === sesion.token;

  if (sesion.estado === 'verificando' || !tokenSincronizado) {
    return <EstadoVista estado="cargando" etiqueta="Verificando sesión…" />;
  }

  if (sesion.estado === 'error') {
    return (
      <EstadoVista
        estado="error"
        mensaje={sesion.errorMensaje ?? 'No se pudo verificar la sesión.'}
        onReintentar={() => window.location.reload()}
      />
    );
  }

  if (sesion.estado === 'anonimo') {
    if (modo === 'standalone') {
      return (
        <Stack alignItems="center" spacing={2} sx={{ py: 6 }}>
          <Typography>Inicie sesión para ver los indicadores.</Typography>
          <Button variant="contained" onClick={sesion.iniciarSesion}>
            Iniciar sesión
          </Button>
        </Stack>
      );
    }

    // En modo federado esto no deberia ocurrir: RutaProtegida en el shell ya
    // exige sesion antes de montar este remoto. Si ocurre de todas formas
    // -por ejemplo, el authBridge del shell no encontro un usuario cuando
    // este componente pregunto-, mostrar el mismo texto que el estado de
    // "cargando" fue un defecto real: un spinner que nunca avanza es
    // indistinguible de uno que esta genuinamente colgado. Aqui se usa
    // "error", que es distinguible y ofrece un reintento real.
    return (
      <EstadoVista
        estado="error"
        mensaje="No se encontro una sesion activa. Esto no deberia ocurrir estando dentro del shell: intente recargar."
        onReintentar={() => window.location.reload()}
      />
    );
  }

  if (!ROLES_CON_ACCESO.some((r) => sesion.roles.includes(r))) {
    return <EstadoVista estado="sinAutorizacion" mensaje="Solo analistas y supervisores ven esta vista." />;
  }

  return <ContenidoIndicadores />;
}

function ContenidoIndicadores(): React.JSX.Element {
  const resumen = useObtenerResumenQuery();
  const tendencia = useObtenerTendenciaQuery();

  if (resumen.isLoading || tendencia.isLoading) {
    return <EstadoVista estado="cargando" etiqueta="Cargando indicadores…" />;
  }

  if (resumen.isError || tendencia.isError) {
    return (
      <EstadoVista
        estado="error"
        mensaje="No se pudieron cargar los indicadores."
        onReintentar={() => {
          void resumen.refetch();
          void tendencia.refetch();
        }}
      />
    );
  }

  if (!resumen.data || !tendencia.data) {
    return <EstadoVista estado="cargando" etiqueta="Cargando indicadores…" />;
  }

  const entradasEstado = Object.entries(resumen.data.porEstado);
  const entradasCategoria = Object.entries(resumen.data.porCategoria);
  const entradasDia = Object.entries(tendencia.data.porDia).sort(([a], [b]) => a.localeCompare(b));

  const totalGeneral =
    entradasEstado.reduce((acc, [, v]) => acc + v, 0) + entradasDia.reduce((acc, [, v]) => acc + v, 0);

  if (totalGeneral === 0) {
    return (
      <EstadoVista
        estado="vacio"
        titulo="Todavía no hay datos"
        descripcion="Los indicadores aparecerán cuando existan solicitudes registradas."
      />
    );
  }

  const maximoEstado = Math.max(1, ...entradasEstado.map(([, v]) => v));
  const maximoCategoria = Math.max(1, ...entradasCategoria.map(([, v]) => v));
  const maximoDia = Math.max(1, ...entradasDia.map(([, v]) => v));

  return (
    <EstadoVista estado="listo">
      <Stack spacing={3}>
        <Typography variant="h5" component="h2">
          Resumen analítico
        </Typography>

        <Grid container spacing={3}>
          <Grid size={{ xs: 12, md: 6 }}>
            <Paper sx={{ p: 3, height: '100%' }}>
              <Typography variant="h6" component="h3" gutterBottom>
                Por estado
              </Typography>
              <Stack spacing={1.5}>
                {entradasEstado.map(([estado, valor]) => (
                  <BarraSimple key={estado} etiqueta={estado} valor={valor} maximo={maximoEstado} />
                ))}
              </Stack>
            </Paper>
          </Grid>

          <Grid size={{ xs: 12, md: 6 }}>
            <Paper sx={{ p: 3, height: '100%' }}>
              <Typography variant="h6" component="h3" gutterBottom>
                Por categoría
              </Typography>
              <Stack spacing={1.5}>
                {entradasCategoria.map(([categoria, valor]) => (
                  <BarraSimple key={categoria} etiqueta={categoria} valor={valor} maximo={maximoCategoria} />
                ))}
              </Stack>
            </Paper>
          </Grid>

          <Grid size={12}>
            <Paper sx={{ p: 3, height: '100%' }}>
              <Typography variant="h6" component="h3" gutterBottom>
                Tendencia diaria de registros
              </Typography>
              <Stack spacing={1.5}>
                {entradasDia.map(([dia, valor]) => (
                  <BarraSimple key={dia} etiqueta={dia} valor={valor} maximo={maximoDia} />
                ))}
              </Stack>
            </Paper>
          </Grid>

          <Grid size={12}>
            {/* Metrica destacada: es un solo numero, y como parrafo con un
                divisor huerfano encima quedaba como una nota al pie en vez
                del indicador de negocio que realmente es. */}
            <Paper
              sx={{
                p: 3,
                height: '100%',
                backgroundImage: 'linear-gradient(135deg, #0B4F9E 0%, #083A75 100%)',
                color: '#FFFFFF',
              }}
            >
              <Typography
                variant="subtitle2"
                sx={{ color: 'rgba(255,255,255,0.78)', letterSpacing: '0.06em', textTransform: 'uppercase' }}
              >
                Tiempo medio hasta resolución
              </Typography>
              <Stack direction="row" alignItems="baseline" spacing={1} sx={{ mt: 1 }}>
                <Typography sx={{ fontSize: '2.6rem', fontWeight: 700, lineHeight: 1 }}>
                  {resumen.data.promedioMinutosHastaResolucion}
                </Typography>
                <Typography sx={{ color: 'rgba(255,255,255,0.78)', fontWeight: 500 }}>
                  minutos
                </Typography>
              </Stack>
            </Paper>
          </Grid>
        </Grid>
      </Stack>
    </EstadoVista>
  );
}
