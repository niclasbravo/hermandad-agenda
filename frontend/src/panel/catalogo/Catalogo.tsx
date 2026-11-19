import { useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { Ban, Check, Pencil, Plus, UserPlus } from 'lucide-react';
import { api, ErrorDeApi } from '../../compartido/api';
import { Cargando, MensajeError, Modal } from '../../compartido/componentes';
import type { Policlinico, Profesion, Profesional } from '../../compartido/tipos';
import { Cabecera } from '../DisenoPanel';
import { usePoliclinicos, useProfesionales, useProfesiones } from '../consultas';

type Pestana = 'profesionales' | 'policlinicos';

/** M-01 Policlínicos y profesionales (222:7759 y estados). CU-01: RF-01, RF-02, RF-03, RF-05. */
export function Catalogo() {
  const [pestana, setPestana] = useState<Pestana>('profesionales');
  const [formProf, setFormProf] = useState<{ p?: Profesional } | null>(null);
  const [duracion, setDuracion] = useState<Profesion | null>(null);
  const [formPol, setFormPol] = useState<{ p?: Policlinico } | null>(null);
  const [aviso, setAviso] = useState<string | null>(null);
  const profesionales = useProfesionales();
  const profesiones = useProfesiones();
  const policlinicos = usePoliclinicos();
  const qc = useQueryClient();
  const desactivar = useMutation({
    mutationFn: (p: Profesional) => api.post(`/api/profesionales/${p.id}/desactivar`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['catalogo'] }),
  });

  return (
    <>
      <Cabecera titulo="Policlínicos y profesionales" subtitulo="Catálogo base para definir bloques horarios y generar cupos" />
      <div className="cuerpo">
        <div className="pestanas" role="tablist">
          <button role="tab" aria-selected={pestana === 'profesionales'} onClick={() => setPestana('profesionales')}>Profesionales</button>
          <button role="tab" aria-selected={pestana === 'policlinicos'} onClick={() => setPestana('policlinicos')}>Policlínicos y box</button>
        </div>
        {aviso && <div className="aviso aviso--exito" role="status"><Check size={20} aria-hidden="true" /> {aviso}</div>}

        {pestana === 'profesionales' && (
          <div style={{ display: 'grid', gridTemplateColumns: 'minmax(0, 2fr) minmax(280px, 1fr)', gap: 20, alignItems: 'start' }} className="reserva-grilla">
            <section className="tarjeta tarjeta--padding pila">
              <div className="fila fila--entre"><h2>Profesionales registrados</h2>
                <button className="boton" onClick={() => setFormProf({})}><UserPlus size={20} aria-hidden="true" /> Registrar profesional</button></div>
              {profesionales.isLoading && <Cargando />}
              <MensajeError error={desactivar.error} />
              <div className="tabla-contenedor">
                <table className="tabla">
                  <thead><tr><th>Nombre</th><th>Profesión</th><th>Policlínicos</th><th>Credencial</th><th><span className="sr-only">Acciones</span></th></tr></thead>
                  <tbody>
                    {profesionales.data?.map((p) => (
                      <tr key={p.id} style={p.activo ? undefined : { opacity: .55 }}>
                        <td><span className="texto-fuerte">{p.nombreCompleto}</span>{p.esAlumnoPractica && <><br /><span className="chip chip--info">Alumno en práctica</span></>}
                          {!p.activo && <><br /><span className="chip chip--gris">Desactivado</span></>}</td>
                        <td>{p.profesion.nombre}<br /><span className="texto-pequeno texto-secundario">{p.duracionAtencion ? `${p.duracionAtencion} min` : 'Duración por definir'}</span>
                          {p.aviso && p.activo && <><br /><span className="chip chip--alerta">Sin cupos</span></>}</td>
                        <td>{p.policlinicos.map((x) => x.nombre).join(', ')}</td>
                        <td className="texto-pequeno">{p.credencial.tituloProfesional ?? <span className="texto-secundario">Sin credencial</span>}
                          {p.credencial.casaEstudios && <><br />{p.credencial.casaEstudios}</>}
                          {p.credencial.registroPrestador && <><br />Registro {p.credencial.registroPrestador}</>}</td>
                        <td>{p.activo && <div className="fila" style={{ gap: 6, flexWrap: 'nowrap' }}>
                          <button className="boton boton--texto boton--compacto" onClick={() => setFormProf({ p })} aria-label={`Editar a ${p.nombreCompleto}`}><Pencil size={18} /></button>
                          <button className="boton boton--texto boton--compacto" onClick={() => { if (confirm(`¿Desactivar a ${p.nombreCompleto}? No se borra: queda en el historial.`)) desactivar.mutate(p); }} aria-label={`Desactivar a ${p.nombreCompleto}`}><Ban size={18} /></button>
                        </div>}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </section>
            <section className="tarjeta tarjeta--padding pila--chica">
              <h2>Duración de atención por profesión</h2>
              <p className="texto-secundario texto-pequeno">Define cuántos cupos genera cada bloque (RN-04).</p>
              <ul style={{ listStyle: 'none', margin: 0, padding: 0 }}>
                {profesiones.data?.map((x) => (
                  <li key={x.id} className="fila fila--entre" style={{ padding: '10px 0', borderBottom: '1px solid var(--color-renglon)' }}>
                    <span>{x.nombre}{x.duracionAlumnoMin && <><br /><span className="texto-pequeno texto-secundario">Alumnos: {x.duracionAlumnoMin} min</span></>}</span>
                    <span className="fila" style={{ gap: 6 }}>
                      {x.duracionMin ? <span className="texto-fuerte">{x.duracionMin} min</span> : <span className="chip chip--alerta">Por definir</span>}
                      <button className="boton boton--texto boton--compacto" onClick={() => setDuracion(x)} aria-label={`Modificar duración de ${x.nombre}`}><Pencil size={16} /></button>
                    </span>
                  </li>
                ))}
              </ul>
            </section>
          </div>
        )}

        {pestana === 'policlinicos' && (
          <section className="tarjeta tarjeta--padding pila">
            <div className="fila fila--entre"><h2>Policlínicos y box</h2>
              <button className="boton" onClick={() => setFormPol({})}><Plus size={20} aria-hidden="true" /> Registrar policlínico</button></div>
            <div className="tabla-contenedor">
              <table className="tabla">
                <thead><tr><th>Policlínico</th><th>Dirección</th><th>Box</th><th>Estado</th><th><span className="sr-only">Acciones</span></th></tr></thead>
                <tbody>
                  {policlinicos.data?.map((p) => (
                    <tr key={p.id}>
                      <td className="texto-fuerte">Policlínico {p.nombre}</td>
                      <td>{p.direccion}, {p.comuna}</td>
                      <td>{p.boxes.filter((b) => b.activo).length}<br /><span className="texto-pequeno texto-secundario">{p.boxes.map((b) => b.nombre).join(', ')}</span></td>
                      <td>{p.activo ? <span className="chip chip--exito">Activo</span> : <span className="chip chip--gris">Inactivo</span>}</td>
                      <td><button className="boton boton--texto boton--compacto" onClick={() => setFormPol({ p })} aria-label={`Editar ${p.nombre}`}><Pencil size={18} /></button></td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </section>
        )}
      </div>
      {formProf && <ModalProfesional actual={formProf.p} alCerrar={() => setFormProf(null)} alGuardar={(p) => setAviso(p.aviso ?? `${p.nombreCompleto} quedó registrado.`)} />}
      {duracion && <ModalDuracion profesion={duracion} alCerrar={() => setDuracion(null)} />}
      {formPol && <ModalPoliclinico actual={formPol.p} alCerrar={() => setFormPol(null)} />}
    </>
  );
}

const esquemaProfesional = z.object({
  nombres: z.string().trim().min(1, 'Escribe los nombres.'),
  apellidos: z.string().trim().min(1, 'Escribe los apellidos.'),
  idProfesion: z.coerce.number({ invalid_type_error: 'Elige la profesión.' }).min(1, 'Elige la profesión.'),
  esAlumnoPractica: z.boolean(),
  idsPoliclinico: z.array(z.coerce.number()).min(1, 'Selecciona al menos un policlínico.'),
  tituloProfesional: z.string().optional(),
  casaEstudios: z.string().optional(),
  registroPrestador: z.string().optional(),
});
type DatosProfesional = z.infer<typeof esquemaProfesional>;

/** Formulario «Registrar profesional» (222:7979, 222:8045). CU-01 E3: aviso si la profesión no tiene duración. */
function ModalProfesional({ actual, alCerrar, alGuardar }: { actual?: Profesional; alCerrar: () => void; alGuardar: (p: Profesional) => void }) {
  const profesiones = useProfesiones();
  const policlinicos = usePoliclinicos();
  const qc = useQueryClient();
  const { register, handleSubmit, formState: { errors }, watch } = useForm<DatosProfesional>({
    resolver: zodResolver(esquemaProfesional),
    defaultValues: actual ? {
      nombres: actual.nombres, apellidos: actual.apellidos, idProfesion: actual.profesion.id, esAlumnoPractica: actual.esAlumnoPractica,
      idsPoliclinico: actual.policlinicos.map((x) => x.id), tituloProfesional: actual.credencial.tituloProfesional ?? '',
      casaEstudios: actual.credencial.casaEstudios ?? '', registroPrestador: actual.credencial.registroPrestador ?? '',
    } : { nombres: '', apellidos: '', idProfesion: 0, esAlumnoPractica: false, idsPoliclinico: [] },
  });
  const guardar = useMutation({
    mutationFn: (d: DatosProfesional) => actual ? api.put<Profesional>(`/api/profesionales/${actual.id}`, d) : api.post<Profesional>('/api/profesionales', d),
    onSuccess: (p) => { qc.invalidateQueries({ queryKey: ['catalogo'] }); alGuardar(p); alCerrar(); },
  });
  const idProf = Number(watch('idProfesion'));
  const sinDuracion = profesiones.data?.find((p) => p.id === idProf)?.duracionMin == null && idProf > 0;
  return (
    <Modal titulo={actual ? 'Editar profesional' : 'Registrar profesional'} alCerrar={alCerrar} ancho descripcion="Los campos marcados con * son obligatorios">
      <form className="pila" onSubmit={handleSubmit((d) => guardar.mutate(d))} noValidate>
        <h3>Datos del profesional</h3>
        <div className="grilla-2">
          <div className="campo"><label htmlFor="pr-nom">Nombres *</label><input id="pr-nom" className="control" {...register('nombres')} aria-invalid={!!errors.nombres} />
            {errors.nombres && <span className="campo-error">{errors.nombres.message}</span>}</div>
          <div className="campo"><label htmlFor="pr-ape">Apellidos *</label><input id="pr-ape" className="control" {...register('apellidos')} aria-invalid={!!errors.apellidos} />
            {errors.apellidos && <span className="campo-error">{errors.apellidos.message}</span>}</div>
          <div className="campo"><label htmlFor="pr-prof">Profesión *</label>
            <select id="pr-prof" className="control" {...register('idProfesion')} aria-invalid={!!errors.idProfesion}>
              <option value={0}>Elige una profesión</option>
              {profesiones.data?.map((p) => <option key={p.id} value={p.id}>{p.nombre}</option>)}
            </select>
            {errors.idProfesion && <span className="campo-error">{errors.idProfesion.message}</span>}</div>
          <label className="casilla" style={{ alignSelf: 'end', minHeight: 48, alignItems: 'center' }}><input type="checkbox" {...register('esAlumnoPractica')} /> Alumno en práctica</label>
        </div>
        {sinDuracion && <div className="aviso aviso--alerta">No se generarán cupos hasta definir la duración de {profesiones.data?.find((p) => p.id === idProf)?.nombre}.</div>}
        <fieldset className="pila--chica" style={{ border: 0, padding: 0, margin: 0 }}>
          <legend className="etiqueta" style={{ marginBottom: 6 }}>Policlínicos donde atiende *</legend>
          <div className="grilla-3">
            {policlinicos.data?.map((p) => <label key={p.id} className="casilla"><input type="checkbox" value={p.id} {...register('idsPoliclinico')} /> Policlínico {p.nombre}</label>)}
          </div>
          {errors.idsPoliclinico && <span className="campo-error">{errors.idsPoliclinico.message}</span>}
        </fieldset>
        <h3>Credencial (opcional)</h3>
        <p className="texto-secundario texto-pequeno">Puedes completarla más tarde. No impide registrar al profesional.</p>
        <div className="grilla-3">
          <div className="campo"><label htmlFor="pr-tit">Título</label><input id="pr-tit" className="control" {...register('tituloProfesional')} /></div>
          <div className="campo"><label htmlFor="pr-casa">Casa de estudios</label><input id="pr-casa" className="control" {...register('casaEstudios')} /></div>
          <div className="campo"><label htmlFor="pr-reg">Registro de prestador (Superintendencia)</label><input id="pr-reg" className="control" {...register('registroPrestador')} /></div>
        </div>
        <MensajeError error={guardar.error} />
        <div className="modal__acciones">
          <button type="button" className="boton boton--secundario" onClick={alCerrar}>Cancelar</button>
          <button type="submit" className="boton" disabled={guardar.isPending}><Check size={20} aria-hidden="true" /> Guardar profesional</button>
        </div>
      </form>
    </Modal>
  );
}

/** Modal «Modificar duración de …» (222:8321). RF-05: entre 5 y 240 min; rechaza 0 o vacío (CU-01 E2). */
function ModalDuracion({ profesion, alCerrar }: { profesion: Profesion; alCerrar: () => void }) {
  const [min, setMin] = useState(profesion.duracionMin?.toString() ?? '');
  const [alumno, setAlumno] = useState(profesion.duracionAlumnoMin?.toString() ?? '');
  const qc = useQueryClient();
  const m = useMutation({
    mutationFn: () => api.put(`/api/profesiones/${profesion.id}/duracion`, { duracionMin: min ? Number(min) : null, duracionAlumnoMin: alumno ? Number(alumno) : null }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['catalogo'] }); alCerrar(); },
  });
  const campos = m.error instanceof ErrorDeApi ? m.error.campos : {};
  return (
    <Modal titulo={`Modificar duración de ${profesion.nombre}`} alCerrar={alCerrar} descripcion="Cada bloque horario se dividirá en cupos de esta duración.">
      <div className="grilla-2">
        <div className="campo"><label htmlFor="d-min">Minutos por paciente *</label>
          <input id="d-min" type="number" min={5} max={240} className="control" value={min} onChange={(e) => setMin(e.target.value)} aria-invalid={!!campos.duracionMin} />
          {campos.duracionMin && <span className="campo-error">{campos.duracionMin}</span>}</div>
        <div className="campo"><label htmlFor="d-alu">Minutos con alumno en práctica</label>
          <input id="d-alu" type="number" min={5} max={240} className="control" value={alumno} onChange={(e) => setAlumno(e.target.value)} aria-invalid={!!campos.duracionAlumnoMin} />
          {campos.duracionAlumnoMin && <span className="campo-error">{campos.duracionAlumnoMin}</span>}</div>
      </div>
      <p className="texto-secundario texto-pequeno">Se usa en los bloques nuevos. Los cupos ya generados mantienen su duración.</p>
      {!campos.duracionMin && !campos.duracionAlumnoMin && <MensajeError error={m.error} />}
      <div className="modal__acciones">
        <button className="boton boton--secundario" onClick={alCerrar}>Cancelar</button>
        <button className="boton" disabled={m.isPending} onClick={() => m.mutate()}><Check size={20} aria-hidden="true" /> Guardar duración</button>
      </div>
    </Modal>
  );
}

function ModalPoliclinico({ actual, alCerrar }: { actual?: Policlinico; alCerrar: () => void }) {
  const [nombre, setNombre] = useState(actual?.nombre ?? '');
  const [direccion, setDireccion] = useState(actual?.direccion ?? '');
  const [comuna, setComuna] = useState(actual?.comuna ?? '');
  const [activo, setActivo] = useState(actual?.activo ?? true);
  const [cantidadBox, setCantidadBox] = useState(3);
  const [nuevoBox, setNuevoBox] = useState('');
  const qc = useQueryClient();
  const guardar = useMutation({
    mutationFn: () => actual
      ? api.put(`/api/policlinicos/${actual.id}`, { nombre, direccion, comuna, activo })
      : api.post('/api/policlinicos', { nombre, direccion, comuna, boxes: Array.from({ length: cantidadBox }, (_, i) => `Box ${i + 1}`) }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['catalogo'] }); alCerrar(); },
  });
  const agregarBox = useMutation({
    mutationFn: () => api.post(`/api/policlinicos/${actual!.id}/box`, { nombre: nuevoBox }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['catalogo'] }); setNuevoBox(''); },
  });
  const campos = guardar.error instanceof ErrorDeApi ? guardar.error.campos : {};
  return (
    <Modal titulo={actual ? `Policlínico ${actual.nombre}` : 'Registrar policlínico'} alCerrar={alCerrar}>
      <div className="campo"><label htmlFor="pol-nom">Nombre *</label><input id="pol-nom" className="control" value={nombre} onChange={(e) => setNombre(e.target.value)} aria-invalid={!!campos.nombre} />
        {campos.nombre && <span className="campo-error">{campos.nombre}</span>}</div>
      <div className="campo"><label htmlFor="pol-dir">Dirección *</label><input id="pol-dir" className="control" value={direccion} onChange={(e) => setDireccion(e.target.value)} aria-invalid={!!campos.direccion} />
        {campos.direccion && <span className="campo-error">{campos.direccion}</span>}</div>
      <div className="campo"><label htmlFor="pol-com">Comuna *</label><input id="pol-com" className="control" value={comuna} onChange={(e) => setComuna(e.target.value)} aria-invalid={!!campos.comuna} />
        {campos.comuna && <span className="campo-error">{campos.comuna}</span>}</div>
      {actual ? (
        <>
          <label className="casilla"><input type="checkbox" checked={activo} onChange={(e) => setActivo(e.target.checked)} /> Policlínico activo</label>
          <div className="pila--chica">
            <span className="etiqueta">Box: {actual.boxes.map((b) => b.nombre).join(', ')}</span>
            <div className="fila" style={{ flexWrap: 'nowrap' }}>
              <input aria-label="Nombre del nuevo box" className="control" placeholder={`Box ${actual.boxes.length + 1}`} value={nuevoBox} onChange={(e) => setNuevoBox(e.target.value)} />
              <button className="boton boton--secundario" disabled={!nuevoBox.trim() || agregarBox.isPending} onClick={() => agregarBox.mutate()}><Plus size={18} aria-hidden="true" /> Agregar box</button>
            </div>
            <MensajeError error={agregarBox.error} />
          </div>
        </>
      ) : (
        <div className="campo"><label htmlFor="pol-box">Cantidad de box</label><input id="pol-box" type="number" min={1} max={12} className="control" value={cantidadBox} onChange={(e) => setCantidadBox(Number(e.target.value))} /></div>
      )}
      {Object.keys(campos).length === 0 && <MensajeError error={guardar.error} />}
      <div className="modal__acciones">
        <button className="boton boton--secundario" onClick={alCerrar}>Cancelar</button>
        <button className="boton" disabled={guardar.isPending} onClick={() => guardar.mutate()}><Check size={20} aria-hidden="true" /> Guardar</button>
      </div>
    </Modal>
  );
}
