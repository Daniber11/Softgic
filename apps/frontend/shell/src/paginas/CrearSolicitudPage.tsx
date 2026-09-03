import * as React from 'react';
import { useNavigate } from 'react-router-dom';
import { useForm, Controller } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import {
  Alert,
  Button,
  FormControl,
  FormHelperText,
  InputLabel,
  MenuItem,
  Paper,
  Select,
  Stack,
  TextField,
  Typography,
} from '@mui/material';
import { EstadoVista } from '@shared/componentes/EstadoVista';
import { crearSolicitudSchema, type CrearSolicitudFormulario } from '@shared/esquemas/formularios';
import { PRIORIDADES, ETIQUETA_PRIORIDAD } from '@shared/dominio/tipos';
import { useCrearSolicitudMutation, useListarCategoriasQuery } from '../store/apiSolicitudes';
import { mensajeDeError } from '../store/erroresApi';

/**
 * Registro de una solicitud nueva (escenario A1).
 *
 * La validacion de react-hook-form + Zod es la misma que ya corre en el
 * servidor (longitudes, campos obligatorios): se repite aqui unicamente
 * para que el usuario vea el error al perder el foco del campo, no despues
 * de un viaje de red. La autorizacion -que solo un SOLICITANTE llegue a ver
 * este formulario- la exige RutaProtegida y, de verdad, el servidor.
 */
export function CrearSolicitudPage(): React.JSX.Element {
  const navigate = useNavigate();
  const { data: categorias, isLoading: cargandoCategorias, isError: errorCategorias, refetch } =
    useListarCategoriasQuery();
  const [crear, { isLoading: enviando, error: errorEnvio }] = useCrearSolicitudMutation();

  const {
    control,
    handleSubmit,
    formState: { errors },
  } = useForm<CrearSolicitudFormulario>({
    resolver: zodResolver(crearSolicitudSchema),
    defaultValues: { asunto: '', descripcion: '', categoriaId: '', prioridad: 'MEDIA' },
  });

  const onSubmit = handleSubmit(async (valores) => {
    const creada = await crear(valores).unwrap();
    navigate(`/solicitudes/${creada.id}`);
  });

  if (cargandoCategorias) {
    return <EstadoVista estado="cargando" etiqueta="Cargando categorías…" />;
  }

  if (errorCategorias || !categorias) {
    return (
      <EstadoVista
        estado="error"
        mensaje="No se pudieron cargar las categorías."
        onReintentar={refetch}
      />
    );
  }

  return (
    <Stack spacing={3} sx={{ maxWidth: 640 }}>
      <Typography variant="h5" component="h2">
        Registrar solicitud
      </Typography>

      <Paper sx={{ p: 3 }}>
        <Stack
          component="form"
          spacing={3}
          onSubmit={(e) => {
            e.preventDefault();
            void onSubmit();
          }}
          noValidate
        >
          {errorEnvio ? <Alert severity="error">{mensajeDeError(errorEnvio)}</Alert> : null}

          <Controller
            name="asunto"
            control={control}
            render={({ field }) => (
              <TextField
                {...field}
                label="Asunto"
                required
                fullWidth
                error={!!errors.asunto}
                helperText={errors.asunto?.message ?? ' '}
                slotProps={{ htmlInput: { maxLength: 200 } }}
              />
            )}
          />

          <Controller
            name="descripcion"
            control={control}
            render={({ field }) => (
              <TextField
                {...field}
                label="Descripción"
                required
                fullWidth
                multiline
                minRows={4}
                error={!!errors.descripcion}
                helperText={errors.descripcion?.message ?? ' '}
                slotProps={{ htmlInput: { maxLength: 2000 } }}
              />
            )}
          />

          <Controller
            name="categoriaId"
            control={control}
            render={({ field }) => (
              <FormControl fullWidth required error={!!errors.categoriaId}>
                <InputLabel id="categoria-label">Categoría</InputLabel>
                <Select {...field} labelId="categoria-label" label="Categoría">
                  {categorias.map((c) => (
                    <MenuItem key={c.id} value={c.id}>
                      {c.nombre}
                    </MenuItem>
                  ))}
                </Select>
                <FormHelperText>{errors.categoriaId?.message ?? ' '}</FormHelperText>
              </FormControl>
            )}
          />

          <Controller
            name="prioridad"
            control={control}
            render={({ field }) => (
              <FormControl fullWidth required error={!!errors.prioridad}>
                <InputLabel id="prioridad-label">Prioridad</InputLabel>
                <Select {...field} labelId="prioridad-label" label="Prioridad">
                  {PRIORIDADES.map((p) => (
                    <MenuItem key={p} value={p}>
                      {ETIQUETA_PRIORIDAD[p]}
                    </MenuItem>
                  ))}
                </Select>
                <FormHelperText>{errors.prioridad?.message ?? ' '}</FormHelperText>
              </FormControl>
            )}
          />

          <Stack direction="row" justifyContent="flex-end">
            <Button type="submit" variant="contained" disabled={enviando}>
              {enviando ? 'Registrando…' : 'Registrar solicitud'}
            </Button>
          </Stack>
        </Stack>
      </Paper>
    </Stack>
  );
}
