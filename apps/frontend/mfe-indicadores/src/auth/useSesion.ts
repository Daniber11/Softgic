import * as React from 'react';
import { ErrorResponse } from 'oidc-client-ts';
import { extraerIdentidad } from '@shared/auth/decodificarJwt';
import type { Rol } from '@shared/dominio/tipos';
import { userManagerStandalone } from './authServiceStandalone';

type EstadoSesion = 'verificando' | 'autenticado' | 'anonimo' | 'error';

interface Sesion {
  readonly estado: EstadoSesion;
  readonly token: string | null;
  readonly roles: readonly Rol[];
  readonly errorMensaje: string | null;
  /** Solo tiene efecto en modo standalone; en federado la sesion la posee el shell. */
  readonly iniciarSesion: () => void;
}

const SIN_SESION_ACTIVA = new Set(['login_required', 'interaction_required', 'consent_required']);

/**
 * Promesa memoizada del import federado, a nivel de modulo.
 *
 * Defecto real, verificado en el navegador: React 19 en modo estricto monta
 * este efecto dos veces (monta, limpia, vuelve a montar), y cada montaje
 * llamaba a `import('shell/authBridge')` por su cuenta. Dos solicitudes
 * concurrentes del MISMO modulo federado, en la ventana de tiempo en que el
 * contenedor remoto todavia se esta inicializando, dejaban la primera
 * colgada para siempre -sin lanzar ningun error, la vista se quedaba en
 * "Verificando sesion..." indefinidamente-. Se confirmo pidiendo el mismo
 * modulo una sola vez desde la consola: esa llamada aislada resolvia bien.
 *
 * Memoizar la promesa aqui, fuera del efecto, hace que las dos invocaciones
 * de React compartan la MISMA carga en lugar de disparar dos, y de paso es
 * mas eficiente: el remoto solo se pide una vez sin importar cuantas veces
 * se monte el componente.
 */
let promesaAuthBridge: Promise<typeof import('shell/authBridge')> | undefined;

function cargarAuthBridge(): Promise<typeof import('shell/authBridge')> {
  promesaAuthBridge ??= import('shell/authBridge');
  return promesaAuthBridge;
}

/**
 * Sesion unificada del remoto, con una unica implementacion segun el modo.
 *
 * Los hooks de React (useState, useEffect) se llaman siempre en el mismo
 * orden sin importar `modo`: lo que cambia entre federado y standalone es la
 * LOGICA dentro del efecto, nunca la cantidad ni el orden de hooks. Es lo que
 * mantiene esta funcion valida frente a las reglas de hooks
 * independientemente de que en la practica `modo` nunca cambia una vez
 * montado el componente.
 *
 * === Por que `shell/authBridge` se importa de forma dinamica ===
 *
 * Este archivo lo usa IndicadoresApp sin importar el modo. Una importacion
 * estatica de un modulo federado se resuelve tan pronto el grafo de modulos
 * se evalua, incluso en la rama que nunca se ejecuta: en modo standalone
 * intentaria alcanzar `shell@http://localhost:3000/remoteEntry.js` sin que
 * exista ningun shell corriendo. El `import()` dinamico, condicionado a
 * `modo === 'federado'`, evita ese intento por completo.
 */
export function useSesion(modo: 'federado' | 'standalone'): Sesion {
  const [estado, setEstado] = React.useState<EstadoSesion>('verificando');
  const [token, setToken] = React.useState<string | null>(null);
  const [roles, setRoles] = React.useState<readonly Rol[]>([]);
  const [errorMensaje, setErrorMensaje] = React.useState<string | null>(null);

  React.useEffect(() => {
    let cancelado = false;
    let desuscribir: (() => void) | undefined;

    async function iniciarFederado(): Promise<void> {
      const modulo = await cargarAuthBridge();
      const authBridge = modulo.default;
      // Sin este await, la primera lectura ganaba la carrera a la
      // hidratacion inicial del puente y siempre encontraba null, aun con
      // una sesion real y activa. Ver el comentario largo en
      // shell/src/auth/authBridge.ts sobre `listo`.
      await authBridge.listo;

      const leer = () => {
        const usuario = authBridge.getUsuario();
        if (cancelado) {
          return;
        }
        if (usuario) {
          setToken(authBridge.getToken());
          setRoles(usuario.roles);
          setEstado('autenticado');
        } else {
          setToken(null);
          setRoles([]);
          setEstado('anonimo');
        }
      };

      leer();
      desuscribir = authBridge.suscribir(leer);
    }

    async function iniciarStandalone(): Promise<void> {
      try {
        const existente = await userManagerStandalone.getUser();
        if (existente && !existente.expired) {
          aplicarUsuarioStandalone(existente.access_token);
          return;
        }

        const recuperado = await userManagerStandalone.signinSilent();
        if (cancelado) {
          return;
        }
        if (recuperado) {
          aplicarUsuarioStandalone(recuperado.access_token);
        } else {
          setEstado('anonimo');
        }
      } catch (error) {
        if (cancelado) {
          return;
        }
        if (error instanceof ErrorResponse && SIN_SESION_ACTIVA.has(error.error ?? '')) {
          setEstado('anonimo');
          return;
        }
        setEstado('error');
        setErrorMensaje(error instanceof Error ? error.message : 'No se pudo verificar la sesión.');
      }
    }

    function aplicarUsuarioStandalone(accessToken: string): void {
      const identidad = extraerIdentidad(accessToken);
      setToken(accessToken);
      setRoles(identidad.roles);
      setEstado('autenticado');
    }

    if (modo === 'federado') {
      void iniciarFederado();
    } else {
      void iniciarStandalone();

      const quitarCargado = userManagerStandalone.events.addUserLoaded((u) =>
        aplicarUsuarioStandalone(u.access_token),
      );
      const quitarDescargado = userManagerStandalone.events.addUserUnloaded(() => {
        setToken(null);
        setRoles([]);
        setEstado('anonimo');
      });
      desuscribir = () => {
        quitarCargado();
        quitarDescargado();
      };
    }

    return () => {
      cancelado = true;
      desuscribir?.();
    };
  }, [modo]);

  const iniciarSesion = React.useCallback(() => {
    if (modo === 'standalone') {
      void userManagerStandalone.signinRedirect({ state: window.location.pathname });
    }
  }, [modo]);

  return { estado, token, roles, errorMensaje, iniciarSesion };
}
