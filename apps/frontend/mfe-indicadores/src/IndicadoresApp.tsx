// =============================================================================
//  IndicadoresApp - modulo expuesto por federacion.
//
//  En la fase 2 su unico proposito es demostrar que la federacion funciona: usa
//  componentes de MUI a proposito, para forzar el uso de Emotion y comprobar que
//  no se instancia dos veces al cruzar la frontera del remoto. La vista
//  analitica real se construye en la fase 5.
// =============================================================================

// El runtime automatico de JSX no inyecta React en el ambito, asi que la
// importacion es explicita: se usa React.version para evidenciar en pantalla que
// host y remoto comparten la misma instancia.
import * as React from 'react';
import { Alert, Box, Chip, Paper, Stack, Typography, useTheme } from '@mui/material';

interface IndicadoresAppProps {
  /** Modo en el que se monta el remoto. Permite verificar visualmente cual esta activo. */
  readonly modo: 'federado' | 'standalone';
}

export default function IndicadoresApp({ modo }: IndicadoresAppProps): React.JSX.Element {
  // Leer el tema comprueba algo que un render simple no comprueba: que el
  // remoto ve el ThemeProvider del host. Si Emotion estuviera duplicado, aqui
  // llegaria el tema por defecto y no el del shell.
  const tema = useTheme();

  return (
    <Paper elevation={3} sx={{ p: 3, borderRadius: 2 }} data-testid="indicadores-app">
      <Stack spacing={2}>
        <Typography variant="h5" component="h2" color="primary">
          Microfrontend de Indicadores
        </Typography>

        <Alert severity="success">
          El remoto se monto correctamente en modo <strong>{modo}</strong>.
        </Alert>

        <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
          <Chip label={`modo: ${modo}`} color="primary" />
          <Chip label={`color primario del tema: ${tema.palette.primary.main}`} variant="outlined" />
          <Chip label={`React ${React.version}`} color="secondary" variant="outlined" />
        </Stack>

        {/* Caja con estilo via Emotion. Si los estilos no se aplican, el fondo
            se ve transparente y el fallo de Emotion queda a la vista. */}
        <Box
          data-testid="caja-emotion"
          sx={{
            p: 2,
            borderRadius: 1,
            bgcolor: 'primary.main',
            color: 'primary.contrastText',
            fontWeight: 600,
          }}
        >
          Si esta caja tiene fondo de color, Emotion resolvio una sola instancia.
        </Box>
      </Stack>
    </Paper>
  );
}
