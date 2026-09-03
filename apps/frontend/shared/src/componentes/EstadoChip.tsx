import * as React from 'react';
import { Chip } from '@mui/material';
import type { ChipProps } from '@mui/material';
import {
  ETIQUETA_ESTADO,
  ETIQUETA_PRIORIDAD,
  type EstadoSolicitud,
  type Prioridad,
} from '../dominio/tipos';

const COLOR_POR_ESTADO: Record<EstadoSolicitud, ChipProps['color']> = {
  REGISTRADA: 'info',
  EN_ATENCION: 'warning',
  RESUELTA: 'success',
  CERRADA: 'default',
};

// BAJA es neutra, ALTA se acerca visualmente al color de error: mayor
// prioridad, mayor urgencia percibida.
const COLOR_POR_PRIORIDAD: Record<Prioridad, ChipProps['color']> = {
  BAJA: 'default',
  MEDIA: 'warning',
  ALTA: 'error',
};

type EstadoChipProps =
  | { tipo: 'estado'; valor: EstadoSolicitud }
  | { tipo: 'prioridad'; valor: Prioridad };

/**
 * Chip de color para un estado o una prioridad.
 *
 * El color nunca es el unico portador del significado: el texto de la
 * etiqueta (Registrada, En atención...) ya lo dice, de modo que alguien con
 * dificultad para distinguir colores no pierde informacion.
 *
 * Documentado en Storybook con los cuatro estados y las tres prioridades.
 */
export function EstadoChip(props: EstadoChipProps): React.JSX.Element {
  if (props.tipo === 'estado') {
    return (
      <Chip
        label={ETIQUETA_ESTADO[props.valor]}
        color={COLOR_POR_ESTADO[props.valor]}
        size="small"
      />
    );
  }

  return (
    <Chip
      label={ETIQUETA_PRIORIDAD[props.valor]}
      color={COLOR_POR_PRIORIDAD[props.valor]}
      size="small"
      variant="outlined"
    />
  );
}
