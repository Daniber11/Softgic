import * as React from 'react';
import { useNavigate } from 'react-router-dom';
import { EstadoVista } from '@shared/componentes/EstadoVista';
import { userManager } from './authService';

const RUTA_POR_DEFECTO = '/';

/**
 * Ruta /callback: donde Keycloak devuelve al usuario tras autenticarse.
 *
 * Completa el intercambio del codigo de autorizacion por el token (incluida
 * la verificacion del `code_verifier` de PKCE, que hace oidc-client-ts por
 * dentro) y despues navega a la ruta que se guardo como `state` al iniciar
 * el flujo -la ruta que el usuario intentaba visitar antes de que
 * RutaProtegida lo mandara a iniciar sesion-.
 */
export function CallbackPage(): React.JSX.Element {
  const navigate = useNavigate();
  const [error, setError] = React.useState<string | null>(null);
  const seEjecutoUnaVez = React.useRef(false);

  const completarLogin = React.useCallback(async () => {
    setError(null);
    try {
      // signinCallback(), no signinRedirectCallback(). Esta misma ruta /callback
      // se carga en DOS contextos distintos: como navegacion normal de vuelta
      // desde el login, y dentro del iframe oculto que usa el renuevo
      // silencioso (automaticSilentRenew, y tambien la recuperacion de sesion
      // al recargar -A6-). signinRedirectCallback() solo sabe manejar el
      // primer caso; dentro del iframe se queda esperando algo que nunca pasa
      // y el signinSilent() del padre termina en "IFrame timed out without a
      // response". signinCallback() lee de que TIPO de peticion se trataba
      // -guardado en el stateStore al iniciarla- y llama internamente al
      // metodo correcto, incluido el postMessage que el iframe necesita
      // enviarle a la ventana padre para resolver la promesa.
      const usuario = await userManager.signinCallback();
      // Cuando esto corrio dentro del iframe silencioso, `usuario` llega
      // undefined (la sesion se resuelve en la ventana padre, no aqui) y no
      // hay nada que navegar: el iframe se destruye solo en cuanto termina.
      if (usuario) {
        const destino = typeof usuario.state === 'string' ? usuario.state : RUTA_POR_DEFECTO;
        navigate(destino, { replace: true });
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : 'No se pudo completar el inicio de sesión.');
    }
  }, [navigate]);

  React.useEffect(() => {
    // React 18+ en modo estricto monta los efectos dos veces en desarrollo.
    // signinRedirectCallback() consume el codigo de un solo uso: la segunda
    // llamada fallaria con un error confuso sobre estado invalido. Esta
    // bandera hace que el intercambio ocurra una unica vez de verdad.
    if (seEjecutoUnaVez.current) {
      return;
    }
    seEjecutoUnaVez.current = true;
    void completarLogin();
  }, [completarLogin]);

  if (error) {
    return <EstadoVista estado="error" mensaje={error} onReintentar={() => void completarLogin()} />;
  }

  return <EstadoVista estado="cargando" etiqueta="Completando inicio de sesión…" />;
}
