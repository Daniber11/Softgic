import type { User } from 'oidc-client-ts';
import { extraerIdentidad } from '@shared/auth/decodificarJwt';
import type { Rol } from '@shared/dominio/tipos';
import { userManager } from './authService';

/**
 * Puente de autenticacion expuesto por federacion al microfrontend remoto.
 *
 * === Por que es un objeto plano y no un React Context ===
 *
 * Compartir un React Context a traves de Module Federation exige que ambas
 * apps referencien exactamente el mismo modulo de contexto -algo fragil de
 * mantener sincronizado entre dos builds independientes-. Un objeto con
 * funciones no tiene ese problema: el remoto lo importa, lo llama, y se
 * suscribe a los cambios con `suscribir`. Es lo que BLUEPRINT 9.2 describe:
 * "expone por federacion un authBridge con getToken() y getUser()".
 *
 * === Por que hay una cache en modulo en vez de leer UserManager directo ===
 *
 * `UserManager.getUser()` es asincrono: consulta el store cada vez. Este
 * puente necesita una lectura SINCRONA (el remoto la usa en el primer
 * render), y la API publica de oidc-client-ts no ofrece un getter sincrono
 * para eso. La alternativa habria sido leer un campo privado del
 * UserManager, pero eso es exactamente lo que CLAUDE.md prohibe: usar una
 * API que no esta documentada como publica. En su lugar, este modulo
 * mantiene su propia copia, actualizada a traves de los eventos publicos
 * `addUserLoaded` / `addUserUnloaded`, mas una hidratacion inicial.
 *
 * === `listo`: la pieza que faltaba, encontrada verificando en el navegador ===
 *
 * La hidratacion inicial es asincrona (`userManager.getUser()`), pero el
 * primer consumidor (`useSesion.ts` en el remoto) llamaba a `getUsuario()`
 * de forma SINCRONA justo despues de que el import federado resolviera, sin
 * esperar esa hidratacion. Con una sesion real y activa, `getUsuario()`
 * devolvia igual `null` en esa primera lectura: la carrera se ganaba antes
 * de que la cache tuviera nada.
 *
 * Y no bastaba con confiar en `suscribir` para corregirlo despues: el
 * usuario ya habia iniciado sesion HACE RATO, mucho antes de que el remoto
 * de indicadores se montara y se suscribiera a `addUserLoaded`. Ese evento
 * ya habia ocurrido en el pasado -un emisor de eventos no reproduce eventos
 * viejos a quien se suscribe tarde-, asi que la suscripcion nunca iba a
 * disparar por si sola. El sintoma en el navegador fue exactamente ese:
 * `getUsuario()` devolvia `null` con una sesion real y visible en la barra
 * superior del propio shell, en la misma pestania.
 *
 * `listo` expone la promesa de esa hidratacion inicial para que el
 * consumidor pueda esperarla antes de su primera lectura, cerrando la
 * carrera sin recurrir a ninguna API privada.
 */

export interface UsuarioBridge {
  readonly actorId: string;
  readonly roles: readonly Rol[];
}

let usuarioCacheado: User | null = null;

userManager.events.addUserLoaded((usuario) => {
  usuarioCacheado = usuario;
});
userManager.events.addUserUnloaded(() => {
  usuarioCacheado = null;
});

const listo: Promise<void> = userManager.getUser().then((usuario) => {
  usuarioCacheado = usuario;
});

function usuarioVigente(): User | null {
  return usuarioCacheado && !usuarioCacheado.expired ? usuarioCacheado : null;
}

export const authBridge = {
  /**
   * Se resuelve cuando la lectura inicial del usuario ya esta disponible.
   * El consumidor debe esperarla antes de la primera llamada a getUsuario()
   * o getToken(); despues de eso, `suscribir` mantiene la cache al dia.
   */
  listo,

  /** Token vigente, o null si no hay sesion. Listo para la cabecera Authorization. */
  getToken(): string | null {
    return usuarioVigente()?.access_token ?? null;
  },

  getUsuario(): UsuarioBridge | null {
    const usuario = usuarioVigente();
    if (!usuario) {
      return null;
    }
    const identidad = extraerIdentidad(usuario.access_token);
    return { actorId: identidad.id, roles: identidad.roles };
  },

  /** Notifica cuando la sesion cambia (login, logout, renuevo). Devuelve como desuscribirse. */
  suscribir(callback: () => void): () => void {
    const quitarCargado = userManager.events.addUserLoaded(callback);
    const quitarDescargado = userManager.events.addUserUnloaded(callback);
    return () => {
      quitarCargado();
      quitarDescargado();
    };
  },
};

export default authBridge;
