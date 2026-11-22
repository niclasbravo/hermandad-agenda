import { useState } from 'react';
import { useMutation, useQuery } from '@tanstack/react-query';
import { MessageCircle, Phone, Sparkles, X, FileText } from 'lucide-react';
import { api, ErrorDeApi } from '../../compartido/api';
import { Cargando, ChipCita, ChipConfirmacion, MensajeError, Modal, Obligatorio } from '../../compartido/componentes';
import { capitalizar, diaCorto, fechaLarga, fechaHoraCorta, hora, hoyIso } from '../../compartido/formato';
import type { Cita, DetalleCita, Documento } from '../../compartido/tipos';
import { useInvalidarAgenda } from '../consultas';
import { useAviso } from '../../compartido/avisos';

function cuandoTexto(c: Cita) {
  return c.fecha === hoyIso() ? `hoy ${fechaLarga(c.fecha).toLowerCase()} a las ${hora(c.horaInicio)}` : `el ${fechaLarga(c.fecha).toLowerCase()} a las ${hora(c.horaInicio)}`;
}

/** Modal «Anular cita» (222:2639). Motivo opcional (RF-17). */
export function ModalAnular({ cita, alCerrar, alAnular }: { cita: Cita; alCerrar: () => void; alAnular?: () => void }) {
  const [motivo, setMotivo] = useState('');
  const invalidar = useInvalidarAgenda();
  const avisar = useAviso();
  const m = useMutation({
    mutationFn: () => api.post(`/api/citas/${cita.id}/anular`, { origen: 'TENS', motivo }),
    onSuccess: () => { invalidar(); alAnular?.(); alCerrar(); avisar('Cita anulada', { detalle: 'Se avisó al paciente por WhatsApp y el cupo quedó libre.', tipo: 'info' }); },
  });
  return (
    <Modal titulo={`¿Anular la cita de ${cita.paciente}?`} alCerrar={alCerrar}
      descripcion={`${cita.profesion} con ${cita.profesional}, ${cuandoTexto(cita)}, ${cita.box}.`}>
      <div className="campo">
        <label htmlFor="motivo-anular">Motivo (opcional)</label>
        <input id="motivo-anular" className="control" value={motivo} onChange={(e) => setMotivo(e.target.value)} placeholder="Ej.: el paciente avisó que no puede venir" />
      </div>
      <p className="texto-secundario texto-pequeno">El horario volverá a quedar disponible. Si hay personas en lista de espera, verás un aviso.</p>
      <MensajeError error={m.error} />
      <div className="modal__acciones">
        <button className="boton boton--secundario" onClick={alCerrar}>Cancelar</button>
        <button className="boton boton--peligro" disabled={m.isPending} onClick={() => m.mutate()}>Anular cita</button>
      </div>
    </Modal>
  );
}

const ESTADO_ENTREGA: Record<string, string> = {
  PROGRAMADO: 'Programado', ENVIADO: 'Enviado', ENTREGADO: 'Entregado', LEIDO: 'Leído', FALLIDO: 'No enviado',
};
const TIPO_MENSAJE: Record<string, string> = {
  CONFIRMACION: 'Confirmación', RECORDATORIO: 'Recordatorio', REFUERZO: 'Refuerzo automático', ANULACION: 'Aviso de anulación',
};

/** Modal «Gestionar confirmación» (222:4352): mensajes, reenvío (RF-38) y resultado de la llamada (RF-39). */
export function ModalConfirmacion({ cita, alCerrar }: { cita: Cita; alCerrar: () => void }) {
  const invalidar = useInvalidarAgenda();
  const detalle = useQuery({ queryKey: ['cita', cita.id], queryFn: () => api.get<DetalleCita>(`/api/citas/${cita.id}`) });
  const [resultado, setResultado] = useState<'CONFIRMO' | 'NO_ASISTIRA' | 'NO_CONTESTO' | ''>('');
  const [comentario, setComentario] = useState('');
  const [aviso, setAviso] = useState<string | null>(null);
  const avisar = useAviso();
  const reenviar = useMutation({
    mutationFn: () => api.post<{ mensaje: string }>(`/api/citas/${cita.id}/reenviar-recordatorio`),
    onSuccess: (r) => { setAviso(r.mensaje); detalle.refetch(); invalidar(); avisar('Recordatorio reenviado', { detalle: 'Sale por WhatsApp en menos de un minuto.' }); },
  });
  const guardar = useMutation({
    mutationFn: () => api.post(`/api/citas/${cita.id}/llamadas`, { resultado, comentario }),
    onSuccess: () => { invalidar(); alCerrar(); avisar('Llamada registrada'); },
  });
  const mensajes = (detalle.data?.mensajes ?? []).filter((m) => m.destinatario === 'PACIENTE' && m.estado !== 'PROGRAMADO');
  return (
    <Modal titulo="Confirmación de la cita" alCerrar={alCerrar}
      descripcion={`${cita.paciente} · ${cita.fecha === hoyIso() ? 'hoy' : diaCorto(cita.fecha)} ${hora(cita.horaInicio)} · ${cita.profesion}, ${cita.profesional} · ${cita.telefono}`}>
      <div className="tarjeta" style={{ background: 'var(--color-papel)', padding: '14px 16px', boxShadow: 'none' }}>
        <div className="etiqueta texto-secundario" style={{ marginBottom: 6 }}>Mensajes enviados</div>
        {detalle.isLoading && <Cargando />}
        {!detalle.isLoading && mensajes.length === 0 && <p className="texto-secundario texto-pequeno">Aún no se ha enviado ningún mensaje.</p>}
        <ul className="pila--chica" style={{ listStyle: 'none', margin: 0, padding: 0 }}>
          {mensajes.map((m) => (
            <li key={m.id} className="fila" style={{ alignItems: 'flex-start', flexWrap: 'nowrap', gap: 8 }}>
              <MessageCircle size={18} aria-hidden="true" style={{ color: 'var(--color-tinta)', marginTop: 2, flexShrink: 0 }} />
              <span><strong>{m.fechaEnvio ? fechaHoraCorta(m.fechaEnvio) : ''}</strong>{' '}
                <span className="texto-secundario">{m.enviadoPor === 'TENS' ? 'Reenviado por la TENS/TONS' : TIPO_MENSAJE[m.tipo]} por WhatsApp · {ESTADO_ENTREGA[m.estado]}
                  {cita.estadoConfirmacion === 'SIN_RESPUESTA' && m.tipo !== 'CONFIRMACION' && m.estado !== 'FALLIDO' ? ', sin respuesta' : ''}</span></span>
            </li>
          ))}
        </ul>
      </div>
      {aviso && <div className="aviso aviso--exito" role="status">{aviso}</div>}
      <MensajeError error={reenviar.error} />
      <div><button className="boton boton--secundario" disabled={reenviar.isPending} onClick={() => reenviar.mutate()}>Reenviar recordatorio por WhatsApp</button></div>
      <hr className="separador" />
      <fieldset className="pila--chica" style={{ border: 0, padding: 0, margin: 0 }}>
        <legend className="fila" style={{ gap: 8, fontFamily: 'var(--fuente-titulo)', fontSize: '1.25rem', marginBottom: 10 }}>
          <Phone size={20} aria-hidden="true" /> Registrar resultado de la llamada
        </legend>
        {([['CONFIRMO', 'Confirmó que asistirá'], ['NO_ASISTIRA', 'Avisó que no asistirá (se anula la cita y se libera el cupo)'], ['NO_CONTESTO', 'No contestó']] as const).map(([v, t]) => (
          <label key={v} className="opcion-radio">
            <input type="radio" name="resultado-llamada" value={v} checked={resultado === v} onChange={() => setResultado(v)} /> {t}
          </label>
        ))}
      </fieldset>
      <div className="campo">
        <label htmlFor="comentario-llamada">Comentario</label>
        <input id="comentario-llamada" className="control" value={comentario} onChange={(e) => setComentario(e.target.value)} />
      </div>
      <p className="texto-secundario texto-pequeno fila" style={{ gap: 6 }}><Sparkles size={16} aria-hidden="true" /> Refuerzos automáticos: los define la Administración.</p>
      <MensajeError error={guardar.error} />
      <div className="modal__acciones">
        <button className="boton boton--secundario" onClick={alCerrar}>Cancelar</button>
        <button className="boton" disabled={!resultado || guardar.isPending} onClick={() => guardar.mutate()}>Guardar resultado</button>
      </div>
    </Modal>
  );
}

function tamano(b: number) {
  return b > 1024 * 1024 ? `${(b / 1024 / 1024).toLocaleString('es-CL', { maximumFractionDigits: 1 })} MB` : `${Math.round(b / 1024)} KB`;
}

/** Modal «Verificar reserva en línea» (222:4038). La verificación queda ligada a los documentos (RF-41). */
export function ModalVerificarEnLinea({ cita, alCerrar }: { cita: Cita; alCerrar: () => void }) {
  const invalidar = useInvalidarAgenda();
  const docs = useQuery({ queryKey: ['documentos', cita.idPaciente], queryFn: () => api.get<Documento[]>(`/api/pacientes/${cita.idPaciente}/documentos`) });
  const [fonasa, setFonasa] = useState(false);
  const [cesfam, setCesfam] = useState(false);
  const [nombreCesfam, setNombreCesfam] = useState('');
  const [errorCampo, setErrorCampo] = useState<string | null>(null);
  const [resultadoNoCumple, setResultadoNoCumple] = useState(false);
  const registrar = useMutation({
    mutationFn: () => api.post(`/api/pacientes/${cita.idPaciente}/verificaciones`, {
      fechaVerificacion: hoyIso(), cesfamInscripcion: nombreCesfam, fonasaVigente: fonasa, inscritoCesfam: cesfam,
      idsDocumento: (docs.data ?? []).filter((d) => d.idVerificacion == null).map((d) => d.id),
    }),
    onSuccess: () => {
      invalidar();
      if (fonasa && cesfam) alCerrar(); else setResultadoNoCumple(true);
    },
  });
  const anular = useMutation({
    mutationFn: () => api.post(`/api/citas/${cita.id}/anular`, { origen: 'TENS', motivo: 'No presentó los certificados (D-03)' }),
    onSuccess: () => { invalidar(); alCerrar(); },
  });
  if (resultadoNoCumple) {
    return (
      <Modal titulo="No puede ser atendido" alCerrar={alCerrar}>
        <div className="aviso aviso--alerta">No puede ser atendido: oriente su inscripción en el CESFAM.</div>
        <p>¿Anular la reserva de {cita.paciente}? El horario volverá a quedar disponible.</p>
        <MensajeError error={anular.error} />
        <div className="modal__acciones">
          <button className="boton boton--secundario" onClick={alCerrar}>Mantener por ahora</button>
          <button className="boton boton--peligro" onClick={() => anular.mutate()} disabled={anular.isPending}>Anular reserva</button>
        </div>
      </Modal>
    );
  }
  return (
    <Modal titulo="Verificar reserva en línea" alCerrar={alCerrar}
      descripcion={`${cita.paciente} reservó en línea ${cita.profesion}, ${fechaLarga(cita.fecha).toLowerCase()} a las ${hora(cita.horaInicio)}${(docs.data?.length ?? 0) > 0 ? ', y adjuntó sus certificados. Revísalos y registra la verificación.' : '. No adjuntó certificados: revísalos cuando llegue.'}`}>
      {docs.isLoading && <Cargando />}
      <ul className="pila--chica" style={{ listStyle: 'none', padding: 0, margin: 0 }}>
        {(docs.data ?? []).map((d) => (
          <li key={d.id} className="tarjeta fila fila--entre" style={{ padding: '10px 14px', boxShadow: 'none' }}>
            <span className="fila" style={{ gap: 10, flexWrap: 'nowrap' }}>
              <FileText size={22} aria-hidden="true" style={{ color: 'var(--color-tinta)' }} />
              <span><span className="texto-fuerte">{d.nombreArchivo}</span><br />
                <span className="texto-secundario texto-pequeno">{d.tipo === 'CERTIFICADO_FONASA' ? 'FONASA' : 'CESFAM'} · {d.tipoMime === 'application/pdf' ? 'PDF' : 'foto'}, {tamano(d.tamanoBytes)}</span></span>
            </span>
            <a className="boton boton--secundario boton--compacto" href={`/api/documentos/${d.id}/archivo`} target="_blank" rel="noreferrer">Ver documento</a>
          </li>
        ))}
      </ul>
      <label className="casilla"><input type="checkbox" checked={fonasa} onChange={(e) => setFonasa(e.target.checked)} /> Certificado de afiliación a FONASA</label>
      <label className="casilla"><input type="checkbox" checked={cesfam} onChange={(e) => setCesfam(e.target.checked)} /> Certificado de inscripción en CESFAM</label>
      <div className="campo">
        <label htmlFor="cesfam">CESFAM de inscripción <Obligatorio /></label>
        <input id="cesfam" className="control" value={nombreCesfam} onChange={(e) => setNombreCesfam(e.target.value)} placeholder="Ej.: CESFAM Norte"
          aria-invalid={!!errorCampo} aria-describedby={errorCampo ? 'cesfam-error' : undefined} />
        {errorCampo && <span id="cesfam-error" className="campo-error">{errorCampo}</span>}
      </div>
      <MensajeError error={registrar.error} />
      <div className="modal__acciones">
        <button className="boton boton--secundario" onClick={alCerrar}>Cancelar</button>
        <button className="boton" disabled={registrar.isPending} onClick={() => {
          if (!nombreCesfam.trim()) { setErrorCampo('Escribe el CESFAM de inscripción.'); return; }
          setErrorCampo(null);
          registrar.mutate();
        }}>Registrar verificación</button>
      </div>
    </Modal>
  );
}

/** Detalle de la cita con su historial (222:3466). */
export function LateralDetalleCita({ idCita, alCerrar, alAnular, alGestionar }: { idCita: number; alCerrar: () => void; alAnular: (c: Cita) => void; alGestionar: (c: Cita) => void }) {
  const { data, isLoading, error } = useQuery({ queryKey: ['cita', idCita], queryFn: () => api.get<DetalleCita>(`/api/citas/${idCita}`) });
  const c = data?.fila.cita;
  return (
    <aside className="lateral" aria-label="Detalle de la cita">
      <div className="fila fila--entre"><h2>Detalle de la cita</h2>
        <button className="boton boton--texto" onClick={alCerrar} aria-label="Cerrar"><X size={22} /></button></div>
      {isLoading && <Cargando />}
      <MensajeError error={error} />
      {c && (
        <>
          <div>
            <div className="texto-fuerte" style={{ fontSize: '1.125rem' }}>{c.paciente}</div>
            <div className="texto-secundario">RUT {c.documento}, {c.telefono}</div>
          </div>
          <div>{capitalizar(c.fecha === hoyIso() ? 'hoy' : fechaLarga(c.fecha))} {hora(c.horaInicio)}, {c.profesion} con {c.profesional}, {c.box}</div>
          <div className="fila"><ChipCita estado={c.estado} /> <ChipConfirmacion estado={c.estadoConfirmacion} /></div>
          {c.estadoConfirmacion === 'NO_ENVIADA' && <div className="aviso aviso--alerta">La confirmación no se pudo entregar. Llama al paciente para confirmar.</div>}
          <div>
            <h3 style={{ marginBottom: 8 }}>Historial</h3>
            <ol className="pila--chica" style={{ paddingLeft: 18, margin: 0 }}>
              {data!.historial.map((e, i) => (
                <li key={i}><span className="texto-secundario texto-pequeno">{fechaHoraCorta(e.fechaHora)}</span><br />{e.texto}</li>
              ))}
            </ol>
          </div>
          {(c.estado === 'RESERVADA' || c.estado === 'PENDIENTE_VERIFICACION') && (
            <div className="fila">
              <button className="boton boton--secundario" onClick={() => alGestionar(c)}>Gestionar confirmación</button>
              <button className="boton boton--gris-borde" onClick={() => alAnular(c)}>Anular</button>
            </div>
          )}
        </>
      )}
    </aside>
  );
}

export function textoError(e: unknown) {
  return e instanceof ErrorDeApi ? e.mensaje : 'Ocurrió un problema.';
}
