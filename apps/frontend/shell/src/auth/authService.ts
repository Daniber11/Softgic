import { InMemoryWebStorage, UserManager, WebStorageStateStore, type User } from 'oidc-client-ts';

/**
 * Instancia unica de UserManager para todo el shell.
 *
 * === Por que `userStore: new InMemoryWebStorage()` ===
 *
 * Por defecto, oidc-client-ts persiste el objeto `User` completo —incluido
 * `access_token`— en `window.sessionStorage`. Eso viola directamente
 * CLAUDE.md ("el token de acceso nunca se guarda en localStorage ni
 * sessionStorage"): cualquier script inyectado por XSS podria leerlo. Se
 * sobreescribe con almacenamiento en memoria, que desaparece con cada
 * recarga de pagina.
 *
 * Eso deja una pregunta: si el token no sobrevive un F5, como se cumple el
 * escenario A6 ("recargar el navegador en el detalle recupera sesion")? La
 * respuesta es que NO se recupera desde almacenamiento local: se pide de
 * nuevo, en silencio, contra la sesion que Keycloak mantiene en su propia
 * cookie (HttpOnly, fuera del alcance de este JavaScript). Es
 * `signinSilent()`, no localStorage, lo que hace que A6 funcione sin
 * comprometer la regla de seguridad. Ver AuthProvider.tsx.
 *
 * === Por que `stateStore` SI usa el almacenamiento por defecto ===
 *
 * El stateStore no guarda el token: guarda el verifier de PKCE, el nonce y
 * la ruta de retorno mientras el navegador esta fuera, en la pagina de login
 * de Keycloak. Esa informacion tiene que sobrevivir la navegacion completa
 * de ida y vuelta —InMemoryWebStorage no sobrevive un cambio de pagina,
 * porque es literalmente una variable de JavaScript— y no es un secreto
 * reutilizable: son valores de un solo uso, atados a una peticion de login
 * concreta. sessionStorage es el lugar correcto para esto.
 *
 * === Por que el singleton se guarda en globalThis, no solo en el modulo ===
 *
 * Defecto real, encontrado verificando la vista de Indicadores en el
 * navegador: `authBridge.ts` (expuesto por federacion a mfeIndicadores)
 * importa este archivo con un `import` relativo normal. El mecanismo
 * `shared` de Module Federation da tratamiento de singleton a paquetes de
 * npm declarados explicitamente (react, oidc-client-ts no estaba en esa
 * lista) -no a modulos LOCALES como este-. Rspack empaqueta el modulo
 * expuesto de `authBridge.ts` como un punto de entrada independiente, y ese
 * punto de entrada evalua SU PROPIA copia de `authService.ts`: ejecuta
 * `new UserManager(...)` una segunda vez, generando una instancia
 * completamente distinta a la que usa AuthProvider.tsx. Se confirmo en el
 * navegador: `authBridge.getUsuario()` devolvia `null` incluso con una
 * sesion real y activa, porque preguntaba a un UserManager que jamas vio un
 * login.
 *
 * Guardar la instancia en `globalThis` la hace verdaderamente unica en la
 * pestania, sin importar cuantas veces se evalue este archivo como modulo:
 * `globalThis` es el mismo objeto `window` para todo el codigo que corre en
 * la pagina, sea cual sea el chunk de Rspack del que provenga.
 */
const CLAVE_GLOBAL = Symbol.for('solicitudes-gov.shell.userManager');

interface GlobalConUserManager {
  [CLAVE_GLOBAL]?: UserManager;
}

function crearOReusarUserManager(): UserManager {
  const global = globalThis as GlobalConUserManager;
  if (!global[CLAVE_GLOBAL]) {
    global[CLAVE_GLOBAL] = new UserManager({
      authority: __KEYCLOAK_ISSUER__,
      client_id: 'shell-web',
      redirect_uri: `${window.location.origin}/callback`,
      post_logout_redirect_uri: window.location.origin,
      response_type: 'code',
      scope: 'openid profile',
      // `userStore` exige el contrato asincrono StateStore (set/get/remove/
      // getAllKeys), no el Storage sincrono del navegador:
      // WebStorageStateStore es el adaptador de oidc-client-ts entre
      // ambos. Envolver InMemoryWebStorage en el, en vez de pasarlo
      // directo, es lo que hace que compile Y que el resultado siga
      // siendo en memoria.
      userStore: new WebStorageStateStore({ store: new InMemoryWebStorage() }),
      automaticSilentRenew: true,
      // El refresco silencioso navega en un iframe oculto contra
      // Keycloak. Si el realm exige mas de esto para reautenticar (por
      // ejemplo, MFA), este parametro le dice a Keycloak que no muestre
      // ninguna pantalla y falle en lugar de quedarse esperando una
      // interaccion que nunca puede ocurrir dentro de un iframe
      // invisible.
      silentRequestTimeoutInSeconds: 10,
    });
  }
  return global[CLAVE_GLOBAL];
}

export const userManager = crearOReusarUserManager();

export type { User };
