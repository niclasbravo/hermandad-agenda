import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { BellRing, Send } from 'lucide-react';
import { api, consulta, ErrorDeApi } from '../compartido/api';
import { Cargando, MensajeError } from '../compartido/componentes';

interface Telefono { telefono: string; formateado: string }
interface Burbuja { idMensaje: number; idCita: number; tipo: string; texto: string; conBotones: boolean; estado: string; fechaEnvio: string | null; citaVigente: boolean }
interface Respondida { idMensaje: number; texto: string; hora: string }

/** Simulador de WhatsApp (sección 11.6), basado en M-05 (222:6955). Solo existe en los perfiles demo y dev. */
export function SimuladorWhatsApp() {
  const qc = useQueryClient();
  const [telefono, setTelefono] = useState('');
  const [respondidas, setRespondidas] = useState<Respondida[]>([]);
  const chat = useRef<HTMLDivElement>(null);
  const telefonos = useQuery({ queryKey: ['sim', 'telefonos'], queryFn: () => api.get<Telefono[]>('/api/dev/simulador/telefonos'), refetchInterval: 5000, retry: false });
  useEffect(() => { if (!telefono && telefonos.data?.length) setTelefono(telefonos.data[0].telefono); }, [telefonos.data, telefono]);
  const mensajes = useQuery({
    queryKey: ['sim', 'mensajes', telefono],
    queryFn: () => api.get<Burbuja[]>('/api/dev/simulador/mensajes' + consulta({ telefono })),
    enabled: !!telefono, refetchInterval: 4000,
  });
  useEffect(() => { chat.current?.scrollTo({ top: chat.current.scrollHeight }); }, [mensajes.data, respondidas]);
  const responder = useMutation({
    mutationFn: (v: { idMensaje: number; respuesta: 'ASISTIRA' | 'NO_ASISTIRA' }) => api.post('/api/dev/simulador/respuesta', v),
    onSuccess: (_, v) => {
      const ahora = new Date();
      setRespondidas((r) => [...r, { idMensaje: v.idMensaje, texto: v.respuesta === 'ASISTIRA' ? 'Asistiré' : 'No podré asistir', hora: `${ahora.getHours()}:${String(ahora.getMinutes()).padStart(2, '0')}` }]);
      qc.invalidateQueries({ queryKey: ['sim'] });
    },
  });
  const enviar = useMutation({ mutationFn: () => api.post('/api/dev/simulador/enviar-pendientes'), onSuccess: () => qc.invalidateQueries({ queryKey: ['sim'] }) });
  const forzar = useMutation({ mutationFn: (idCita: number) => api.post(`/api/dev/simulador/recordatorio/${idCita}`), onSuccess: () => qc.invalidateQueries({ queryKey: ['sim'] }) });

  if (telefonos.error instanceof ErrorDeApi && (telefonos.error.estado === 404 || telefonos.error.estado === 401)) {
    return <main className="seccion"><h1>Simulador de WhatsApp</h1><p>El simulador solo existe en los perfiles de demostración y desarrollo.</p><Link to="/">Volver</Link></main>;
  }
  const lista = mensajes.data ?? [];
  const ultimaCita = [...lista].reverse().find((m) => m.citaVigente)?.idCita;
  return (
    <main style={{ minHeight: '100vh', background: 'var(--color-fondo)', padding: '24px 16px' }}>
      <div style={{ maxWidth: 980, margin: '0 auto', display: 'grid', gridTemplateColumns: 'minmax(0,1fr) 390px', gap: 32, alignItems: 'start' }} className="reserva-grilla">
        <section className="pila">
          <h1>Simulador de WhatsApp</h1>
          <p className="texto-secundario">Muestra los mensajes que el sistema envía a cada número. Los botones «Asistiré» y «No podré asistir» usan el mismo procesamiento que el webhook de WhatsApp.</p>
          <div className="campo"><label htmlFor="sim-tel">Número</label>
            <select id="sim-tel" className="control" value={telefono} onChange={(e) => { setTelefono(e.target.value); setRespondidas([]); }}>
              {telefonos.data?.map((t) => <option key={t.telefono} value={t.telefono}>{t.formateado}</option>)}
            </select></div>
          <div className="fila">
            <button className="boton boton--secundario" disabled={enviar.isPending} onClick={() => enviar.mutate()}><Send size={18} aria-hidden="true" /> Enviar mensajes pendientes ahora</button>
            <button className="boton boton--secundario" disabled={!ultimaCita || forzar.isPending} onClick={() => ultimaCita && forzar.mutate(ultimaCita)}><BellRing size={18} aria-hidden="true" /> Forzar envío del recordatorio ahora</button>
          </div>
          <MensajeError error={responder.error || forzar.error || enviar.error} />
          <p className="texto-secundario texto-pequeno">Los números que terminan en 0000 simulan un envío fallido: la cita queda «No enviada: llamar».</p>
          <Link to="/agenda-dia">Ir a la agenda del día</Link>
        </section>
        <div className="telefono" aria-label="Teléfono simulado">
          <div className="telefono__cabecera"><div className="texto-fuerte">Hermandad de Dolores</div><div className="texto-pequeno" style={{ opacity: .9 }}>Mensajes automáticos de citas</div></div>
          <div className="telefono__chat" ref={chat} aria-live="polite">
            {mensajes.isLoading && <Cargando />}
            {lista.length === 0 && !mensajes.isLoading && <p className="texto-pequeno texto-secundario centrado">Sin mensajes para este número.</p>}
            {lista.map((m) => {
              const respuesta = respondidas.find((r) => r.idMensaje === m.idMensaje);
              return (
                <div key={m.idMensaje} className="pila--chica">
                  <div className="burbuja burbuja--entrante">
                    {m.texto}
                    {m.estado === 'FALLIDO' && <div className="texto-pequeno" style={{ color: 'var(--color-error)' }}>No entregado</div>}
                    <div className="burbuja__hora">{m.fechaEnvio ? `Enviado ${m.fechaEnvio.slice(11, 16)}` : ''} {m.estado === 'ENTREGADO' || m.estado === 'LEIDO' ? '✓✓' : m.estado === 'ENVIADO' ? '✓' : ''}</div>
                    {m.conBotones && (
                      <div className="burbuja__botones">
                        <button disabled={!m.citaVigente || !!respuesta || responder.isPending} onClick={() => responder.mutate({ idMensaje: m.idMensaje, respuesta: 'ASISTIRA' })}>Asistiré</button>
                        <button disabled={!m.citaVigente || !!respuesta || responder.isPending} onClick={() => responder.mutate({ idMensaje: m.idMensaje, respuesta: 'NO_ASISTIRA' })}>No podré asistir</button>
                      </div>
                    )}
                  </div>
                  {respuesta && <div className="burbuja burbuja--saliente">{respuesta.texto}<div className="burbuja__hora">{respuesta.hora}</div></div>}
                </div>
              );
            })}
          </div>
        </div>
      </div>
    </main>
  );
}
