import * as React from 'react';
import { Box, Button, CircularProgress, Stack, Typography } from '@mui/material';
import ReplayIcon from '@mui/icons-material/Replay';
import LockOutlinedIcon from '@mui/icons-material/LockOutlined';
import InboxOutlinedIcon from '@mui/icons-material/InboxOutlined';

type EstadoVistaProps =
  | { estado: 'cargando'; etiqueta?: string }
  | { estado: 'vacio'; titulo?: string; descripcion?: string }
  | { estado: 'error'; mensaje: string; onReintentar: () => void }
  | { estado: 'sinAutorizacion'; mensaje?: string }
  | { estado: 'listo'; children: React.ReactNode };

/**
 * Envuelve los cuatro estados que CLAUDE.md exige en cada vista: cargando,
 * vacio, error con reintento, y autorizacion insuficiente. Ninguna vista de
 * este proyecto dibuja su propio spinner o su propio mensaje de error suelto;
 * todas pasan por aqui, que es lo que garantiza que se vean y se comporten
 * igual en toda la aplicacion.
 *
 * Accesibilidad: 'cargando' anuncia con `role="status"` (aria-live polite,
 * no interrumpe); 'error' y 'sinAutorizacion' usan `role="alert"`
 * (aria-live assertive, si interrumpe, porque son condiciones que bloquean
 * la tarea del usuario). 'listo' no envuelve nada: el contenido real no debe
 * cargar con una capa extra de accesibilidad que no le corresponde.
 *
 * Documentado en Storybook con sus cuatro estados representativos.
 */
export function EstadoVista(props: EstadoVistaProps): React.JSX.Element {
  switch (props.estado) {
    case 'cargando':
      return (
        <Stack
          role="status"
          direction="row"
          spacing={2}
          alignItems="center"
          justifyContent="center"
          sx={{ py: 6 }}
        >
          <CircularProgress size={28} aria-hidden="true" />
          <Typography color="text.secondary">{props.etiqueta ?? 'Cargando…'}</Typography>
        </Stack>
      );

    case 'vacio':
      return (
        <Stack alignItems="center" spacing={1} sx={{ py: 6, textAlign: 'center' }}>
          <InboxOutlinedIcon fontSize="large" color="disabled" aria-hidden="true" />
          <Typography variant="subtitle1">{props.titulo ?? 'No hay nada aquí todavía'}</Typography>
          {props.descripcion ? (
            <Typography color="text.secondary" variant="body2">
              {props.descripcion}
            </Typography>
          ) : null}
        </Stack>
      );

    case 'error':
      return (
        <Stack role="alert" alignItems="center" spacing={2} sx={{ py: 6, textAlign: 'center' }}>
          <Typography color="error.main" variant="subtitle1">
            {props.mensaje}
          </Typography>
          <Button
            variant="outlined"
            color="error"
            startIcon={<ReplayIcon />}
            onClick={props.onReintentar}
          >
            Reintentar
          </Button>
        </Stack>
      );

    case 'sinAutorizacion':
      return (
        <Stack role="alert" alignItems="center" spacing={1} sx={{ py: 6, textAlign: 'center' }}>
          <LockOutlinedIcon fontSize="large" color="disabled" aria-hidden="true" />
          <Typography variant="subtitle1">Autorización insuficiente</Typography>
          <Typography color="text.secondary" variant="body2">
            {props.mensaje ?? 'Su rol no permite ver este contenido.'}
          </Typography>
        </Stack>
      );

    case 'listo':
      return <Box>{props.children}</Box>;
  }
}
