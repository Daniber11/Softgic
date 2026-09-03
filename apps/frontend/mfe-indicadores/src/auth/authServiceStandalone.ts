import { InMemoryWebStorage, UserManager, WebStorageStateStore } from 'oidc-client-ts';

/**
 * UserManager propio del remoto, usado UNICAMENTE en modo standalone.
 *
 * BLUEPRINT 9.2: "En modo standalone el remoto usa su propio proveedor."
 * Nunca hay dos instancias de OIDC compitiendo por el mismo token: cuando el
 * shell federa este remoto, este archivo no se ejecuta -IndicadoresApp usa
 * `auth/useSesion.ts` en su rama federada, que consume `shell/authBridge` en
 * su lugar-.
 *
 * Usa el mismo cliente publico `shell-web` que el shell (con un
 * `redirect_uri` distinto, ya autorizado en el realm:
 * redirectUris incluye tanto :3000/* como :3001/*). Las mismas razones de
 * seguridad del shell aplican aqui: token en memoria, nunca en localStorage
 * ni sessionStorage (ver el comentario largo en shell/src/auth/authService.ts).
 */
export const userManagerStandalone = new UserManager({
  authority: __KEYCLOAK_ISSUER__,
  client_id: 'shell-web',
  redirect_uri: `${window.location.origin}/callback`,
  post_logout_redirect_uri: window.location.origin,
  response_type: 'code',
  scope: 'openid profile',
  userStore: new WebStorageStateStore({ store: new InMemoryWebStorage() }),
  automaticSilentRenew: true,
  silentRequestTimeoutInSeconds: 10,
});
