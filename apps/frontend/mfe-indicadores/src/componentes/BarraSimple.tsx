import * as React from 'react';
import { Box, Stack, Typography } from '@mui/material';

interface BarraSimpleProps {
  readonly etiqueta: string;
  readonly valor: number;
  readonly maximo: number;
}

/**
 * El modelo analitico devuelve las claves tal como viven en la dimension
 * (`ATENCION_CIUDADANA`, `EN_ATENCION`). Mostrarlas crudas expone el
 * identificador tecnico al usuario final; aqui se presentan como texto.
 * La fecha ISO se deja intacta: ya es legible y reordenarla la haria
 * ambigua entre formatos regionales.
 */
function comoTexto(clave: string): string {
  if (/^\d{4}-\d{2}-\d{2}$/.test(clave)) {
    return clave;
  }
  const conEspacios = clave.replace(/_/g, ' ').toLowerCase();
  return conEspacios.charAt(0).toUpperCase() + conEspacios.slice(1);
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
    <Stack spacing={0.75}>
      <Stack direction="row" justifyContent="space-between" alignItems="baseline" spacing={2}>
        <Typography variant="body2" sx={{ color: 'text.secondary', fontWeight: 500 }}>
          {comoTexto(etiqueta)}
        </Typography>
        {/* Cifra tabular y a mayor tamaño: es el dato, no la etiqueta. Con
            ambos al mismo tamaño la fila se leia como texto corrido. */}
        <Typography
          variant="body1"
          sx={{ fontWeight: 700, fontVariantNumeric: 'tabular-nums', lineHeight: 1 }}
        >
          {valor}
        </Typography>
      </Stack>
      <Box
        role="img"
        aria-label={`${comoTexto(etiqueta)}: ${valor}`}
        sx={{
          height: 10,
          borderRadius: 999,
          bgcolor: '#EDF2F7',
          border: '1px solid',
          borderColor: 'divider',
          overflow: 'hidden',
        }}
      >
        <Box
          sx={{
            height: '100%',
            // Minimo visible: con valores muy pequeños frente al maximo, la
            // barra quedaba en 1-2 px y parecia que el dato era cero.
            width: `${valor > 0 ? Math.max(porcentaje, 4) : 0}%`,
            borderRadius: 999,
            backgroundImage: 'linear-gradient(90deg, #3B82D6 0%, #0B4F9E 100%)',
            transition: 'width 420ms cubic-bezier(0.4, 0, 0.2, 1)',
          }}
        />
      </Box>
    </Stack>
  );
}
