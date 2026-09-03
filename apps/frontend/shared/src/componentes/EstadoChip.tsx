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
/**
 * Estilo "soft": fondo tenue del color + texto y borde del mismo tono, en
 * vez del relleno saturado por defecto de MUI. Con varias filas de tabla, los
 * chips solidos compiten entre si y con el resto del contenido; el tono suave
 * mantiene el estado legible sin que la tabla parezca un semaforo.
 */
const estiloSuave = (color: string) => ({
  color,
  backgroundColor: `color-mix(in srgb, ${color} 12%, transparent)`,
  border: `1px solid color-mix(in srgb, ${color} 32%, transparent)`,
});

const TONO_ESTADO: Record<EstadoSolicitud, string> = {
  REGISTRADA: '#0B4F9E',
  EN_ATENCION: '#B45309',
  RESUELTA: '#0F9D58',
  CERRADA: '#475569',
};

const TONO_PRIORIDAD: Record<Prioridad, string> = {
  BAJA: '#475569',
  MEDIA: '#B45309',
  ALTA: '#C2354B',
};

export function EstadoChip(props: EstadoChipProps): React.JSX.Element {
  if (props.tipo === 'estado') {
    return (
      <Chip
        label={ETIQUETA_ESTADO[props.valor]}
        color={COLOR_POR_ESTADO[props.valor]}
        size="small"
        sx={estiloSuave(TONO_ESTADO[props.valor])}
      />
    );
  }

  return (
    <Chip
      label={ETIQUETA_PRIORIDAD[props.valor]}
      color={COLOR_POR_PRIORIDAD[props.valor]}
      size="small"
      variant="outlined"
      sx={estiloSuave(TONO_PRIORIDAD[props.valor])}
    />
  );
}
