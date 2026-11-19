import { useState } from 'react';
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom';
import { useMutation, useQuery } from '@tanstack/react-query';
import { CalendarPlus, Check, ClipboardCheck, Pencil } from 'lucide-react';
import { api } from '../../compartido/api';
import { Cargando, ChipCita, MensajeError, Modal } from '../../compartido/componentes';
import { fechaCorta, fechaDiaMes, hora, hoyIso } from '../../compartido/formato';
import { useSesion } from '../../compartido/sesion';
import type { Cita, Paciente } from '../../compartido/tipos';
import { Cabecera } from '../DisenoPanel';
import { useInvalidarAgenda } from '../consultas';

/** M-03 Ficha del paciente (222:5197), Recién registrado, Reserva no permitida (222:6722) y Habilitado (222:6850). */
export function FichaPaciente() {
  const { id } = useParams();
  const { usuario } = useSesion();
  const esTens = usuario?.perfil === 'TENS';
  const navegar = useNavigate();
  const aviso = (useLocation().state as { aviso?: string } | null)?.aviso;
  const [verificar, setVerificar] = useState(false);
  const [noCumple, setNoCumple] = useState(false);
  const paciente = useQuery({ queryKey: ['paciente', Number(id)], queryFn: () => api.get<Paciente>(`/api/pacientes/${id}`) });
  const historial = useQuery({ queryKey: ['historial', Number(id)], queryFn: () => api.get<Cita[]>(`/api/pacientes/${id}/historial`) });
  const p = paciente.data;

  if (paciente.isLoading) return <Cargando />;
  if (!p) return <div className="cuerpo"><MensajeError error={paciente.error} /></div>;

  const chip = p.estadoHabilitacion === 'HABILITADO'
    ? <span className="chip chip--exito">Habilitada para reservar</span>
    : p.estadoHabilitacion === 'NO_CUMPLE'
      ? <span className="chip chip--error">No cumple requisitos</span>
      : <span className="chip chip--alerta">Sin verificación de requisitos</span>;
  const pendientes = (historial.data ?? []).filter((c) => c.estado === 'PENDIENTE_VERIFICACION');

  return (
    <>
      <Cabecera titulo="Ficha del paciente" subtitulo="Datos, verificación de requisitos e historial" />
      <div className="cuerpo">
        {aviso && <div className="aviso aviso--exito" role="status"><Check size={20} aria-hidden="true" /> {aviso}</div>}
        <section className="tarjeta tarjeta--padding fila fila--entre">
          <div className="pila--chica">
            <h1 style={{ fontSize: '2rem', lineHeight: '2.5rem' }}>{p.nombreCompleto}</h1>
            <div className="fila" style={{ gap: 10 }}>
              <span className="texto-secundario">{p.tipoDocumento === 'RUT' ? 'RUT' : p.tipoDocumento === 'PASAPORTE' ? 'Pasaporte' : 'Documento'} {p.documentoFormateado}, {p.edad} {p.edad === 1 ? 'año' : 'años'}</span>
              {chip}
            </div>
          </div>
          {esTens && (
            <div className="fila">
              <Link className="boton boton--secundario" to={`/pacientes/${p.id}/editar`}><Pencil size={20} aria-hidden="true" /> Editar datos</Link>
              <button className="boton" disabled={!p.habilitado} onClick={() => navegar(`/reservar-cita?paciente=${p.id}`)}
                aria-describedby={!p.habilitado ? 'no-habilitado' : undefined}>
                <CalendarPlus size={20} aria-hidden="true" /> Reservar cita
              </button>
            </div>
          )}
        </section>
        {!p.habilitado && esTens && (
          <div id="no-habilitado" className="aviso aviso--alerta" role="status">
            Reserva no permitida: este paciente aún no tiene registrada la verificación de FONASA y CESFAM. Regístrala antes de reservar.
          </div>
        )}
        <div className="grilla-2" style={{ alignItems: 'start' }}>
          <div className="pila">
            <section className="tarjeta tarjeta--padding">
              <h2 style={{ marginBottom: 8 }}>Datos de ingreso</h2>
              <dl className="tabla" style={{ display: 'grid', gridTemplateColumns: 'minmax(120px, 40%) 1fr', margin: 0 }}>
                {([
                  [p.tipoDocumento === 'RUT' ? 'RUT' : 'Documento', p.documentoFormateado],
                  ['Fecha de nacimiento', `${fechaCorta(p.fechaNacimiento)} (${p.edad} años)`],
                  ['Género', p.genero],
                  ['Nacionalidad', p.nacionalidad],
                  ['Teléfono', p.telefonoFormateado],
                  ['Dirección', p.direccion],
                  ['Correo', p.correo ?? 'No registrado (opcional)'],
                  ['Adulto responsable', p.adultoResponsable
                    ? `${p.adultoResponsable.nombreCompleto}, ${p.adultoResponsable.relacion}, ${p.adultoResponsable.telefonoFormateado}`
                    : 'No tiene'],
                ] as const).map(([k, v]) => (
                  <div key={k} style={{ display: 'contents' }}>
                    <dt className="texto-secundario texto-pequeno" style={{ padding: '10px 0', borderBottom: '1px solid var(--color-renglon)' }}>{k}</dt>
                    <dd style={{ margin: 0, padding: '10px 0', borderBottom: '1px solid var(--color-renglon)' }}>{v}</dd>
                  </div>
                ))}
              </dl>
            </section>
            <section className="tarjeta tarjeta--padding pila--chica">
              <h2>Requisitos de ingreso</h2>
              {p.verificacionVigente ? (
                <>
                  {p.verificacionVigente.resultado === 'HABILITADO'
                    ? <span className="chip chip--exito" style={{ alignSelf: 'flex-start' }}>✓ Verificación registrada</span>
                    : <span className="chip chip--error" style={{ alignSelf: 'flex-start' }}>No cumple</span>}
                  <p className="texto-secundario texto-pequeno">{p.verificacionVigente.descripcion}.</p>
                  {p.verificacionVigente.observacion && <p className="texto-pequeno">Observación: {p.verificacionVigente.observacion}</p>}
                </>
              ) : (
                <p className="texto-secundario">
                  Revisa el certificado de FONASA y el de inscripción en CESFAM que presenta {p.adultoResponsable ? 'el adulto responsable' : 'el paciente'}. Si cumple, registra la verificación.
                </p>
              )}
              {esTens && (
                <button className="boton boton--secundario" style={{ alignSelf: 'flex-start' }} onClick={() => setVerificar(true)}>
                  <ClipboardCheck size={20} aria-hidden="true" /> {p.verificacionVigente ? 'Registrar nueva verificación' : 'Registrar verificación'}
                </button>
              )}
            </section>
          </div>
          <section className="tarjeta tarjeta--padding">
            <h2 style={{ marginBottom: 8 }}>Historial de citas</h2>
            {historial.isLoading && <Cargando />}
            {historial.data?.length === 0 && <p className="texto-secundario">Aún no tiene citas registradas.</p>}
            <ul style={{ listStyle: 'none', margin: 0, padding: 0 }}>
              {historial.data?.map((c) => (
                <li key={c.id} className="fila" style={{ padding: '12px 0', borderBottom: '1px solid var(--color-renglon)', flexWrap: 'nowrap', alignItems: 'flex-start' }}>
                  <span className="hora" style={{ minWidth: 96 }}>{fechaDiaMes(c.fecha)}, {hora(c.horaInicio)}</span>
                  <span style={{ flex: 1 }}><span className="texto-fuerte">{c.profesion}</span><br />
                    <span className="texto-secundario texto-pequeno">{c.profesional}, Policlínico {c.policlinico}</span></span>
                  <ChipCita estado={c.estado} texto={c.estado === 'RESERVADA' && c.fecha < hoyIso() ? 'Sin registro' : undefined} />
                </li>
              ))}
            </ul>
          </section>
        </div>
      </div>
      {verificar && <ModalRegistrarVerificacion paciente={p} alCerrar={() => setVerificar(false)} alNoCumple={() => setNoCumple(true)} />}
      {noCumple && <ModalNoCumple paciente={p} pendientes={pendientes} alCerrar={() => setNoCumple(false)} />}
    </>
  );
}

/** Modal «Registrar verificación» (222:6829). HABILITADO solo si ambas casillas están marcadas (RF-13). */
function ModalRegistrarVerificacion({ paciente, alCerrar, alNoCumple }: { paciente: Paciente; alCerrar: () => void; alNoCumple: () => void }) {
  const [fonasa, setFonasa] = useState(false);
  const [cesfamOk, setCesfamOk] = useState(false);
  const [cesfam, setCesfam] = useState('');
  const [fecha, setFecha] = useState(hoyIso());
  const [observacion, setObservacion] = useState('');
  const [errorCesfam, setErrorCesfam] = useState<string | null>(null);
  const invalidar = useInvalidarAgenda();
  const m = useMutation({
    mutationFn: () => api.post<Paciente>(`/api/pacientes/${paciente.id}/verificaciones`, {
      fechaVerificacion: fecha, cesfamInscripcion: cesfam, fonasaVigente: fonasa, inscritoCesfam: cesfamOk, observacion,
    }),
    onSuccess: (p) => { invalidar(); alCerrar(); if (!p.habilitado) alNoCumple(); },
  });
  return (
    <Modal titulo="Registrar verificación de requisitos" alCerrar={alCerrar}
      descripcion={`Confirma que revisaste los certificados que presentó ${paciente.adultoResponsable ? 'el adulto responsable de ' : ''}${paciente.nombreCompleto}.`}>
      <label className="casilla"><input type="checkbox" checked={fonasa} onChange={(e) => setFonasa(e.target.checked)} /> Certificado de afiliación a FONASA</label>
      <label className="casilla"><input type="checkbox" checked={cesfamOk} onChange={(e) => setCesfamOk(e.target.checked)} /> Certificado de inscripción en CESFAM</label>
      <div className="grilla-2">
        <div className="campo">
          <label htmlFor="v-cesfam">CESFAM de inscripción *</label>
          <input id="v-cesfam" className="control" value={cesfam} onChange={(e) => setCesfam(e.target.value)} placeholder="Ej.: CESFAM Norte"
            aria-invalid={!!errorCesfam} aria-describedby={errorCesfam ? 'v-cesfam-error' : undefined} />
          {errorCesfam && <span id="v-cesfam-error" className="campo-error">{errorCesfam}</span>}
        </div>
        <div className="campo">
          <label htmlFor="v-fecha">Fecha de verificación</label>
          <input id="v-fecha" type="date" className="control" value={fecha} max={hoyIso()} onChange={(e) => setFecha(e.target.value)} />
        </div>
      </div>
      <div className="campo">
        <label htmlFor="v-obs">Observación (opcional)</label>
        <input id="v-obs" className="control" value={observacion} onChange={(e) => setObservacion(e.target.value)} />
      </div>
      {(!fonasa || !cesfamOk) && <p className="texto-secundario texto-pequeno">Si falta alguno de los dos certificados, la verificación queda como «No cumple».</p>}
      <MensajeError error={m.error} />
      <div className="modal__acciones">
        <button className="boton boton--secundario" onClick={alCerrar}>Cancelar</button>
        <button className="boton" disabled={m.isPending} onClick={() => {
          if (!cesfam.trim()) { setErrorCesfam('Escribe el CESFAM de inscripción.'); return; }
          m.mutate();
        }}>Registrar</button>
      </div>
    </Modal>
  );
}

/** D-03: si no cumple, se ofrece anular las citas pendientes de verificación. */
function ModalNoCumple({ paciente, pendientes, alCerrar }: { paciente: Paciente; pendientes: Cita[]; alCerrar: () => void }) {
  const invalidar = useInvalidarAgenda();
  const m = useMutation({
    mutationFn: async () => {
      for (const c of pendientes) await api.post(`/api/citas/${c.id}/anular`, { origen: 'TENS', motivo: 'No cumple los requisitos de FONASA y CESFAM (D-03)' });
    },
    onSuccess: () => { invalidar(); alCerrar(); },
  });
  return (
    <Modal titulo="No puede ser atendido" alCerrar={alCerrar}>
      <div className="aviso aviso--alerta">No puede ser atendido: oriente su inscripción en el CESFAM.</div>
      {pendientes.length > 0 && <p>{paciente.nombreCompleto} tiene {pendientes.length} {pendientes.length === 1 ? 'reserva pendiente' : 'reservas pendientes'} de verificación. ¿Anularlas?</p>}
      <MensajeError error={m.error} />
      <div className="modal__acciones">
        <button className="boton boton--secundario" onClick={alCerrar}>Cerrar</button>
        {pendientes.length > 0 && <button className="boton boton--peligro" disabled={m.isPending} onClick={() => m.mutate()}>Anular reservas pendientes</button>}
      </div>
    </Modal>
  );
}
