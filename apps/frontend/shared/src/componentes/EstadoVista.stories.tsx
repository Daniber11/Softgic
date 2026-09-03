import { Paper, Typography } from '@mui/material';
import type { Meta, StoryObj } from '@storybook/react-webpack5';
import { EstadoVista } from './EstadoVista';

const meta: Meta<typeof EstadoVista> = {
  title: 'Compartido/EstadoVista',
  component: EstadoVista,
};

export default meta;
type Story = StoryObj<typeof EstadoVista>;

/**
 * Los cuatro estados que CLAUDE.md exige en cada vista de la aplicacion:
 * cargando, vacio, error con reintento, y autorizacion insuficiente.
 */
export const Cargando: Story = {
  args: { estado: 'cargando', etiqueta: 'Cargando solicitudes…' },
};

export const Vacio: Story = {
  args: {
    estado: 'vacio',
    titulo: 'No hay solicitudes con estos filtros',
    descripcion: 'Pruebe a ajustar o quitar alguno de los filtros.',
  },
};

export const ErrorConReintento: Story = {
  args: {
    estado: 'error',
    mensaje: 'No se pudo contactar al servidor.',
    onReintentar: () => alert('Reintentando…'),
  },
};

export const AutorizacionInsuficiente: Story = {
  args: {
    estado: 'sinAutorizacion',
    mensaje: 'Su rol no permite ver el resumen analítico.',
  },
};

export const Listo: Story = {
  args: {
    estado: 'listo',
    children: (
      <Paper sx={{ p: 2 }}>
        <Typography>Contenido real de la vista, ya cargado.</Typography>
      </Paper>
    ),
  },
};
