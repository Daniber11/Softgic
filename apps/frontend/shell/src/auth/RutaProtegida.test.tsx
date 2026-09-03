import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { RutaProtegida } from './RutaProtegida';
import { useAuth } from './AuthProvider';

/**
 * Guard de ruta por rol (BLUEPRINT 9: "vistas con autorizacion insuficiente
 * explicita"). `useAuth` se mockea para controlar el estado de sesion sin
 * levantar Keycloak: lo que se prueba es la LOGICA de la guardia -que
 * decision toma segun el estado y el rol-, no la libreria OIDC.
 */
vi.mock('./AuthProvider', () => ({
  useAuth: vi.fn(),
}));

const useAuthMock = vi.mocked(useAuth);

function renderizar(hijos = <p>Contenido protegido</p>, rolesPermitidos?: readonly ('SOLICITANTE' | 'ANALISTA' | 'SUPERVISOR')[]) {
  return render(
    <MemoryRouter initialEntries={['/solicitudes/nueva']}>
      {rolesPermitidos ? (
        <RutaProtegida rolesPermitidos={rolesPermitidos}>{hijos}</RutaProtegida>
      ) : (
        <RutaProtegida>{hijos}</RutaProtegida>
      )}
    </MemoryRouter>,
  );
}

describe('RutaProtegida', () => {
  beforeEach(() => {
    useAuthMock.mockReset();
  });

  it('debeMostrarElContenidoCuandoElRolEstaPermitido', () => {
    useAuthMock.mockReturnValue({
      estado: 'autenticado',
      actorId: 'u-1',
      roles: ['SUPERVISOR'],
      errorMensaje: null,
      tieneRol: (permitidos) => permitidos.includes('SUPERVISOR'),
      iniciarSesion: vi.fn(),
      cerrarSesion: vi.fn(),
      obtenerToken: () => 'token',
      saliendo: () => false,
    });

    renderizar(<p>Contenido protegido</p>, ['SUPERVISOR']);

    expect(screen.getByText('Contenido protegido')).toBeInTheDocument();
  });

  it('debeMostrarAutorizacionInsuficienteCuandoElRolNoAlcanza', () => {
    useAuthMock.mockReturnValue({
      estado: 'autenticado',
      actorId: 'u-1',
      roles: ['SOLICITANTE'],
      errorMensaje: null,
      tieneRol: (permitidos) => permitidos.includes('SOLICITANTE'),
      iniciarSesion: vi.fn(),
      cerrarSesion: vi.fn(),
      obtenerToken: () => 'token',
      saliendo: () => false,
    });

    renderizar(<p>Contenido protegido</p>, ['SUPERVISOR']);

    expect(screen.queryByText('Contenido protegido')).not.toBeInTheDocument();
    expect(screen.getByText('Autorización insuficiente')).toBeInTheDocument();
  });

  it('debePermitirCualquierRolAutenticadoSiNoSeExigeUnoConcreto', () => {
    useAuthMock.mockReturnValue({
      estado: 'autenticado',
      actorId: 'u-1',
      roles: ['SOLICITANTE'],
      errorMensaje: null,
      tieneRol: () => true,
      iniciarSesion: vi.fn(),
      cerrarSesion: vi.fn(),
      obtenerToken: () => 'token',
      saliendo: () => false,
    });

    renderizar(<p>Contenido protegido</p>);

    expect(screen.getByText('Contenido protegido')).toBeInTheDocument();
  });

  it('debeMostrarCargandoMientrasVerificaLaSesion', () => {
    useAuthMock.mockReturnValue({
      estado: 'verificando',
      actorId: null,
      roles: [],
      errorMensaje: null,
      tieneRol: () => false,
      iniciarSesion: vi.fn(),
      cerrarSesion: vi.fn(),
      obtenerToken: () => null,
      saliendo: () => false,
    });

    renderizar();

    expect(screen.getByRole('status')).toBeInTheDocument();
    expect(screen.queryByText('Contenido protegido')).not.toBeInTheDocument();
  });

  it('debeMostrarElErrorDeVerificacionConSuMensaje', () => {
    useAuthMock.mockReturnValue({
      estado: 'error',
      actorId: null,
      roles: [],
      errorMensaje: 'Keycloak no responde.',
      tieneRol: () => false,
      iniciarSesion: vi.fn(),
      cerrarSesion: vi.fn(),
      obtenerToken: () => null,
      saliendo: () => false,
    });

    renderizar();

    expect(screen.getByRole('alert')).toHaveTextContent('Keycloak no responde.');
  });

  it('debeIniciarSesionPreservandoLaRutaCuandoEstaAnonimo', async () => {
    const iniciarSesion = vi.fn();
    useAuthMock.mockReturnValue({
      estado: 'anonimo',
      actorId: null,
      roles: [],
      errorMensaje: null,
      tieneRol: () => false,
      iniciarSesion,
      cerrarSesion: vi.fn(),
      obtenerToken: () => null,
      saliendo: () => false,
    });

    renderizar();

    await waitFor(() => expect(iniciarSesion).toHaveBeenCalledWith('/solicitudes/nueva'));
  });
});
