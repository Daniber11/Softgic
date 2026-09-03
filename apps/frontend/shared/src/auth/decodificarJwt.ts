import { ROLES, type Rol } from '../dominio/tipos';

/**
 * Forma minima que interesa del payload de un access token de Keycloak.
 *
 * Se decodifica el `access_token`, no el `id_token`: es el mismo token que
 * viaja al backend en la cabecera Authorization, asi que leer roles de aqui
 * garantiza que el frontend vea exactamente lo que el servidor va a validar.
 */
interface PayloadTokenKeycloak {
  readonly sub?: string;
  readonly preferred_username?: string;
  readonly realm_access?: { readonly roles?: readonly string[] };
}

/**
 * Decodifica la porcion payload de un JWT (base64url) sin verificar la firma.
 *
 * No verificar la firma en el cliente es correcto, no un descuido: el
 * navegador no tiene forma de guardar la clave publica de forma que un
 * atacante con el mismo navegador no pueda falsificarla igual. La firma la
 * verifica el backend en cada peticion (Resource Server), que es el unico
 * lugar donde una verificacion de firma protege algo. Aqui el decodificado
 * es solo para pintar la interfaz: que rol mostrar, que menu habilitar.
 */
function decodificarPayload(jwt: string): PayloadTokenKeycloak {
  const partes = jwt.split('.');
  const payloadBase64Url = partes[1];
  if (partes.length !== 3 || payloadBase64Url === undefined) {
    throw new Error('El token no tiene el formato de un JWT (header.payload.signature).');
  }

  const base64 = payloadBase64Url.replace(/-/g, '+').replace(/_/g, '/');
  const relleno = base64.length % 4 === 0 ? '' : '='.repeat(4 - (base64.length % 4));
  const json = atob(base64 + relleno);

  return JSON.parse(json) as PayloadTokenKeycloak;
}

/**
 * Extrae el identificador y los roles conocidos de un access token.
 *
 * Devuelve un arreglo vacio de roles cuando el token no trae la claim
 * `realm_access` en absoluto, que es exactamente lo que Keycloak emite para
 * un usuario sin ningun rol de realm (verificado contra el Keycloak real del
 * stack en la fase 1: no llega un arreglo vacio, la claim falta del todo).
 * El backend aplica la misma tolerancia en ConversorAuthoritiesKeycloak.
 */
export function extraerIdentidad(accessToken: string): { id: string; roles: readonly Rol[] } {
  const payload = decodificarPayload(accessToken);

  const rolesConocidos = new Set<string>(ROLES);
  const roles = (payload.realm_access?.roles ?? []).filter(
    (rol): rol is Rol => rolesConocidos.has(rol),
  );

  return { id: payload.sub ?? payload.preferred_username ?? '', roles };
}
