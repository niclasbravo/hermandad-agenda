import { useEffect } from 'react';
import { useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQuery } from '@tanstack/react-query';
import { Save } from 'lucide-react';
import { api, ErrorDeApi } from '../../compartido/api';
import { Cargando, MensajeError } from '../../compartido/componentes';
import { formatearRut, rutValido } from '../../compartido/rut';
import type { Paciente } from '../../compartido/tipos';
import { Cabecera } from '../DisenoPanel';
import { useInvalidarAgenda } from '../consultas';
import { CamposPaciente } from './CamposPaciente';
import { aPeticion, esquemaPaciente, type DatosFormulario } from './esquemaPaciente';

const VACIO: DatosFormulario = {
  tipoDocumento: 'RUT', numeroDocumento: '', nombres: '', apellidos: '', fechaNacimiento: '', telefonoCelular: '', correo: '',
  direccion: '', genero: '', nacionalidad: 'Chilena', conAdulto: false,
  adulto: { tipoDocumento: 'RUT', numeroDocumento: '', nombreCompleto: '', telefono: '', relacion: '' },
};

/** M-03 Registrar (222:6549) y Editar (222:5965). */
export function FormularioPaciente() {
  const { id } = useParams();
  const [params] = useSearchParams();
  const edicion = !!id;
  const navegar = useNavigate();
  const invalidar = useInvalidarAgenda();
  const actual = useQuery({ queryKey: ['paciente', Number(id)], queryFn: () => api.get<Paciente>(`/api/pacientes/${id}`), enabled: edicion });
  const { register, handleSubmit, formState: { errors }, watch, reset, setError } = useForm<DatosFormulario>({
    resolver: zodResolver(esquemaPaciente),
    defaultValues: VACIO,
  });

  useEffect(() => {
    const doc = params.get('documento');
    if (!edicion && doc) reset({ ...VACIO, numeroDocumento: rutValido(doc) ? formatearRut(doc) : doc, tipoDocumento: rutValido(doc) ? 'RUT' : 'PASAPORTE' });
  }, [edicion, params, reset]);
  useEffect(() => {
    const p = actual.data;
    if (!p) return;
    reset({
      tipoDocumento: p.tipoDocumento, numeroDocumento: p.documentoFormateado, nombres: p.nombres, apellidos: p.apellidos,
      fechaNacimiento: p.fechaNacimiento, telefonoCelular: p.telefonoFormateado, correo: p.correo ?? '', direccion: p.direccion,
      genero: p.genero, nacionalidad: p.nacionalidad, conAdulto: !!p.adultoResponsable,
      adulto: p.adultoResponsable ? {
        tipoDocumento: p.adultoResponsable.tipoDocumento as 'RUT', numeroDocumento: p.adultoResponsable.documentoFormateado,
        nombreCompleto: p.adultoResponsable.nombreCompleto, telefono: p.adultoResponsable.telefonoFormateado, relacion: p.adultoResponsable.relacion,
      } : VACIO.adulto,
    });
  }, [actual.data, reset]);

  const guardar = useMutation({
    mutationFn: (d: DatosFormulario) => edicion
      ? api.put<Paciente>(`/api/pacientes/${id}`, aPeticion(d))
      : api.post<Paciente>('/api/pacientes', aPeticion(d)),
    onSuccess: (p) => { invalidar(); navegar(`/pacientes/${p.id}`, { state: { aviso: edicion ? 'Datos actualizados.' : 'Paciente registrado.' } }); },
    onError: (e) => {
      if (e instanceof ErrorDeApi) {
        Object.entries(e.campos).forEach(([k, v]) => setError(k as keyof DatosFormulario, { message: v }));
      }
    },
  });
  const existente = guardar.error instanceof ErrorDeApi && guardar.error.codigo === 'DOCUMENTO_DUPLICADO'
    ? (guardar.error.detalles.idPaciente as number) : null;

  if (edicion && actual.isLoading) return <Cargando />;
  return (
    <>
      <Cabecera titulo={edicion ? 'Editar datos del paciente' : 'Registrar paciente'}
        subtitulo={edicion ? 'Los cambios quedan registrados con usuario y fecha.' : 'Datos de ingreso definidos por la institución. Los campos marcados con * son obligatorios.'} />
      <form className="cuerpo" onSubmit={handleSubmit((d) => guardar.mutate(d))} noValidate>
        <CamposPaciente register={register} errors={errors} watch={watch} documentoBloqueado={edicion} />
        {Object.keys(errors).length > 0 && <div className="aviso aviso--error" role="alert">Faltan datos obligatorios o hay datos que corregir. Revisa los campos marcados en rojo.</div>}
        <MensajeError error={guardar.error} />
        {existente && <button type="button" className="enlace" onClick={() => navegar(`/pacientes/${existente}`)}>Ver la ficha del paciente existente</button>}
        <div className="fila" style={{ justifyContent: 'flex-end' }}>
          <button type="button" className="boton boton--secundario" onClick={() => navegar(-1)}>Cancelar</button>
          <button type="submit" className="boton" disabled={guardar.isPending}><Save size={20} aria-hidden="true" /> {edicion ? 'Guardar cambios' : 'Registrar paciente'}</button>
        </div>
      </form>
    </>
  );
}
