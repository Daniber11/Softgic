import * as React from 'react';
import {
  Alert,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogContentText,
  DialogTitle,
  TextField,
} from '@mui/material';

interface DialogoTextoProps {
  readonly abierto: boolean;
  readonly titulo: string;
  readonly descripcion?: string;
  readonly etiquetaCampo: string;
  readonly etiquetaConfirmar: string;
  readonly enviando: boolean;
  readonly errorMensaje: string | null;
  readonly onCerrar: () => void;
  readonly onConfirmar: (texto: string) => void;
}

/**
 * Dialogo generico de un campo de texto, usado tanto para devolver una
 * solicitud (motivo obligatorio) como para agregar una observacion.
 *
 * El foco al cerrar lo gestiona el propio `Dialog` de MUI: lo devuelve al
 * elemento que lo abrio, sin codigo adicional. El foco AL ABRIR se gestiona
 * a mano con `inputRef` + `TransitionProps.onEntered` en lugar del
 * `autoFocus` nativo de TextField: con `multiline`, `TextareaAutosize` hace
 * una segunda pasada de render para medir su altura y esa remonta el nodo
 * despues de que el Dialog ya puso el foco ahi, perdiendolo (el foco queda
 * en el contenedor del dialogo). Foco explicito tras `onEntered` -que
 * dispara cuando la transicion de entrada ya termino- evita la carrera.
 */
export function DialogoTexto({
  abierto,
  titulo,
  descripcion,
  etiquetaCampo,
  etiquetaConfirmar,
  enviando,
  errorMensaje,
  onCerrar,
  onConfirmar,
}: DialogoTextoProps): React.JSX.Element {
  const [texto, setTexto] = React.useState('');
  const campoRef = React.useRef<HTMLTextAreaElement | null>(null);

  React.useEffect(() => {
    if (abierto) {
      setTexto('');
    }
  }, [abierto]);

  return (
    <Dialog
      open={abierto}
      onClose={onCerrar}
      fullWidth
      maxWidth="sm"
      slotProps={{ transition: { onEntered: () => campoRef.current?.focus() } }}
    >
      <DialogTitle>{titulo}</DialogTitle>
      <DialogContent>
        {descripcion ? <DialogContentText sx={{ mb: 2 }}>{descripcion}</DialogContentText> : null}
        {errorMensaje ? (
          <Alert severity="error" sx={{ mb: 2 }}>
            {errorMensaje}
          </Alert>
        ) : null}
        <TextField
          inputRef={campoRef}
          fullWidth
          multiline
          minRows={3}
          label={etiquetaCampo}
          value={texto}
          onChange={(e) => setTexto(e.target.value)}
          disabled={enviando}
        />
      </DialogContent>
      <DialogActions>
        <Button onClick={onCerrar} disabled={enviando}>
          Cancelar
        </Button>
        <Button
          variant="contained"
          disabled={enviando || texto.trim().length === 0}
          onClick={() => onConfirmar(texto.trim())}
        >
          {etiquetaConfirmar}
        </Button>
      </DialogActions>
    </Dialog>
  );
}
