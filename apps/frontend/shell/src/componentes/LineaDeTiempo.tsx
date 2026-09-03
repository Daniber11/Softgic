import * as React from 'react';
import { Box, List, ListItem, Stack, Typography } from '@mui/material';
import { EstadoChip } from '@shared/componentes/EstadoChip';
import { ETIQUETA_ROL } from '@shared/dominio/tipos';
import type { CambioEstado } from '@shared/esquemas/solicitud';

interface LineaDeTiempoProps {
  readonly historial: readonly CambioEstado[];
}

/**
 * Historial de transiciones de una solicitud.
 *
 * Se renderiza como una lista ordenada real (`component="ol"`), no una pila
 * de `<div>`: para quien usa lector de pantalla, es la diferencia entre
 * escuchar "elemento 3 de 6" y escuchar seis parrafos sueltos sin relacion
 * aparente entre si.
 */
export function LineaDeTiempo({ historial }: LineaDeTiempoProps): React.JSX.Element {
  return (
    <List component="ol" aria-label="Historial de la solicitud" sx={{ p: 0 }}>
      {historial.map((cambio) => (
        <ListItem
          key={cambio.id}
          component="li"
          alignItems="flex-start"
          sx={{ borderLeft: 2, borderColor: 'divider', pl: 2, mb: 1 }}
        >
          <Stack spacing={0.5} sx={{ width: '100%' }}>
            <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap">
              {cambio.estadoOrigen ? (
                <>
                  <EstadoChip tipo="estado" valor={cambio.estadoOrigen} />
                  <Typography component="span" aria-hidden="true">
                    →
                  </Typography>
                </>
              ) : null}
              <EstadoChip tipo="estado" valor={cambio.estadoDestino} />
            </Stack>
            <Typography variant="body2" color="text.secondary">
              {ETIQUETA_ROL[cambio.actorRol]} · {new Date(cambio.ocurridoEn).toLocaleString('es-CO')}
            </Typography>
            {cambio.motivo ? (
              <Box>
                <Typography variant="body2">{cambio.motivo}</Typography>
              </Box>
            ) : null}
          </Stack>
        </ListItem>
      ))}
    </List>
  );
}
