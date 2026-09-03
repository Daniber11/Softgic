import * as React from 'react';
import { Link as RouterLink, useSearchParams } from 'react-router-dom';
import {
  FormControl,
  InputLabel,
  Link,
  MenuItem,
  Pagination,
  Paper,
  Select,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Typography,
} from '@mui/material';
import { EstadoChip } from '@shared/componentes/EstadoChip';
import { EstadoVista } from '@shared/componentes/EstadoVista';
import {
  ESTADOS_SOLICITUD,
  ETIQUETA_ESTADO,
  PRIORIDADES,
  ETIQUETA_PRIORIDAD,
  type EstadoSolicitud,
  type Prioridad,
} from '@shared/dominio/tipos';
import { useListarCategoriasQuery, useListarSolicitudesQuery } from '../store/apiSolicitudes';
import { mensajeDeError } from '../store/erroresApi';

const TAMANIO_PAGINA = 20;

/**
 * Bandeja de solicitudes.
 *
 * Los filtros viven en la URL (useSearchParams), no en un useState local:
 * asi un enlace a la bandeja filtrada es compartible, y recargar la pagina
 * conserva el filtro en lugar de perderlo, que es la misma idea detras del
 * escenario A6 aplicada aqui tambien.
 *
 * El filtrado por pertenencia (un SOLICITANTE solo ve lo suyo) no aparece en
 * ningun lado de este componente: lo aplica el backend segun el rol del
 * token, nunca segun un parametro que el cliente pudiera enviar u omitir.
 */
export function BandejaPage(): React.JSX.Element {
  const [parametros, setParametros] = useSearchParams();

  const estado = parametros.get('estado') as EstadoSolicitud | null;
  const prioridad = parametros.get('prioridad') as Prioridad | null;
  const categoriaId = parametros.get('categoriaId');
  const pagina = Number(parametros.get('page') ?? '0');

  const actualizarFiltro = (clave: string, valor: string) => {
    const siguiente = new URLSearchParams(parametros);
    if (valor) {
      siguiente.set(clave, valor);
    } else {
      siguiente.delete(clave);
    }
    siguiente.set('page', '0');
    setParametros(siguiente);
  };

  const cambiarPagina = (_evento: React.ChangeEvent<unknown>, siguiente: number) => {
    const params = new URLSearchParams(parametros);
    params.set('page', String(siguiente - 1));
    setParametros(params);
  };

  const { data: categorias } = useListarCategoriasQuery();

  // Con exactOptionalPropertyTypes, una clave "presente pero undefined" no es
  // lo mismo que una clave ausente: solo se incluyen los filtros que
  // realmente tienen un valor, en lugar de mandar undefined explicito.
  const consulta = useListarSolicitudesQuery({
    ...(estado ? { estado } : {}),
    ...(prioridad ? { prioridad } : {}),
    ...(categoriaId ? { categoriaId } : {}),
    page: pagina,
    size: TAMANIO_PAGINA,
  });

  const nombreCategoria = (id: string): string =>
    categorias?.find((c) => c.id === id)?.nombre ?? id;

  return (
    <Stack spacing={3}>
      <Typography variant="h5" component="h2">
        Bandeja de solicitudes
      </Typography>

      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} component="form" aria-label="Filtros de la bandeja">
        <FormControl size="small" sx={{ minWidth: 180 }}>
          <InputLabel id="filtro-estado-label">Estado</InputLabel>
          <Select
            labelId="filtro-estado-label"
            label="Estado"
            value={estado ?? ''}
            onChange={(e) => actualizarFiltro('estado', e.target.value)}
          >
            <MenuItem value="">Todos</MenuItem>
            {ESTADOS_SOLICITUD.map((e) => (
              <MenuItem key={e} value={e}>
                {ETIQUETA_ESTADO[e]}
              </MenuItem>
            ))}
          </Select>
        </FormControl>

        <FormControl size="small" sx={{ minWidth: 180 }}>
          <InputLabel id="filtro-prioridad-label">Prioridad</InputLabel>
          <Select
            labelId="filtro-prioridad-label"
            label="Prioridad"
            value={prioridad ?? ''}
            onChange={(e) => actualizarFiltro('prioridad', e.target.value)}
          >
            <MenuItem value="">Todas</MenuItem>
            {PRIORIDADES.map((p) => (
              <MenuItem key={p} value={p}>
                {ETIQUETA_PRIORIDAD[p]}
              </MenuItem>
            ))}
          </Select>
        </FormControl>

        <FormControl size="small" sx={{ minWidth: 220 }}>
          <InputLabel id="filtro-categoria-label">Categoría</InputLabel>
          <Select
            labelId="filtro-categoria-label"
            label="Categoría"
            value={categoriaId ?? ''}
            onChange={(e) => actualizarFiltro('categoriaId', e.target.value)}
          >
            <MenuItem value="">Todas</MenuItem>
            {(categorias ?? []).map((c) => (
              <MenuItem key={c.id} value={c.id}>
                {c.nombre}
              </MenuItem>
            ))}
          </Select>
        </FormControl>
      </Stack>

      {consulta.isLoading ? <EstadoVista estado="cargando" etiqueta="Cargando solicitudes…" /> : null}

      {consulta.isError ? (
        <EstadoVista estado="error" mensaje={mensajeDeError(consulta.error)} onReintentar={consulta.refetch} />
      ) : null}

      {consulta.isSuccess && consulta.data.content.length === 0 ? (
        <EstadoVista
          estado="vacio"
          titulo="No hay solicitudes con estos filtros"
          descripcion="Pruebe a ajustar o quitar alguno de los filtros."
        />
      ) : null}

      {consulta.isSuccess && consulta.data.content.length > 0 ? (
        <EstadoVista estado="listo">
          <TableContainer component={Paper}>
            <Table aria-label="Listado de solicitudes">
              <TableHead>
                <TableRow>
                  <TableCell>Código</TableCell>
                  <TableCell>Asunto</TableCell>
                  <TableCell>Categoría</TableCell>
                  <TableCell>Prioridad</TableCell>
                  <TableCell>Estado</TableCell>
                  <TableCell>Creada</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {consulta.data.content.map((s) => (
                  // La fila sigue siendo un <tr> real: envolverla entera en un
                  // <a> produciria HTML de tabla invalido (un enlace no puede
                  // ser fila de una tabla) y perderia la semantica de fila para
                  // lectores de pantalla. El enlace vive en la celda del
                  // codigo, que es el patron accesible estandar para tablas
                  // navegables.
                  <TableRow key={s.id} hover>
                    <TableCell component="th" scope="row">
                      <Link component={RouterLink} to={`/solicitudes/${s.id}`} underline="hover">
                        {s.codigo}
                      </Link>
                    </TableCell>
                    <TableCell>{s.asunto}</TableCell>
                    <TableCell>{nombreCategoria(s.categoriaId)}</TableCell>
                    <TableCell>
                      <EstadoChip tipo="prioridad" valor={s.prioridad} />
                    </TableCell>
                    <TableCell>
                      <EstadoChip tipo="estado" valor={s.estado} />
                    </TableCell>
                    <TableCell>{new Date(s.creadaEn).toLocaleString('es-CO')}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>

          {consulta.data.totalPages > 1 ? (
            <Stack alignItems="center" sx={{ mt: 2 }}>
              <Pagination
                count={consulta.data.totalPages}
                page={pagina + 1}
                onChange={cambiarPagina}
                aria-label="Paginación de la bandeja"
              />
            </Stack>
          ) : null}
        </EstadoVista>
      ) : null}
    </Stack>
  );
}
