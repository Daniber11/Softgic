// =============================================================================
//  LimiteDeError - aisla el fallo de un microfrontend remoto.
//
//  Un remoto es codigo que se descarga por red en tiempo de ejecucion: puede no
//  estar desplegado, puede tardar, puede fallar al evaluarse. Sin este limite,
//  cualquiera de esos casos deja el shell entero en blanco, que es exactamente
//  el sintoma que el reto pide evitar.
// =============================================================================

import * as React from 'react';
import { Alert, AlertTitle, Button } from '@mui/material';

interface LimiteDeErrorProps {
  readonly children: React.ReactNode;
  /** Nombre del remoto, para que el mensaje diga cual fallo y no "algo fallo". */
  readonly nombreRemoto: string;
  /**
   * Se invoca al pulsar "Reintentar".
   *
   * El limite NO reintenta por su cuenta: no puede. React.lazy memoiza la
   * promesa rechazada, de modo que limpiar el estado de aqui volveria a
   * renderizar exactamente el mismo modulo fallido. Quien reintenta es el
   * padre, creando un React.lazy nuevo y remontando este limite con otra key.
   */
  readonly onReintentar: () => void;
}

interface LimiteDeErrorState {
  readonly error: Error | null;
}

export class LimiteDeError extends React.Component<LimiteDeErrorProps, LimiteDeErrorState> {
  public constructor(props: LimiteDeErrorProps) {
    super(props);
    this.state = { error: null };
  }

  public static getDerivedStateFromError(error: Error): LimiteDeErrorState {
    return { error };
  }

  public override render(): React.ReactNode {
    const { error } = this.state;
    const { children, nombreRemoto, onReintentar } = this.props;

    if (error !== null) {
      return (
        <Alert
          severity="error"
          data-testid="error-remoto"
          action={
            <Button color="inherit" size="small" onClick={onReintentar}>
              Reintentar
            </Button>
          }
        >
          <AlertTitle>No se pudo cargar el modulo {nombreRemoto}</AlertTitle>
          {error.message}
        </Alert>
      );
    }

    return children;
  }
}
