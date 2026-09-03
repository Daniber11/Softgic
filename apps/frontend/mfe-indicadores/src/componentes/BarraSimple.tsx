import * as React from 'react';
import { Box, Stack, Typography } from '@mui/material';

interface BarraSimpleProps {
  readonly etiqueta: string;
  readonly valor: number;
  readonly maximo: number;
}

/**
 * Barra horizontal minima, construida con Box y sin libreria de graficos: el
 * stack aprobado no incluye una, y estos indicadores son tres numeros por
 * categoria, no un panel de analitica compleja. Anadir una dependencia
 * entera para esto seria la clase de sobreingenieria que CLAUDE.md prohibe.
 */
export function BarraSimple({ etiqueta, valor, maximo }: BarraSimpleProps): React.JSX.Element {
  const porcentaje = maximo > 0 ? Math.round((valor / maximo) * 100) : 0;

  return (
    <Stack spacing={0.5}>
      <Stack direction="row" justifyContent="space-between">
        <Typography variant="body2">{etiqueta}</Typography>
        <Typography variant="body2" fontWeight={600}>
          {valor}
        </Typography>
      </Stack>
      <Box
        role="img"
        aria-label={`${etiqueta}: ${valor}`}
        sx={{ height: 8, borderRadius: 1, bgcolor: 'action.hover', overflow: 'hidden' }}
      >
        <Box sx={{ height: '100%', width: `${porcentaje}%`, bgcolor: 'primary.main' }} />
      </Box>
    </Stack>
  );
}
