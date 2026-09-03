import * as React from 'react';
import { useDispatch } from 'react-redux';
import { ErrorResponse, type User } from 'oidc-client-ts';
import { extraerIdentidad } from '@shared/auth/decodificarJwt';
import type { Rol } from '@shared/dominio/tipos';
import { tokenActualizado } from '../store/authSlice';
import { userManager } from './authService';

type EstadoSesion = 'verificando' | 'autenticado' | 'anonimo' | 'error';

interface ContextoAuth {
  readonly estado: EstadoSesion;
  readonly actorId: string | null;
  readonly roles: readonly Rol[];
  readonly errorMensaje: string | null;
  readonly tieneRol: (permitidos: readonly Rol[]) => boolean;
  readonly iniciarSesion: (rutaDestino: string) => Promise<void>;
  readonly cerrarSesion: () => Promise<void>;
  /** Lectura sincrona del token vigente, para el interceptor HTTP de RTK Query. */
  readonly obtenerToken: () => string | null;
  /**
   * Lectura sincrona de "hay un cierre de sesion en curso". Ver el
   * comentario largo junto a `cerrarSesion` sobre la condicion de carrera
   * que esto evita.
   */
  readonly saliendo: () => boolean;
}

const AuthContext = React.createContext<ContextoAuth | null>(null);

/** Codigos de error de OIDC que significan "no hay sesion", no una falla real. */
const SIN_SESION_ACTIVA = new Set(['login_required', 'interaction_required', 'consent_required']);

/**
 * Duenio unico de la sesion OIDC del shell (BLUEPRINT 9.2).
 *
 * Al montar, intenta recuperar la sesion en silencio contra Keycloak antes de
 * decidir que el usuario esta anonimo. Ese intento silencioso, no un token
 * guardado en el navegador, es lo que hace que recargar la pagina (A6)
 * recupere la sesion: ver el comentario largo en authService.ts.
 */
export function AuthProvider({ children }: { children: React.ReactNode }): React.JSX.Element {
  const dispatch = useDispatch();
  const [estado, setEstado] = React.useState<EstadoSesion>('verificando');
  const [usuario, setUsuario] = React.useState<User | null>(null);
  const [errorMensaje, setErrorMensaje] = React.useState<string | null>(null);
  // Ver el comentario largo en cerrarSesion: evita que RutaProtegida
  // reaccione al "anonimo" transitorio que signoutRedirect() dispara antes
  // de completar su propia navegacion.
  const saliendoRef = React.useRef(false);

  const aplicarUsuario = React.useCallback(
    (u: User) => {
      setUsuario(u);
      setEstado('autenticado');
      setErrorMensaje(null);
      // Refleja el token en Redux para que RTK Query lo lea en
      // prepareHeaders; ver el comentario en store/authSlice.ts.
      dispatch(tokenActualizado(u.access_token));
    },
    [dispatch],
  );

  const limpiarSesion = React.useCallback(() => {
    setUsuario(null);
    setEstado('anonimo');
    dispatch(tokenActualizado(null));
  }, [dispatch]);

  const recuperarSesion = React.useCallback(async () => {
    setEstado('verificando');
    try {
      const existente = await userManager.getUser();
      if (existente && !existente.expired) {
        aplicarUsuario(existente);
        return;
      }

      const recuperado = await userManager.signinSilent();
      if (recuperado) {
        aplicarUsuario(recuperado);
      } else {
        limpiarSesion();
      }
    } catch (error) {
      if (error instanceof ErrorResponse && SIN_SESION_ACTIVA.has(error.error ?? '')) {
        limpiarSesion();
        return;
      }
      // Un timeout de iframe o un Keycloak inalcanzable si es una falla real:
      // se expone via EstadoVista(error) en vez de tratarse como "anonimo".
      setUsuario(null);
      setEstado('error');
      setErrorMensaje(error instanceof Error ? error.message : 'No se pudo verificar la sesión.');
      dispatch(tokenActualizado(null));
    }
  }, [aplicarUsuario, limpiarSesion, dispatch]);

  // Deliberadamente sin dependencias: el chequeo inicial de sesion debe
  // correr una unica vez al montar, no cada vez que recuperarSesion cambia
  // de identidad por sus propias dependencias.
  React.useEffect(() => {
    void recuperarSesion();
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

  // El renuevo automatico (automaticSilentRenew) actualiza el usuario en
  // segundo plano; si el renuevo falla, la sesion se da por perdida y se
  // vuelve al estado anonimo en lugar de seguir usando un token vencido.
  React.useEffect(() => {
    const quitarCargado = userManager.events.addUserLoaded(aplicarUsuario);
    const quitarDescargado = userManager.events.addUserUnloaded(limpiarSesion);
    const quitarFalloRenuevo = userManager.events.addSilentRenewError(limpiarSesion);

    return () => {
      quitarCargado();
      quitarDescargado();
      quitarFalloRenuevo();
    };
  }, [aplicarUsuario, limpiarSesion]);

  const iniciarSesion = React.useCallback(async (rutaDestino: string) => {
    await userManager.signinRedirect({ state: rutaDestino });
  }, []);

  const cerrarSesion = React.useCallback(async () => {
    // signoutRedirect(), no solo removeUser(). removeUser() unicamente borra
    // el User local; la cookie de sesion de Keycloak sigue viva. Se
    // verifico en el navegador: con solo removeUser(), el siguiente
    // signinRedirect() (el que dispara RutaProtegida al ver "anonimo")
    // reautenticaba en silencio con el MISMO usuario, sin mostrar login -
    // "cerrar sesion" no cerraba nada desde el punto de vista de Keycloak.
    //
    // SEGUNDA CONDICION DE CARRERA, TAMBIEN VERIFICADA EN EL NAVEGADOR:
    // signoutRedirect() dispara el evento userUnloaded ANTES de completar su
    // propia navegacion (que primero resuelve de forma asincrona la URL de
    // fin de sesion). Eso hace que limpiarSesion() ponga el estado en
    // "anonimo" mientras la navegacion del logout todavia esta en vuelo, y
    // el efecto de RutaProtegida, al ver "anonimo", lanza su PROPIO
    // signinRedirect() -devolviendo al usuario a login-. Las dos
    // navegaciones compiten por la misma pestania y la de login gana: en la
    // red se veia el logout como net::ERR_ABORTED seguido de un /callback
    // con un codigo de autorizacion nuevo. "Cerrar sesion" terminaba
    // reautenticando al mismo usuario.
    //
    // saliendoRef es una bandera SINCRONA (no estado de React, que se
    // procesa en lotes y no evita la carrera) que RutaProtegida consulta
    // antes de auto-redirigir a login.
    saliendoRef.current = true;
    await userManager.signoutRedirect();
  }, []);

  const saliendo = React.useCallback((): boolean => saliendoRef.current, []);

  const obtenerToken = React.useCallback((): string | null => {
    return usuario && !usuario.expired ? usuario.access_token : null;
  }, [usuario]);

  const identidad = React.useMemo(
    () => (usuario ? extraerIdentidad(usuario.access_token) : null),
    [usuario],
  );

  const tieneRol = React.useCallback(
    (permitidos: readonly Rol[]): boolean =>
      identidad !== null && identidad.roles.some((rol) => permitidos.includes(rol)),
    [identidad],
  );

  const valor = React.useMemo<ContextoAuth>(
    () => ({
      estado,
      actorId: identidad?.id ?? null,
      roles: identidad?.roles ?? [],
      errorMensaje,
      tieneRol,
      iniciarSesion,
      cerrarSesion,
      obtenerToken,
      saliendo,
    }),
    [estado, identidad, errorMensaje, tieneRol, iniciarSesion, cerrarSesion, obtenerToken, saliendo],
  );

  return <AuthContext.Provider value={valor}>{children}</AuthContext.Provider>;
}

export function useAuth(): ContextoAuth {
  const contexto = React.useContext(AuthContext);
  if (contexto === null) {
    throw new Error('useAuth debe usarse dentro de AuthProvider.');
  }
  return contexto;
}
