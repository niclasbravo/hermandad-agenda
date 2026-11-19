import { useEffect, useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Ban, Check, ChevronLeft, ChevronRight, Lock, Pencil, Plus, Settings2, X } from 'lucide-react';
import { api, consulta, ErrorDeApi } from '../../compartido/api';
import { Cargando, MensajeError, Modal, Vacio } from '../../compartido/componentes';
import { DIAS_SEMANA, diaCorto, fechaCorta, fechaLarga, hora, hoyIso, lunesDe, rangoSemana, sumarDias } from '../../compartido/formato';
import type { CitaAfectada, CupoVista, Parametro, Profesional } from '../../compartido/tipos';
import { Cabecera } from '../DisenoPanel';
import { useInvalidarAgenda, usePoliclinicos, useProfesionales } from '../consultas';

interface Bloque {
  id: number; idProfesional: number; profesional: string; profesion: string; duracion: number | null; idBox: number; box: string;
  idPoliclinico: number; policlinico: string; diaSemana: number; horaInicio: string; horaTermino: string; vigenciaDesde: string;
  vigenciaHasta: string; activo: boolean;
}
interface Previa { cantidadPorDia: number; duracion: number; horas: string[]; dias: number; totalCupos: number; mensaje: string }
type DatosBloque = { idProfesional: number | ''; idBox: number | ''; diaSemana: number; horaInicio: string; horaTermino: string; vigenciaDesde: string; vigenciaHasta: string };

function finDeSemestre() {
  const h = new Date();
  return h.getMonth() < 6 ? `${h.getFullYear()}-06-30` : `${h.getFullYear()}-12-31`;
}

/** M-02 Agenda de cupos (222:7043 y estados). CU-02, RF-04, RF-06 a RF-08, RF-37, RF-40. Solo Administración. */
export function AgendaCupos() {
  const profesionales = useProfesionales();
  const policlinicos = usePoliclinicos();
  const [vista, setVista] = useState<'PROFESIONAL' | 'BOX'>('PROFESIONAL');
  const [idProfesional, setIdProfesional] = useState<number | ''>('');
  const [idPoliclinico, setIdPoliclinico] = useState<number | ''>('');
  const [idBox, setIdBox] = useState<number | ''>('');
  const [lunes, setLunes] = useState(lunesDe(hoyIso()));
  const [lateral, setLateral] = useState<{ bloque?: Bloque } | null>(null);
  const [bloquear, setBloquear] = useState<CupoVista | null>(null);
  const [reglas, setReglas] = useState(false);
  const [afectadas, setAfectadas] = useState<{ lista: CitaAfectada[]; idBloque: number; reintentar: () => Promise<unknown> } | null>(null);
  const invalidar = useInvalidarAgenda();

  const activos = (profesionales.data ?? []).filter((p) => p.activo);
  useEffect(() => { if (idProfesional === '' && activos.length) setIdProfesional(activos[0].id); }, [activos, idProfesional]);
  const prof = activos.find((p) => p.id === idProfesional);
  useEffect(() => { if (prof && (idPoliclinico === '' || !prof.policlinicos.some((x) => x.id === idPoliclinico))) setIdPoliclinico(prof.policlinicos[0]?.id ?? ''); }, [prof, idPoliclinico]);
  const boxesPol = policlinicos.data?.find((p) => p.id === idPoliclinico)?.boxes ?? [];
  useEffect(() => { if (vista === 'BOX' && (idBox === '' || !boxesPol.some((b) => b.id === idBox)) && boxesPol.length) setIdBox(boxesPol[0].id); }, [vista, boxesPol, idBox]);

  const semana = useQuery({
    queryKey: ['agenda-semana', vista, idProfesional, idPoliclinico, idBox, lunes],
    queryFn: () => vista === 'PROFESIONAL'
      ? api.get<CupoVista[]>('/api/agenda/semana' + consulta({ profesional: idProfesional, policlinico: idPoliclinico, desde: lunes }))
      : api.get<CupoVista[]>('/api/agenda/semana-box' + consulta({ box: idBox, desde: lunes })),
    enabled: vista === 'PROFESIONAL' ? !!idProfesional : !!idBox,
  });
  const bloques = useQuery({
    queryKey: ['agenda-semana', 'bloques', idProfesional],
    queryFn: () => api.get<Bloque[]>('/api/bloques' + consulta({ profesional: idProfesional })),
    enabled: !!idProfesional && vista === 'PROFESIONAL',
  });

  const dias = useMemo(() => {
    const base = [0, 1, 2, 3, 4].map((i) => sumarDias(lunes, i));
    const extra = [5, 6].map((i) => sumarDias(lunes, i)).filter((f) => semana.data?.some((c) => c.fecha === f));
    return [...base, ...extra];
  }, [lunes, semana.data]);

  const desactivar = useMutation({
    mutationFn: (b: Bloque) => api.post(`/api/bloques/${b.id}/desactivar`),
    onSuccess: invalidar,
    onError: (e, b) => {
      if (e instanceof ErrorDeApi && e.codigo === 'RESERVAS_AFECTADAS') {
        setAfectadas({ lista: e.detalles.afectadas as CitaAfectada[], idBloque: b.id, reintentar: () => api.post(`/api/bloques/${b.id}/desactivar`) });
      }
    },
  });

  return (
    <>
      <Cabecera titulo="Agenda de cupos" subtitulo="Bloques horarios semanales y estado de cada cupo">
        <div className="segmentado" role="group" aria-label="Vista">
          <button aria-pressed={vista === 'PROFESIONAL'} onClick={() => setVista('PROFESIONAL')}>Por profesional</button>
          <button aria-pressed={vista === 'BOX'} onClick={() => setVista('BOX')}>Por box</button>
        </div>
      </Cabecera>
      <div className="cuerpo">
        <div className="fila" style={{ alignItems: 'flex-end' }}>
          {vista === 'PROFESIONAL' && (
            <div className="campo" style={{ minWidth: 260 }}>
              <label htmlFor="ac-prof">Profesional</label>
              <select id="ac-prof" className="control" value={idProfesional} onChange={(e) => setIdProfesional(Number(e.target.value))}>
                {activos.map((p) => <option key={p.id} value={p.id}>{p.nombreCompleto} ({p.profesion.nombre})</option>)}
              </select>
            </div>
          )}
          <div className="campo" style={{ minWidth: 200 }}>
            <label htmlFor="ac-pol">Policlínico</label>
            <select id="ac-pol" className="control" value={idPoliclinico} onChange={(e) => setIdPoliclinico(Number(e.target.value))}>
              {(vista === 'PROFESIONAL' && prof ? prof.policlinicos : policlinicos.data ?? []).map((p) => <option key={p.id} value={p.id}>{p.nombre}</option>)}
            </select>
          </div>
          {vista === 'BOX' && (
            <div className="campo" style={{ minWidth: 140 }}>
              <label htmlFor="ac-box">Box</label>
              <select id="ac-box" className="control" value={idBox} onChange={(e) => setIdBox(Number(e.target.value))}>
                {boxesPol.map((b) => <option key={b.id} value={b.id}>{b.nombre}</option>)}
              </select>
            </div>
          )}
          <div className="campo">
            <span className="etiqueta">Semana</span>
            <div className="fila" style={{ gap: 6, flexWrap: 'nowrap' }}>
              <button className="boton boton--gris-borde boton--compacto" aria-label="Semana anterior" onClick={() => setLunes(sumarDias(lunes, -7))}><ChevronLeft size={20} /></button>
              <span className="control" style={{ display: 'flex', alignItems: 'center', whiteSpace: 'nowrap' }}>{rangoSemana(lunes)}</span>
              <button className="boton boton--gris-borde boton--compacto" aria-label="Semana siguiente" onClick={() => setLunes(sumarDias(lunes, 7))}><ChevronRight size={20} /></button>
            </div>
          </div>
          <div className="fila" style={{ marginLeft: 'auto' }}>
            <button className="boton boton--secundario" onClick={() => setReglas(true)}><Settings2 size={20} aria-hidden="true" /> Publicación y reglas</button>
            <button className="boton" onClick={() => setLateral({})}><Plus size={20} aria-hidden="true" /> Nuevo bloque horario</button>
          </div>
        </div>

        <section className="tarjeta tarjeta--padding pila">
          <div className="fila texto-pequeno" style={{ gap: 8 }}>
            <span className="texto-secundario">Estados:</span>
            <span className="chip chip--info">Reservado</span>
            <span className="chip chip--gris">Bloqueado</span>
            <span className="chip" style={{ border: '1.5px solid var(--color-tinta)', color: 'var(--color-tinta)' }}>Disponible</span>
            <span className="chip" style={{ border: '1.5px dashed var(--color-renglon-oscuro)', color: 'var(--color-grafito-2)' }}>No publicado</span>
            <span className="texto-secundario">· Haz clic en un cupo libre para bloquearlo.</span>
          </div>
          {semana.isLoading && <Cargando />}
          <MensajeError error={semana.error} />
          <div className="columnas" style={{ gridAutoColumns: 'minmax(150px, 1fr)' }}>
            {dias.map((f) => {
              const lista = (semana.data ?? []).filter((c) => c.fecha === f);
              return (
                <section key={f} aria-label={fechaLarga(f)}>
                  <div className="columna__cabecera texto-fuerte">{diaCorto(f)}</div>
                  {lista.length === 0 && <div className="cupo cupo--no-publicado centrado texto-secundario">Sin bloques</div>}
                  {lista.map((c) => <CeldaCupo key={c.id} c={c} vista={vista} alBloquear={() => setBloquear(c)} />)}
                </section>
              );
            })}
          </div>
        </section>

        {vista === 'PROFESIONAL' && (
          <section className="tarjeta tarjeta--padding pila--chica">
            <h2>Bloques horarios de {prof?.nombreCompleto}</h2>
            {prof?.aviso && <div className="aviso aviso--alerta">{prof.aviso}</div>}
            {bloques.data?.length === 0 && <Vacio>Este profesional aún no tiene bloques horarios.</Vacio>}
            <MensajeError error={desactivar.error instanceof ErrorDeApi && desactivar.error.codigo !== 'RESERVAS_AFECTADAS' ? desactivar.error : null} />
            <div className="tabla-contenedor">
              <table className="tabla">
                <thead><tr><th>Día</th><th>Horario</th><th>Lugar</th><th>Vigencia</th><th>Acciones</th></tr></thead>
                <tbody>
                  {bloques.data?.map((b) => (
                    <tr key={b.id}>
                      <td>{DIAS_SEMANA[b.diaSemana - 1]}</td>
                      <td><span className="hora">{hora(b.horaInicio)} a {hora(b.horaTermino)}</span><br /><span className="texto-pequeno texto-secundario">cupos de {b.duracion ?? '—'} min</span></td>
                      <td>Policlínico {b.policlinico}, {b.box}</td>
                      <td>{fechaCorta(b.vigenciaDesde)} al {fechaCorta(b.vigenciaHasta)}</td>
                      <td><div className="fila" style={{ gap: 8 }}>
                        <button className="boton boton--secundario boton--compacto" onClick={() => setLateral({ bloque: b })}><Pencil size={16} aria-hidden="true" /> Modificar</button>
                        <button className="boton boton--gris-borde boton--compacto" onClick={() => { if (confirm('¿Desactivar este bloque? Los cupos futuros dejarán de ofrecerse.')) desactivar.mutate(b); }}><Ban size={16} aria-hidden="true" /> Desactivar</button>
                      </div></td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </section>
        )}
      </div>
      {lateral && <LateralBloque bloque={lateral.bloque} profesionales={activos} idProfesionalInicial={idProfesional || undefined}
        alCerrar={() => setLateral(null)} alAfectadas={(lista, idBloque, reintentar) => setAfectadas({ lista, idBloque, reintentar })} />}
      {bloquear && <ModalBloquear cupo={bloquear} alCerrar={() => setBloquear(null)} />}
      {reglas && <ModalReglas profesionales={activos} alCerrar={() => setReglas(false)} />}
      {afectadas && <ModalAfectadas datos={afectadas} alCerrar={() => setAfectadas(null)} />}
    </>
  );
}

function CeldaCupo({ c, vista, alBloquear }: { c: CupoVista; vista: 'PROFESIONAL' | 'BOX'; alBloquear: () => void }) {
  const sub = vista === 'BOX' ? c.profesional : null;
  if (c.estado === 'RESERVADO') {
    return <div className="cupo cupo--reservado"><span className="hora">{hora(c.horaInicio)}</span><br /><span className="texto-pequeno">{c.paciente ? abreviar(c.paciente) : 'Reservado'}</span>{sub && <><br /><span className="texto-pequeno texto-secundario">{sub}</span></>}</div>;
  }
  if (c.estado === 'BLOQUEADO') {
    return <div className="cupo cupo--bloqueado"><span className="hora" style={{ color: 'inherit' }}>{hora(c.horaInicio)}</span><br /><span className="texto-pequeno"><Lock size={12} aria-hidden="true" /> {c.motivoBloqueo}</span></div>;
  }
  if (c.estado === 'PASADO') {
    return <div className="cupo cupo--pasado"><span className="hora" style={{ color: 'inherit' }}>{hora(c.horaInicio)}</span><br /><span className="texto-pequeno">Sin reserva</span></div>;
  }
  return (
    <button className={'cupo' + (c.estado === 'NO_PUBLICADO' ? ' cupo--no-publicado' : '')} onClick={alBloquear}
      aria-label={`Bloquear el cupo de las ${hora(c.horaInicio)} del ${fechaLarga(c.fecha)}`}>
      <span className="hora">{hora(c.horaInicio)}</span><br />
      <span className="texto-pequeno texto-secundario">{c.estado === 'NO_PUBLICADO' ? 'No publicado' : 'Disponible'}{c.habilitadoWeb ? ' · web' : ''}</span>
      {sub && <><br /><span className="texto-pequeno texto-secundario">{sub}</span></>}
    </button>
  );
}

function abreviar(nombre: string) {
  const partes = nombre.split(' ');
  return partes.length > 1 ? `${partes[0][0]}. ${partes[partes.length - 2] ?? partes[1]}` : nombre;
}

function LateralBloque({ bloque, profesionales, idProfesionalInicial, alCerrar, alAfectadas }: {
  bloque?: Bloque; profesionales: Profesional[]; idProfesionalInicial?: number; alCerrar: () => void;
  alAfectadas: (lista: CitaAfectada[], idBloque: number, reintentar: () => Promise<unknown>) => void;
}) {
  const policlinicos = usePoliclinicos();
  const invalidar = useInvalidarAgenda();
  const [d, setD] = useState<DatosBloque>(bloque ? {
    idProfesional: bloque.idProfesional, idBox: bloque.idBox, diaSemana: bloque.diaSemana, horaInicio: hora(bloque.horaInicio),
    horaTermino: hora(bloque.horaTermino), vigenciaDesde: bloque.vigenciaDesde, vigenciaHasta: bloque.vigenciaHasta,
  } : { idProfesional: idProfesionalInicial ?? '', idBox: '', diaSemana: 1, horaInicio: '09:00', horaTermino: '12:00', vigenciaDesde: hoyIso(), vigenciaHasta: finDeSemestre() });
  const prof = profesionales.find((p) => p.id === d.idProfesional);
  const opcionesBox = (policlinicos.data ?? []).filter((p) => prof?.policlinicos.some((x) => x.id === p.id))
    .flatMap((p) => p.boxes.filter((b) => b.activo).map((b) => ({ id: b.id, texto: `Policlínico ${p.nombre}, ${b.nombre}` })));
  useEffect(() => { if (d.idBox === '' && opcionesBox.length) setD((x) => ({ ...x, idBox: opcionesBox[0].id })); }, [opcionesBox, d.idBox]);

  const completo = d.idProfesional !== '' && d.idBox !== '' && d.horaInicio && d.horaTermino && d.vigenciaDesde && d.vigenciaHasta;
  const previa = useQuery({
    queryKey: ['previa-bloque', d],
    queryFn: () => api.post<Previa>('/api/bloques/previsualizar', d),
    enabled: !!completo,
    retry: false,
  });
  const guardar = useMutation({
    mutationFn: () => bloque ? api.put(`/api/bloques/${bloque.id}`, d) : api.post('/api/bloques', d),
    onSuccess: () => { invalidar(); alCerrar(); },
    onError: (e) => {
      if (bloque && e instanceof ErrorDeApi && e.codigo === 'RESERVAS_AFECTADAS') {
        alAfectadas(e.detalles.afectadas as CitaAfectada[], bloque.id, () => api.put(`/api/bloques/${bloque.id}`, d));
        alCerrar();
      }
    },
  });
  const set = (k: keyof DatosBloque, v: string | number) => setD((x) => ({ ...x, [k]: v }));
  return (
    <aside className="lateral" aria-label={bloque ? 'Modificar bloque horario' : 'Nuevo bloque horario'}>
      <div className="fila fila--entre"><h2>{bloque ? 'Modificar bloque horario' : 'Nuevo bloque horario'}</h2>
        <button className="boton boton--texto" onClick={alCerrar} aria-label="Cerrar"><X size={22} /></button></div>
      <div className="campo"><label htmlFor="b-prof">Profesional</label>
        <select id="b-prof" className="control" value={d.idProfesional} onChange={(e) => setD((x) => ({ ...x, idProfesional: Number(e.target.value), idBox: '' }))}>
          <option value="">Elige un profesional</option>
          {profesionales.map((p) => <option key={p.id} value={p.id}>{p.nombreCompleto} ({p.profesion.nombre})</option>)}
        </select></div>
      <div className="campo"><label htmlFor="b-box">Policlínico y box</label>
        <select id="b-box" className="control" value={d.idBox} onChange={(e) => set('idBox', Number(e.target.value))}>
          {opcionesBox.map((o) => <option key={o.id} value={o.id}>{o.texto}</option>)}
        </select></div>
      <div className="campo"><label htmlFor="b-dia">Día</label>
        <select id="b-dia" className="control" value={d.diaSemana} onChange={(e) => set('diaSemana', Number(e.target.value))}>
          {DIAS_SEMANA.map((n, i) => <option key={n} value={i + 1}>{n}</option>)}
        </select></div>
      <div className="grilla-2">
        <div className="campo"><label htmlFor="b-ini">Hora de inicio</label><input id="b-ini" type="time" className="control" value={d.horaInicio} onChange={(e) => set('horaInicio', e.target.value)} /></div>
        <div className="campo"><label htmlFor="b-fin">Hora de término</label><input id="b-fin" type="time" className="control" value={d.horaTermino} onChange={(e) => set('horaTermino', e.target.value)} /></div>
      </div>
      <div className="grilla-2">
        <div className="campo"><label htmlFor="b-desde">Vigente desde</label><input id="b-desde" type="date" className="control" value={d.vigenciaDesde} onChange={(e) => set('vigenciaDesde', e.target.value)} /></div>
        <div className="campo"><label htmlFor="b-hasta">Hasta</label><input id="b-hasta" type="date" className="control" value={d.vigenciaHasta} onChange={(e) => set('vigenciaHasta', e.target.value)} /></div>
      </div>
      {previa.data && (
        <div className="aviso aviso--info pila--chica" style={{ flexDirection: 'column', gap: 4 }} role="status">
          <strong>Se generarán {previa.data.cantidadPorDia} cupos de {previa.data.duracion} minutos</strong>
          <span>{previa.data.mensaje.replace(/^Se generarán \d+ cupos de \d+ minutos: /, '')}</span>
          <span className="texto-pequeno texto-secundario">{previa.data.totalCupos} cupos en total, en {previa.data.dias} {previa.data.dias === 1 ? 'semana' : 'semanas'}.</span>
        </div>
      )}
      <MensajeError error={previa.error} />
      {bloque && <p className="texto-secundario texto-pequeno">Si el cambio deja sin horario a pacientes con reserva, verás la lista para reasignarlas o anularlas con aviso antes de guardar.</p>}
      <MensajeError error={guardar.error instanceof ErrorDeApi && guardar.error.codigo === 'RESERVAS_AFECTADAS' ? null : guardar.error} />
      <div className="fila" style={{ marginTop: 'auto', justifyContent: 'flex-end' }}>
        <button className="boton boton--secundario" onClick={alCerrar}>Cancelar</button>
        <button className="boton" disabled={!completo || !!previa.error || guardar.isPending} onClick={() => guardar.mutate()}><Check size={20} aria-hidden="true" /> Guardar bloque</button>
      </div>
    </aside>
  );
}

/** Modal «Bloquear cupo u horario» (222:7500). D-12: solo Administración y siempre con motivo. */
function ModalBloquear({ cupo, alCerrar }: { cupo: CupoVista; alCerrar: () => void }) {
  const [motivo, setMotivo] = useState('');
  const [rango, setRango] = useState(false);
  const [hasta, setHasta] = useState(hora(cupo.horaTermino));
  const [error, setError] = useState<string | null>(null);
  const invalidar = useInvalidarAgenda();
  const m = useMutation({
    mutationFn: () => api.post('/api/bloqueos', rango
      ? { idProfesional: cupo.idProfesional, fecha: cupo.fecha, desde: hora(cupo.horaInicio), hasta, motivo }
      : { idsCupo: [cupo.id], motivo }),
    onSuccess: () => { invalidar(); alCerrar(); },
  });
  return (
    <Modal titulo="Bloquear cupo u horario" alCerrar={alCerrar}
      descripcion={`${cupo.profesional}, Policlínico ${cupo.policlinico}, ${cupo.box}. Los cupos bloqueados dejan de aparecer en la búsqueda de horas.`}>
      <div className="texto-fuerte">{fechaLarga(cupo.fecha)}, {hora(cupo.horaInicio)}</div>
      <label className="casilla"><input type="checkbox" checked={rango} onChange={(e) => setRango(e.target.checked)} /> Bloquear un rango horario de ese día</label>
      {rango && <div className="campo"><label htmlFor="bl-hasta">Hasta</label><input id="bl-hasta" type="time" className="control" value={hasta} onChange={(e) => setHasta(e.target.value)} /></div>}
      <div className="campo">
        <label htmlFor="bl-motivo">Motivo *</label>
        <input id="bl-motivo" className="control" value={motivo} onChange={(e) => setMotivo(e.target.value)} placeholder="Ej.: Reunión clínica"
          aria-invalid={!!error} aria-describedby={error ? 'bl-motivo-error' : undefined} />
        {error && <span id="bl-motivo-error" className="campo-error">{error}</span>}
      </div>
      <MensajeError error={m.error} />
      <div className="modal__acciones">
        <button className="boton boton--secundario" onClick={alCerrar}>Cancelar</button>
        <button className="boton" disabled={m.isPending} onClick={() => { if (!motivo.trim()) { setError('Escribe el motivo del bloqueo.'); return; } m.mutate(); }}>
          <Lock size={18} aria-hidden="true" /> Bloquear</button>
      </div>
    </Modal>
  );
}

const PLAZOS = [[7, 'Hasta 1 semana antes'], [14, 'Hasta 2 semanas antes'], [21, 'Hasta 3 semanas antes'], [28, 'Hasta 4 semanas antes']] as const;

/** Modal «Publicación y reglas de reserva» (222:7621), con los refuerzos y el umbral de inasistencia. */
function ModalReglas({ profesionales, alCerrar }: { profesionales: Profesional[]; alCerrar: () => void }) {
  const qc = useQueryClient();
  const parametros = useQuery({ queryKey: ['parametros'], queryFn: () => api.get<Parametro[]>('/api/parametros') });
  const [plazos, setPlazos] = useState<Record<number, number>>(() => Object.fromEntries(profesionales.map((p) => [p.id, p.plazoPublicacionDias])));
  const [valores, setValores] = useState<Record<string, string>>({});
  useEffect(() => {
    if (parametros.data) setValores(Object.fromEntries(parametros.data.map((p) => [p.clave, p.valor ?? ''])));
  }, [parametros.data]);
  const guardar = useMutation({
    mutationFn: async () => {
      for (const p of profesionales) {
        if (plazos[p.id] !== p.plazoPublicacionDias) await api.put(`/api/profesionales/${p.id}/publicacion`, { plazoPublicacionDias: plazos[p.id] });
      }
      await api.put('/api/parametros', {
        PORCENTAJE_CUPOS_WEB: valores.PORCENTAJE_CUPOS_WEB, TOPE_RESERVAS_WEB: valores.TOPE_RESERVAS_WEB,
        REFUERZOS_CANTIDAD: valores.REFUERZOS_CANTIDAD, REFUERZOS_HORAS_ANTES: valores.REFUERZOS_HORAS_ANTES,
        UMBRAL_INASISTENCIA: valores.UMBRAL_INASISTENCIA,
      });
    },
    onSuccess: () => { qc.invalidateQueries(); alCerrar(); },
  });
  const v = (k: string) => valores[k] ?? '';
  const set = (k: string, x: string) => setValores((s) => ({ ...s, [k]: x }));
  return (
    <Modal titulo="Publicación y reglas de reserva" alCerrar={alCerrar} ancho
      descripcion="La agenda se crea para todo el semestre, pero al público se le muestra solo hasta cierto plazo. Los cupos se publican solos, semana a semana.">
      {parametros.isLoading && <Cargando />}
      <div className="grilla-2">
        {profesionales.map((p) => (
          <div key={p.id} className="campo">
            <label htmlFor={'plazo-' + p.id}>{p.nombreCompleto} ({p.profesion.nombre}): mostrar al público</label>
            <select id={'plazo-' + p.id} className="control" value={plazos[p.id]} onChange={(e) => setPlazos((s) => ({ ...s, [p.id]: Number(e.target.value) }))}>
              {!PLAZOS.some(([d]) => d === plazos[p.id]) && <option value={plazos[p.id]}>Hasta {plazos[p.id]} días antes</option>}
              {PLAZOS.map(([d, t]) => <option key={d} value={d}>{t}</option>)}
            </select>
          </div>
        ))}
      </div>
      <hr className="separador" />
      <div className="grilla-2">
        <div className="campo">
          <label htmlFor="p-web">Cupos ofrecidos en la reserva en línea (% de cada bloque)</label>
          <input id="p-web" className="control" type="number" min={0} max={100} value={v('PORCENTAJE_CUPOS_WEB')} placeholder="Sin configurar (0 %)" onChange={(e) => set('PORCENTAJE_CUPOS_WEB', e.target.value)} />
          <span className="campo-ayuda">Al cambiarlo se recalculan los cupos futuros no reservados.</span>
        </div>
        <div className="campo">
          <label htmlFor="p-tope">Tope de horas en línea por paciente (opcional)</label>
          <input id="p-tope" className="control" type="number" min={1} value={v('TOPE_RESERVAS_WEB')} placeholder="Sin tope" onChange={(e) => set('TOPE_RESERVAS_WEB', e.target.value)} />
        </div>
        <div className="campo">
          <label htmlFor="p-ref">Refuerzos del recordatorio (cantidad)</label>
          <input id="p-ref" className="control" type="number" min={0} max={5} value={v('REFUERZOS_CANTIDAD')} placeholder="Sin refuerzos" onChange={(e) => set('REFUERZOS_CANTIDAD', e.target.value)} />
        </div>
        <div className="campo">
          <label htmlFor="p-refh">Horas antes de la cita de cada refuerzo</label>
          <input id="p-refh" className="control" value={v('REFUERZOS_HORAS_ANTES')} placeholder="Ej.: 48,4" onChange={(e) => set('REFUERZOS_HORAS_ANTES', e.target.value)} />
          <span className="campo-ayuda">Separadas por coma, una por refuerzo.</span>
        </div>
        <div className="campo">
          <label htmlFor="p-umbral">Umbral de inasistencia reiterada</label>
          <input id="p-umbral" className="control" type="number" min={1} value={v('UMBRAL_INASISTENCIA')} onChange={(e) => set('UMBRAL_INASISTENCIA', e.target.value)} />
          <span className="campo-ayuda">Inasistencias en el período desde las que un paciente aparece en el listado.</span>
        </div>
      </div>
      <MensajeError error={guardar.error} />
      <div className="modal__acciones">
        <button className="boton boton--secundario" onClick={alCerrar}>Cancelar</button>
        <button className="boton" disabled={guardar.isPending} onClick={() => guardar.mutate()}><Check size={20} aria-hidden="true" /> Guardar regla</button>
      </div>
    </Modal>
  );
}

/** D-10: reservas afectadas. Para cada una: reasignar a otro cupo o anular con aviso. Solo después se aplica el cambio. */
function ModalAfectadas({ datos, alCerrar }: { datos: { lista: CitaAfectada[]; idBloque: number; reintentar: () => Promise<unknown> }; alCerrar: () => void }) {
  const invalidar = useInvalidarAgenda();
  const [acciones, setAcciones] = useState<Record<number, { accion: 'ANULAR' | 'REASIGNAR'; idCupoNuevo?: number }>>(
    Object.fromEntries(datos.lista.map((a) => [a.idCita, { accion: 'ANULAR' as const }])));
  const detalle = useQuery({
    queryKey: ['cupo-afectado', datos.lista[0]?.idCita],
    queryFn: () => api.get<{ fila: { cita: { idPoliclinico: number; idProfesion: number } } }>(`/api/citas/${datos.lista[0].idCita}`),
    enabled: datos.lista.length > 0,
  });
  const ref = detalle.data?.fila.cita;
  const opciones = useQuery({
    queryKey: ['cupos', 'reasignar', ref?.idPoliclinico, ref?.idProfesion],
    queryFn: () => api.get<CupoVista[]>('/api/cupos/disponibles' + consulta({ policlinico: ref!.idPoliclinico, profesion: ref!.idProfesion, desde: hoyIso(), hasta: sumarDias(hoyIso(), 60) })),
    enabled: !!ref,
  });
  const resolver = useMutation({
    mutationFn: async () => {
      await api.post(`/api/bloques/${datos.idBloque}/resolver-afectadas`, {
        acciones: datos.lista.map((a) => ({ idCita: a.idCita, ...acciones[a.idCita] })),
      });
      await datos.reintentar();
    },
    onSuccess: () => { invalidar(); alCerrar(); },
  });
  const listo = datos.lista.every((a) => acciones[a.idCita].accion === 'ANULAR' || acciones[a.idCita].idCupoNuevo);
  return (
    <Modal titulo="Reservas afectadas por el cambio" alCerrar={alCerrar} ancho
      descripcion="Estas personas tienen hora en los cupos que dejarían de existir. Reasigna cada reserva a otro horario o anúlala con aviso al paciente. Nada se borra en silencio.">
      <ul className="pila--chica" style={{ listStyle: 'none', margin: 0, padding: 0 }}>
        {datos.lista.map((a) => (
          <li key={a.idCita} className="tarjeta pila--chica" style={{ padding: 14, boxShadow: 'none' }}>
            <div className="fila fila--entre"><span><span className="texto-fuerte">{a.paciente}</span> · <span className="texto-secundario">{a.telefono}</span></span>
              <span className="hora">{fechaCorta(a.fecha)} {hora(a.hora)}</span></div>
            <div className="fila">
              <label className="casilla"><input type="radio" name={'acc-' + a.idCita} checked={acciones[a.idCita].accion === 'ANULAR'}
                onChange={() => setAcciones((s) => ({ ...s, [a.idCita]: { accion: 'ANULAR' } }))} /> Anular con aviso</label>
              <label className="casilla"><input type="radio" name={'acc-' + a.idCita} checked={acciones[a.idCita].accion === 'REASIGNAR'}
                onChange={() => setAcciones((s) => ({ ...s, [a.idCita]: { accion: 'REASIGNAR' } }))} /> Reasignar</label>
              {acciones[a.idCita].accion === 'REASIGNAR' && (
                <select aria-label="Nuevo horario" className="control" style={{ maxWidth: 320 }} value={acciones[a.idCita].idCupoNuevo ?? ''}
                  onChange={(e) => setAcciones((s) => ({ ...s, [a.idCita]: { accion: 'REASIGNAR', idCupoNuevo: Number(e.target.value) } }))}>
                  <option value="">Elige el nuevo horario</option>
                  {opciones.data?.map((c) => <option key={c.id} value={c.id}>{fechaCorta(c.fecha)} {hora(c.horaInicio)} · {c.profesional}</option>)}
                </select>
              )}
            </div>
          </li>
        ))}
      </ul>
      <MensajeError error={resolver.error} />
      <div className="modal__acciones">
        <button className="boton boton--secundario" onClick={alCerrar}>Cancelar</button>
        <button className="boton" disabled={!listo || resolver.isPending} onClick={() => resolver.mutate()}>Resolver y aplicar el cambio</button>
      </div>
    </Modal>
  );
}
