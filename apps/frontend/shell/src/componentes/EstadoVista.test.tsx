import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { EstadoVista } from '@shared/componentes/EstadoVista';

/**
 * Los cuatro estados que CLAUDE.md exige en cada vista: cargando, vacio,
 * error con reintento, y autorizacion insuficiente. Cada prueba verifica lo
 * que un usuario real percibe -el texto en pantalla, si el boton de
 * reintento existe y funciona-, no detalles de implementacion.
 */
describe('EstadoVista', () => {
  it('debeMostrarElTextoDeCargaConRolStatus', () => {
    render(<EstadoVista estado="cargando" etiqueta="Cargando solicitudes…" />);

    expect(screen.getByRole('status')).toHaveTextContent('Cargando solicitudes…');
  });

  it('debeMostrarUnTituloPorDefectoSiNoSeIndicaUno', () => {
    render(<EstadoVista estado="cargando" />);

    expect(screen.getByRole('status')).toHaveTextContent('Cargando…');
  });

  it('debeMostrarElMensajeDeVacioConSuDescripcion', () => {
    render(
      <EstadoVista
        estado="vacio"
        titulo="No hay solicitudes"
        descripcion="Pruebe a ajustar los filtros."
      />,
    );

    expect(screen.getByText('No hay solicitudes')).toBeInTheDocument();
    expect(screen.getByText('Pruebe a ajustar los filtros.')).toBeInTheDocument();
  });

  it('debeMostrarElErrorConRolAlertYPermitirReintentar', async () => {
    const usuario = userEvent.setup();
    const onReintentar = vi.fn();

    render(<EstadoVista estado="error" mensaje="No se pudo conectar." onReintentar={onReintentar} />);

    const alerta = screen.getByRole('alert');
    expect(alerta).toHaveTextContent('No se pudo conectar.');

    await usuario.click(screen.getByRole('button', { name: /reintentar/i }));
    expect(onReintentar).toHaveBeenCalledTimes(1);
  });

  it('debeMostrarAutorizacionInsuficienteConRolAlert', () => {
    render(<EstadoVista estado="sinAutorizacion" mensaje="Solo el supervisor puede ver esto." />);

    expect(screen.getByRole('alert')).toHaveTextContent('Solo el supervisor puede ver esto.');
    expect(screen.getByText('Autorización insuficiente')).toBeInTheDocument();
  });

  it('debeRenderizarElContenidoTalCualCuandoEstaListo', () => {
    render(
      <EstadoVista estado="listo">
        <p>Contenido real de la vista</p>
      </EstadoVista>,
    );

    expect(screen.getByText('Contenido real de la vista')).toBeInTheDocument();
  });
});
