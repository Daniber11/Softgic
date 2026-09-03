import * as React from 'react';
import { useParams } from 'react-router-dom';
import {
  Alert,
  Button,
  Divider,
  Grid,
  Paper,
  Stack,
  Typography,
} from '@mui/material';
import { EstadoChip } from '@shared/componentes/EstadoChip';
import { EstadoVista } from '@shared/componentes/EstadoVista';
import { ETIQUETA_ROL } from '@shared/dominio/tipos';
import { DialogoTexto } from '../componentes/DialogoTexto';
import { LineaDeTiempo } from '../componentes/LineaDeTiempo';
import { useAuth } from '../auth/AuthProvider';
import {
  useAgregarObservacionMutation,
  useObtenerSolicitudQuery,
  useTomarSolicitudMutation,
  useTransicionarMutation,
} from '../store/apiSolicitudes';
import { mensajeDeError } from '../store/erroresApi';

type DialogoAbierto = 'ninguno' | 'devolver' | 'observar';

/**
 * Detalle de una solicitud: datos, linea de tiempo, observaciones, y las
 * acciones disponibles segun el rol de quien mira.
 *
 * Que boton se muestra es una prediccion de usabilidad, no una promesa de
 * exito: por ejemplo, "Resolver" se ofrece a cualquier ANALISTA con la
 * solicitud EN_ATENCION, pero si no es el analista que la tomo, el servidor
 * lo rechaza igual y el mensaje de error explica por que. El servidor es
 * quien decide siempre.
 *
 * Este componente tambien es la pieza central del escenario A6: al recargar
 * el navegador en esta ruta, RutaProtegida vuelve a verificar la sesion (sin
 * leer un token de almacenamiento local) y esta consulta vuelve a pedir el
 * detalle al servidor -no hay ningun dato cacheado localmente que sobreviva
 * al reload-.
 */
export function DetalleSolicitudPage(): React.JSX.Element {
  const { id } = useParams<{ id: string }>();
  const auth = useAuth();
  const [dialogo, setDialogo] = React.useState<DialogoAbierto>('ninguno');

  const consulta = useObtenerSolicitudQuery(id ?? '', { skip: !id });
  const [tomar, tomarEstado] = useTomarSolicitudMutation();
  const [transicionar, transicionarEstado] = useTransicionarMutation();
  const [observar, observarEstado] = useAgregarObservacionMutation();

  if (!id) {
    return <EstadoVista estado="error" mensaje="Falta el identificador de la solicitud." onReintentar={() => {}} />;
  }

  if (consulta.isLoading) {
    return <EstadoVista estado="cargando" etiqueta="Cargando solicitud…" />;
  }

  if (consulta.isError && consulta.error) {
    const problema = mensajeDeError(consulta.error);
    const es404 = 'status' in consulta.error && consulta.error.status === 404;
    return es404 ? (
      <EstadoVista estado="vacio" titulo="Solicitud no encontrada" descripcion={problema} />
    ) : (
      <EstadoVista estado="error" mensaje={problema} onReintentar={consulta.refetch} />
    );
  }

  if (!consulta.data) {
    return <EstadoVista estado="cargando" etiqueta="Cargando solicitud…" />;
  }

  const solicitud = consulta.data;
  const puedeTomar = auth.roles.includes('ANALISTA') && solicitud.estado === 'REGISTRADA';
  const puedeResolver = auth.roles.includes('ANALISTA') && solicitud.estado === 'EN_ATENCION';
  const puedeDevolverOCerrar = auth.roles.includes('SUPERVISOR') && solicitud.estado === 'RESUELTA';
  const puedeObservar = auth.roles.includes('ANALISTA') || auth.roles.includes('SUPERVISOR');

  return (
    <Stack spacing={3}>
      <Stack direction="row" justifyContent="space-between" alignItems="flex-start" flexWrap="wrap" spacing={2}>
        <Stack>
          <Typography variant="h5" component="h2">
            {solicitud.codigo} — {solicitud.asunto}
          </Typography>
          <Stack direction="row" spacing={1} sx={{ mt: 1 }}>
            <EstadoChip tipo="estado" valor={solicitud.estado} />
            <EstadoChip tipo="prioridad" valor={solicitud.prioridad} />
          </Stack>
        </Stack>

        <Stack direction="row" spacing={1} flexWrap="wrap">
          {puedeTomar ? (
            <Button variant="contained" disabled={tomarEstado.isLoading} onClick={() => void tomar(id)}>
              Tomar
            </Button>
          ) : null}
          {puedeResolver ? (
            <Button
              variant="contained"
              disabled={transicionarEstado.isLoading}
              onClick={() => void transicionar({ id, accion: 'RESOLVER' })}
            >
              Resolver
            </Button>
          ) : null}
          {puedeDevolverOCerrar ? (
            <>
              <Button variant="outlined" onClick={() => setDialogo('devolver')}>
                Devolver
              </Button>
              <Button
                variant="contained"
                color="success"
                disabled={transicionarEstado.isLoading}
                onClick={() => void transicionar({ id, accion: 'CERRAR' })}
              >
                Cerrar
              </Button>
            </>
          ) : null}
          {puedeObservar ? (
            <Button variant="outlined" onClick={() => setDialogo('observar')}>
              Agregar observación
            </Button>
          ) : null}
        </Stack>
      </Stack>

      {tomarEstado.isError ? <Alert severity="error">{mensajeDeError(tomarEstado.error)}</Alert> : null}
      {transicionarEstado.isError ? (
        <Alert severity="error">{mensajeDeError(transicionarEstado.error)}</Alert>
      ) : null}

      <Grid container spacing={3}>
        <Grid size={{ xs: 12, md: 6 }}>
          <Paper sx={{ p: 3, height: '100%' }}>
            <Typography variant="h6" component="h3" gutterBottom>
              Detalle
            </Typography>
            <Typography variant="body1" sx={{ whiteSpace: 'pre-wrap', mb: 2 }}>
              {solicitud.descripcion}
            </Typography>
            <Divider sx={{ my: 2 }} />
            <Typography variant="subtitle2" gutterBottom>
              Observaciones
            </Typography>
            {solicitud.observaciones && solicitud.observaciones.length > 0 ? (
              <Stack spacing={1.5}>
                {solicitud.observaciones.map((o) => (
                  <Paper key={o.id} variant="outlined" sx={{ p: 1.5 }}>
                    <Typography variant="body2">{o.texto}</Typography>
                    <Typography variant="caption" color="text.secondary">
                      {ETIQUETA_ROL[o.actorRol]} · {new Date(o.ocurridoEn).toLocaleString('es-CO')}
                    </Typography>
                  </Paper>
                ))}
              </Stack>
            ) : (
              <Typography variant="body2" color="text.secondary">
                Sin observaciones todavía.
              </Typography>
            )}
          </Paper>
        </Grid>

        <Grid size={{ xs: 12, md: 6 }}>
          <Paper sx={{ p: 3, height: '100%' }}>
            <Typography variant="h6" component="h3" gutterBottom>
              Línea de tiempo
            </Typography>
            <LineaDeTiempo historial={solicitud.historial ?? []} />
          </Paper>
        </Grid>
      </Grid>

      <DialogoTexto
        abierto={dialogo === 'devolver'}
        titulo="Devolver solicitud"
        descripcion="Indique el motivo por el cual se devuelve a atención."
        etiquetaCampo="Motivo"
        etiquetaConfirmar="Devolver"
        enviando={transicionarEstado.isLoading}
        errorMensaje={dialogo === 'devolver' && transicionarEstado.isError ? mensajeDeError(transicionarEstado.error) : null}
        onCerrar={() => setDialogo('ninguno')}
        onConfirmar={(motivo) => {
          void transicionar({ id, accion: 'DEVOLVER', motivo }).unwrap().then(() => setDialogo('ninguno'));
        }}
      />

      <DialogoTexto
        abierto={dialogo === 'observar'}
        titulo="Agregar observación"
        etiquetaCampo="Observación"
        etiquetaConfirmar="Agregar"
        enviando={observarEstado.isLoading}
        errorMensaje={dialogo === 'observar' && observarEstado.isError ? mensajeDeError(observarEstado.error) : null}
        onCerrar={() => setDialogo('ninguno')}
        onConfirmar={(texto) => {
          void observar({ id, texto }).unwrap().then(() => setDialogo('ninguno'));
        }}
      />
    </Stack>
  );
}
