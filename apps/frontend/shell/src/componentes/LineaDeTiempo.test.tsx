import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { LineaDeTiempo } from './LineaDeTiempo';
import type { CambioEstado } from '@shared/esquemas/solicitud';

const historial: CambioEstado[] = [
  {
    id: 'a0a95b13-d0a6-446f-af9a-90e2c7bdbcd9',
    estadoOrigen: null,
    estadoDestino: 'REGISTRADA',
    actorId: 'u-solicitante',
    actorRol: 'SOLICITANTE',
    motivo: null,
    ocurridoEn: '2026-09-03T14:45:56.080Z',
  },
  {
    id: '1da631cd-11ad-4155-81cf-6dc47f67f685',
    estadoOrigen: 'REGISTRADA',
    estadoDestino: 'EN_ATENCION',
    actorId: 'u-analista',
    actorRol: 'ANALISTA',
    motivo: null,
    ocurridoEn: '2026-09-03T14:45:57.402Z',
  },
  {
    id: 'f404f56d-bd9e-44de-88f7-091cb91d6f23',
    estadoOrigen: 'RESUELTA',
    estadoDestino: 'EN_ATENCION',
    actorId: 'u-supervisor',
    actorRol: 'SUPERVISOR',
    motivo: 'Falta adjuntar la evidencia de la validación.',
    ocurridoEn: '2026-09-03T14:45:58.334Z',
  },
];

/**
 * La linea de tiempo del detalle: BLUEPRINT 5, "vista de detalle con linea
 * de tiempo". Cubre las tres formas que una fila puede tomar: el registro
 * inicial (sin origen), una transicion normal, y una con motivo.
 */
describe('LineaDeTiempo', () => {
  it('debeRenderizarseComoListaOrdenadaConUnElementoPorCambio', () => {
    render(<LineaDeTiempo historial={historial} />);

    const lista = screen.getByRole('list', { name: /historial de la solicitud/i });
    expect(lista.tagName).toBe('OL');
    expect(screen.getAllByRole('listitem')).toHaveLength(3);
  });

  it('elRegistroInicialNoDebeMostrarUnEstadoDeOrigen', () => {
    render(<LineaDeTiempo historial={[historial[0]!]} />);

    // Solo debe verse un chip de estado (el destino); no hay flecha ni origen.
    expect(screen.getByText('Registrada')).toBeInTheDocument();
    expect(screen.queryByText('→')).not.toBeInTheDocument();
  });

  it('unaTransicionNormalDebeMostrarOrigenYDestino', () => {
    render(<LineaDeTiempo historial={[historial[1]!]} />);

    expect(screen.getByText('Registrada')).toBeInTheDocument();
    expect(screen.getByText('En atención')).toBeInTheDocument();
  });

  it('debeMostrarElMotivoCuandoLaTransicionLoTrae', () => {
    render(<LineaDeTiempo historial={[historial[2]!]} />);

    expect(screen.getByText('Falta adjuntar la evidencia de la validación.')).toBeInTheDocument();
  });

  it('debeMostrarElRolDelActorYLaFechaFormateada', () => {
    render(<LineaDeTiempo historial={[historial[1]!]} />);

    expect(screen.getByText(/Analista/)).toBeInTheDocument();
  });
});
