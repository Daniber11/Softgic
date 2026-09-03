import { Stack } from '@mui/material';
import type { Meta, StoryObj } from '@storybook/react-webpack5';
import { EstadoChip } from './EstadoChip';

const meta: Meta<typeof EstadoChip> = {
  title: 'Compartido/EstadoChip',
  component: EstadoChip,
};

export default meta;
type Story = StoryObj<typeof EstadoChip>;

/** Los cuatro estados posibles de una solicitud, cada uno con su color. */
export const TodosLosEstados: Story = {
  render: () => (
    <Stack direction="row" spacing={1}>
      <EstadoChip tipo="estado" valor="REGISTRADA" />
      <EstadoChip tipo="estado" valor="EN_ATENCION" />
      <EstadoChip tipo="estado" valor="RESUELTA" />
      <EstadoChip tipo="estado" valor="CERRADA" />
    </Stack>
  ),
};

/** Las tres prioridades. BAJA es neutra, ALTA se acerca al color de error. */
export const TodasLasPrioridades: Story = {
  render: () => (
    <Stack direction="row" spacing={1}>
      <EstadoChip tipo="prioridad" valor="BAJA" />
      <EstadoChip tipo="prioridad" valor="MEDIA" />
      <EstadoChip tipo="prioridad" valor="ALTA" />
    </Stack>
  ),
};

export const EstadoIndividual: Story = {
  args: { tipo: 'estado', valor: 'EN_ATENCION' },
};
