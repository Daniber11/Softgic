import * as React from 'react';
import { useLocation } from 'react-router-dom';
import { EstadoVista } from '@shared/componentes/EstadoVista';
import type { Rol } from '@shared/dominio/tipos';
import { useAuth } from './AuthProvider';

interface RutaProtegidaProps {
  readonly children: React.ReactNode;
  /** Si se omite, cualquier usuario autenticado puede entrar. */
  readonly rolesPermitidos?: readonly Rol[];
}

/**
 * Guardia de ruta: exige sesion y, opcionalmente, un rol concreto.
 *
 * Esto es usabilidad, no seguridad (CLAUDE.md 8.3): ocultar una vista aqui
 * no reemplaza la validacion en servidor, que ocurre en las tres capas de
 * SecurityConfiguration, @PreAuthorize y el agregado. Lo que si logra este
 * componente es que un usuario sin el rol vea un mensaje claro en lugar de
 * una peticion que falla con 403 sin explicacion, o peor, una pantalla en
 * blanco.
 */
export function RutaProtegida({ children, rolesPermitidos }: RutaProtegidaProps): React.JSX.Element {
  const auth = useAuth();
  const location = useLocation();
  const yaPidioLogin = React.useRef(false);

  React.useEffect(() => {
    // auth.saliendo() descarta el "anonimo" transitorio de un cierre de
    // sesion en curso: sin este chequeo, este mismo efecto competia con
    // signoutRedirect() y volvia a autenticar al usuario que se acababa de
    // desloguear. Ver el comentario largo en AuthProvider.cerrarSesion.
    if (auth.estado === 'anonimo' && !yaPidioLogin.current && !auth.saliendo()) {
      yaPidioLogin.current = true;
      // Se preserva la ruta actual (path + query) para volver exactamente
      // aqui despues de autenticarse, en vez de aterrizar siempre en "/".
      void auth.iniciarSesion(location.pathname + location.search);
    }
  }, [auth, location.pathname, location.search]);

  if (auth.estado === 'verificando' || auth.estado === 'anonimo') {
    return <EstadoVista estado="cargando" etiqueta="Verificando sesión…" />;
  }

  if (auth.estado === 'error') {
    return (
      <EstadoVista
        estado="error"
        mensaje={auth.errorMensaje ?? 'No se pudo verificar la sesión.'}
        onReintentar={() => window.location.reload()}
      />
    );
  }

  if (rolesPermitidos && !auth.tieneRol(rolesPermitidos)) {
    return <EstadoVista estado="sinAutorizacion" />;
  }

  return <>{children}</>;
}
