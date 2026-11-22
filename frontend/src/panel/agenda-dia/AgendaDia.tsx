import { Fragment, useEffect, useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useMutation, useQuery } from '@tanstack/react-query';
import { CalendarX, Check, ChevronLeft, ChevronRight, Columns3, List, Lock, Plus, X } from 'lucide-react';
import { api, consulta } from '../../compartido/api';
import { Cargando, ChipConfirmacion, Iniciales, MensajeError, Vacio } from '../../compartido/componentes';
import { fechaLarga, fechaLargaConAno, hora, hoyIso, sumarDias } from '../../compartido/formato';
import { useSesion } from '../../compartido/sesion';
import type { AgendaDia as Agenda, AvisoCupo, CeldaCupo, Cita, Columna, FilaCita, Paciente, VistaAgenda } from '../../compartido/tipos';
import { Cabecera } from '../DisenoPanel';
import { useInvalidarAgenda, usePoliclinicos } from '../consultas';
import { LateralDetalleCita, ModalAnular, ModalConfirmacion, ModalVerificarEnLinea } from './Modales';
import { useAviso } from '../../compartido/avisos';

function ahoraHHMM() {
  const d = new Date();
  return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`;
}

/** M-06 Agenda del día (222:1814): lista, por profesional o por box (D-16). */
export function AgendaDia() {
  const { usuario } = useSesion();
  const esTens = usuario?.perfil === 'TENS';
  const [fecha, setFecha] = useState(hoyIso());
  const [vista, setVista] = useState<VistaAgenda>('LISTA');
  const [idPoliclinico, setIdPoliclinico] = useState<number | ''>('');
  const [anular, setAnular] = useState<Cita | null>(null);
  const [gestionar, setGestionar] = useState<Cita | null>(null);
  const [verificar, setVerificar] = useState<Cita | null>(null);
  const [detalle, setDetalle] = useState<number | null>(null);
  const [reservaRapida, setReservaRapida] = useState<{ celda: CeldaCupo; columna: Columna } | null>(null);
  const [ahora, setAhora] = useState(ahoraHHMM());
  const policlinicos = usePoliclinicos();
  const invalidar = useInvalidarAgenda();

  useEffect(() => {
    const t = setInterval(() => setAhora(ahoraHHMM()), 30_000);
    return () => clearInterval(t);
  }, []);
  useEffect(() => {
    if (!esTens && idPoliclinico === '' && policlinicos.data?.length) setIdPoliclinico(policlinicos.data[0].id);
  }, [esTens, idPoliclinico, policlinicos.data]);

  const pol = esTens ? usuario?.idPoliclinicoBase : idPoliclinico || undefined;
  const nombrePol = esTens ? usuario?.policlinicoBase : policlinicos.data?.find((p) => p.id === idPoliclinico)?.nombre;
  const agenda = useQuery({
    queryKey: ['agenda-dia', pol, fecha, vista],
    queryFn: () => api.get<Agenda>('/api/agenda-dia' + consulta({ policlinico: pol, fecha, vista })),
    enabled: esTens || idPoliclinico !== '',
    refetchInterval: 60_000,
  });
  const avisos = useQuery({
    queryKey: ['avisos', pol],
    queryFn: () => api.get<AvisoCupo[]>('/api/avisos-cupo' + consulta({ policlinico: pol })),
    enabled: esTens,
    refetchInterval: 60_000,
  });
  const avisar = useAviso();
  const asistencia = useMutation({
    mutationFn: ({ id, asistio }: { id: number; asistio: boolean }) => api.post(`/api/citas/${id}/asistencia`, { asistio }),
    onSuccess: (_r, v) => { invalidar(); avisar(v.asistio ? 'Asistencia registrada' : 'Inasistencia registrada', { tipo: v.asistio ? 'exito' : 'info' }); },
  });

  const r = agenda.data?.resumen;
  const esHoy = fecha === hoyIso();
  const avisosDelDia = (avisos.data ?? []).filter((a) => a.fecha === fecha);

  return (
    <>
      <Cabecera titulo="Agenda del día" subtitulo={`${fechaLargaConAno(fecha)}, Policlínico ${nombrePol ?? ''}`}>
        <div className="segmentado" role="group" aria-label="Vista de la agenda">
          <button aria-pressed={vista === 'LISTA'} onClick={() => setVista('LISTA')}><List size={18} aria-hidden="true" /> Lista</button>
          <button aria-pressed={vista === 'POR_PROFESIONAL'} onClick={() => setVista('POR_PROFESIONAL')}><Columns3 size={18} aria-hidden="true" /> Por profesional</button>
          <button aria-pressed={vista === 'POR_BOX'} onClick={() => setVista('POR_BOX')}><Columns3 size={18} aria-hidden="true" /> Por box</button>
        </div>
      </Cabecera>
      <div className="cuerpo">
        <div className="fila fila--entre">
          <div className="fila" style={{ gap: 6 }}>
            <button className="boton boton--gris-borde boton--compacto" aria-label="Día anterior" onClick={() => setFecha(sumarDias(fecha, -1))}><ChevronLeft size={20} /></button>
            <button className="boton boton--gris-borde boton--compacto" onClick={() => setFecha(hoyIso())} disabled={esHoy}>Hoy</button>
            <button className="boton boton--gris-borde boton--compacto" aria-label="Día siguiente" onClick={() => setFecha(sumarDias(fecha, 1))}><ChevronRight size={20} /></button>
            <span className="texto-fuerte" style={{ marginLeft: 8 }}>{fechaLarga(fecha)}</span>
          </div>
          {!esTens && (
            <div className="campo" style={{ minWidth: 240 }}>
              <label htmlFor="pol-agenda" className="sr-only">Policlínico</label>
              <select id="pol-agenda" className="control" value={idPoliclinico} onChange={(e) => setIdPoliclinico(Number(e.target.value))}>
                {policlinicos.data?.map((p) => <option key={p.id} value={p.id}>{p.nombre}</option>)}
              </select>
            </div>
          )}
        </div>

        {r && (
          <section className="resumen" aria-label="Resumen del día">
            <div className="indicador"><div className="indicador__valor">{r.citasHoy}</div><div className="indicador__texto">Citas {esHoy ? 'hoy' : 'del día'}</div></div>
            <div className="indicador"><div className="indicador__valor" style={{ color: 'var(--color-exito)' }}>{r.confirmadas}</div><div className="indicador__texto">Confirmadas</div></div>
            <div className="indicador indicador--alerta"><div className="indicador__valor">{r.porLlamar}</div><div className="indicador__texto">Por llamar (no enviadas)</div></div>
            <div className="indicador indicador--gris"><div className="indicador__valor">{r.sinRespuesta}</div><div className="indicador__texto">Sin respuesta</div></div>
            <div className="indicador indicador--info"><div className="indicador__valor">{r.enListaEspera}</div><div className="indicador__texto">En lista de espera</div>
              {esTens && <Link to="/lista-espera" className="texto-fuerte texto-pequeno">Ver lista →</Link>}</div>
          </section>
        )}
        {r && r.pendientesVerificacion > 0 && (
          <div><span className="chip chip--alerta">{r.pendientesVerificacion} {r.pendientesVerificacion === 1 ? 'reserva en línea pendiente' : 'reservas en línea pendientes'} de verificación</span></div>
        )}
        <MensajeError error={asistencia.error} />
        {agenda.isLoading && <Cargando forma="tarjetas" texto="Cargando la agenda del día…" />}
        <MensajeError error={agenda.error} />

        {agenda.data && vista === 'LISTA' && (
          <TablaDelDia filas={agenda.data.citas} avisos={avisosDelDia} esHoy={esHoy} ahora={ahora} esTens={esTens}
            alAsistencia={(id, asistio) => asistencia.mutate({ id, asistio })} ocupado={asistencia.isPending}
            alAnular={setAnular} alGestionar={setGestionar} alVerificar={setVerificar} alDetalle={setDetalle} />
        )}
        {agenda.data && vista !== 'LISTA' && (
          <VistaColumnas columnas={agenda.data.columnas} esTens={esTens} esHoy={esHoy} ahora={ahora}
            alDetalle={setDetalle} alReservar={(celda, columna) => setReservaRapida({ celda, columna })} />
        )}
      </div>

      {anular && <ModalAnular cita={anular} alCerrar={() => setAnular(null)} />}
      {gestionar && <ModalConfirmacion cita={gestionar} alCerrar={() => setGestionar(null)} />}
      {verificar && <ModalVerificarEnLinea cita={verificar} alCerrar={() => setVerificar(null)} />}
      {detalle && <LateralDetalleCita idCita={detalle} alCerrar={() => setDetalle(null)}
        alAnular={(c) => { setDetalle(null); setAnular(c); }} alGestionar={(c) => { setDetalle(null); setGestionar(c); }} />}
      {reservaRapida && <LateralReservaRapida celda={reservaRapida.celda} columna={reservaRapida.columna} fecha={fecha}
        alCerrar={() => setReservaRapida(null)} />}
    </>
  );
}

interface TablaProps {
  filas: FilaCita[];
  avisos: AvisoCupo[];
  esHoy: boolean;
  ahora: string;
  esTens: boolean;
  ocupado: boolean;
  alAsistencia: (id: number, asistio: boolean) => void;
  alAnular: (c: Cita) => void;
  alGestionar: (c: Cita) => void;
  alVerificar: (c: Cita) => void;
  alDetalle: (id: number) => void;
}

type Item = { tipo: 'cita'; hora: string; fila: FilaCita } | { tipo: 'aviso'; hora: string; aviso: AvisoCupo } | { tipo: 'ahora'; hora: string };

function TablaDelDia({ filas, avisos, esHoy, ahora, esTens, ocupado, alAsistencia, alAnular, alGestionar, alVerificar, alDetalle }: TablaProps) {
  const navegar = useNavigate();
  const invalidar = useInvalidarAgenda();
  const atender = useMutation({ mutationFn: (id: number) => api.post(`/api/avisos-cupo/${id}/atender`), onSuccess: invalidar });
  const items: Item[] = [
    ...filas.map((f) => ({ tipo: 'cita' as const, hora: hora(f.cita.horaInicio), fila: f })),
    ...avisos.map((a) => ({ tipo: 'aviso' as const, hora: hora(a.hora), aviso: a })),
  ].sort((a, b) => a.hora.localeCompare(b.hora));
  if (esHoy) {
    const i = items.findIndex((x) => x.hora > ahora);
    items.splice(i < 0 ? items.length : i, 0, { tipo: 'ahora', hora: ahora });
  }
  if (filas.length === 0 && avisos.length === 0) {
    return <div className="tarjeta"><Vacio><CalendarX size={28} aria-hidden="true" /><br />No hay citas para este día.</Vacio></div>;
  }
  return (
    <div className="tarjeta tabla-contenedor">
      <table className="tabla">
        <thead>
          <tr><th className="col-hora">Hora</th><th>Paciente</th><th>Profesional</th><th>Confirmación</th><th>Asistencia</th></tr>
        </thead>
        <tbody>
          {items.map((it, idx) => {
            if (it.tipo === 'ahora') {
              return <tr key="ahora" className="fila-ahora"><td colSpan={5}><div className="linea-ahora"><span>Ahora {ahora}</span></div></td></tr>;
            }
            if (it.tipo === 'aviso') {
              const a = it.aviso;
              return (
                <tr key={'aviso' + a.id} className="fila--resaltada">
                  <td className="col-hora"><span className="hora">{hora(a.hora)}</span></td>
                  <td colSpan={3}><strong>{a.mensaje}</strong><div className="texto-secundario texto-pequeno">{a.profesional}, {a.box}</div></td>
                  <td>
                    {esTens && (
                      <button className="boton boton--compacto" disabled={atender.isPending} onClick={async () => {
                        // RF-26: el clic marca el aviso como atendido. Si eso falla, igual se abre la reserva:
                        // el aviso sigue visible mientras el cupo esté libre.
                        try { await atender.mutateAsync(a.id); } catch { /* sin efecto en la reserva */ }
                        navegar(`/reservar-cita?paciente=${a.idPaciente}&cupo=${a.idCupo}&inscripcion=${a.idInscripcion}`);
                      }}>Reservar para {a.primera.split(' ')[0]}</button>
                    )}
                  </td>
                </tr>
              );
            }
            const { cita: c, requiereLlamada, avisadoA, tieneDocumentos } = it.fila;
            const pendiente = c.estado === 'PENDIENTE_VERIFICACION';
            const registrada = c.estado === 'ASISTIDA' || c.estado === 'INASISTIDA';
            return (
              <Fragment key={c.id + '-' + idx}>
                <tr className={pendiente || c.estadoConfirmacion === 'NO_ENVIADA' ? '' : registrada && c.estado === 'ASISTIDA' ? 'fila--atendida' : ''}>
                  <td className="col-hora"><span className="hora">{hora(c.horaInicio)}</span></td>
                  <td>
                    <button className="fila" style={{ gap: 10, flexWrap: 'nowrap', background: 'none', border: 0, padding: 0, cursor: 'pointer', textAlign: 'left', font: 'inherit', color: 'inherit' }}
                      onClick={() => alDetalle(c.id)} aria-label={`Ver detalle de la cita de ${c.paciente}`}>
                      <Iniciales texto={c.iniciales} alerta={c.estadoConfirmacion === 'NO_ENVIADA'} />
                      <span><span className="texto-fuerte">{c.paciente}</span><br /><span className="texto-secundario texto-pequeno">{c.documento}</span></span>
                    </button>
                  </td>
                  <td>{c.profesional}<br /><span className="texto-secundario texto-pequeno">{c.profesion}, {c.box}</span></td>
                  <td>
                    {pendiente ? (
                      <div className="pila--chica" style={{ gap: 4 }}>
                        <span className="chip chip--alerta">Pendiente de verificación</span>
                        <span className="texto-pequeno texto-secundario">Reserva en línea{tieneDocumentos ? ' con certificados' : ''}</span>
                        {esTens && <button className="enlace texto-pequeno" style={{ alignSelf: 'flex-start' }} onClick={() => alVerificar(c)}>Verificar reserva en línea</button>}
                      </div>
                    ) : (
                      <div className="pila--chica" style={{ gap: 4 }}>
                        <span><ChipConfirmacion estado={c.estadoConfirmacion} /></span>
                        {c.estadoConfirmacion === 'NO_ENVIADA' && <span className="texto-pequeno">Llamar al {c.telefono}</span>}
                        {avisadoA && <span className="texto-pequeno texto-secundario">Avisado a {avisadoA}</span>}
                        {esTens && requiereLlamada && c.estado === 'RESERVADA' && (
                          <button className="enlace texto-pequeno" style={{ alignSelf: 'flex-start' }} onClick={() => alGestionar(c)}>Gestionar confirmación</button>
                        )}
                      </div>
                    )}
                  </td>
                  <td>
                    {c.estado === 'ASISTIDA' && <span className="chip chip--exito"><Check size={16} aria-hidden="true" /> Asistió</span>}
                    {c.estado === 'INASISTIDA' && <span className="chip chip--error"><X size={16} aria-hidden="true" /> No asistió</span>}
                    {esTens && c.estado === 'RESERVADA' && (
                      <div className="fila" style={{ gap: 8, flexWrap: 'nowrap' }}>
                        <button className="boton boton--exito-borde boton--compacto" disabled={ocupado || c.fecha > hoyIso()}
                          onClick={() => alAsistencia(c.id, true)} aria-label={`Registrar que ${c.paciente} asistió`}><Check size={18} aria-hidden="true" /> Asistió</button>
                        <button className="boton boton--gris-borde boton--compacto" disabled={ocupado || c.fecha > hoyIso()}
                          onClick={() => alAsistencia(c.id, false)} aria-label={`Registrar que ${c.paciente} no asistió`}><X size={18} aria-hidden="true" /> No asistió</button>
                        <button className="boton boton--texto boton--compacto" onClick={() => alAnular(c)} aria-label={`Anular la cita de ${c.paciente}`}>
                          <CalendarX size={18} aria-hidden="true" /> Anular</button>
                      </div>
                    )}
                    {esTens && pendiente && (
                      <button className="boton boton--texto boton--compacto" onClick={() => alAnular(c)}><CalendarX size={18} aria-hidden="true" /> Anular</button>
                    )}
                  </td>
                </tr>
              </Fragment>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}

function VistaColumnas({ columnas, esTens, esHoy, ahora, alDetalle, alReservar }: {
  columnas: Columna[]; esTens: boolean; esHoy: boolean; ahora: string; alDetalle: (id: number) => void;
  alReservar: (celda: CeldaCupo, columna: Columna) => void;
}) {
  if (columnas.length === 0) return <div className="tarjeta"><Vacio>No hay bloques horarios este día.</Vacio></div>;
  return (
    <div className="tarjeta tarjeta--padding">
      <div className="columnas">
        {columnas.map((col) => (
          <section key={col.id} aria-label={col.titulo}>
            <div className="columna__cabecera"><div className="texto-fuerte">{col.titulo}</div><div className="texto-secundario texto-pequeno">{col.subtitulo}</div></div>
            {col.cupos.map((celda) => {
              const f = celda.cita;
              if (f) {
                const c = f.cita;
                const clase = c.estado === 'ASISTIDA' ? 'cupo--asistio' : c.estadoConfirmacion === 'NO_ENVIADA' || c.estado === 'PENDIENTE_VERIFICACION' ? 'cupo--alerta' : 'cupo--reservado';
                return (
                  <button key={celda.idCupo} className={'cupo ' + clase} onClick={() => alDetalle(c.id)}>
                    <span className="hora">{hora(celda.horaInicio)}</span> <span className="texto-fuerte">{c.paciente}</span><br />
                    <span className="texto-pequeno">
                      {c.estado === 'ASISTIDA' ? 'Asistió' : c.estado === 'INASISTIDA' ? 'No asistió' : c.estado === 'PENDIENTE_VERIFICACION' ? 'Pendiente de verificación'
                        : c.estadoConfirmacion === 'NO_ENVIADA' ? 'No enviada: llamar' : c.estadoConfirmacion === 'CONFIRMADA' || c.estadoConfirmacion === 'CONFIRMADA_TELEFONO'
                          ? 'Confirmada' + (c.relacionAdulto && f.avisadoA ? ` (${c.relacionAdulto})` : '') : c.estadoConfirmacion === 'SIN_RESPUESTA' ? 'Sin respuesta' : 'Enviada'}
                    </span>
                  </button>
                );
              }
              if (celda.estado === 'BLOQUEADO') {
                return <div key={celda.idCupo} className="cupo cupo--bloqueado"><span className="hora" style={{ color: 'inherit' }}>{hora(celda.horaInicio)}</span> <Lock size={14} aria-label="Bloqueado" /> {celda.motivoBloqueo}</div>;
              }
              const pasado = celda.estado === 'PASADO' || (esHoy && hora(celda.horaInicio) <= ahora);
              if (celda.estado === 'DISPONIBLE' && esTens && !pasado) {
                return (
                  <button key={celda.idCupo} className="cupo" onClick={() => alReservar(celda, col)} aria-label={`Reservar ${hora(celda.horaInicio)} con ${col.titulo}`}>
                    <Plus size={14} aria-hidden="true" /> <span className="hora">{hora(celda.horaInicio)}</span> <span className="texto-secundario">Disponible</span>
                  </button>
                );
              }
              return (
                <div key={celda.idCupo} className={'cupo ' + (pasado ? 'cupo--pasado' : celda.estado === 'NO_PUBLICADO' ? 'cupo--no-publicado' : '')}>
                  <span className="hora" style={pasado ? { color: 'inherit' } : undefined}>{hora(celda.horaInicio)}</span> <span className="texto-secundario">{pasado ? 'Sin reserva' : celda.estado === 'NO_PUBLICADO' ? 'No publicado' : 'Disponible'}</span>
                </div>
              );
            })}
          </section>
        ))}
      </div>
    </div>
  );
}

/** Panel lateral de reserva (222:3173): se busca al paciente por RUT y se reserva el cupo elegido. */
function LateralReservaRapida({ celda, columna, fecha, alCerrar }: { celda: CeldaCupo; columna: Columna; fecha: string; alCerrar: () => void }) {
  const [rut, setRut] = useState('');
  const [paciente, setPaciente] = useState<Paciente | null>(null);
  const [errorBusqueda, setErrorBusqueda] = useState<unknown>(null);
  const [hecho, setHecho] = useState(false);
  const invalidar = useInvalidarAgenda();
  const reservar = useMutation({
    mutationFn: () => api.post('/api/citas', { idPaciente: paciente!.id, idCupo: celda.idCupo }),
    onSuccess: () => { invalidar(); setHecho(true); },
  });
  const buscar = async (e: FormEvent) => {
    e.preventDefault();
    setErrorBusqueda(null);
    setPaciente(null);
    try {
      setPaciente(await api.get<Paciente>('/api/pacientes' + consulta({ documento: rut })));
    } catch (err) {
      setErrorBusqueda(err);
    }
  };
  return (
    <aside className="lateral" aria-label="Reservar hora">
      <div className="fila fila--entre"><h2>Reservar hora</h2><button className="boton boton--texto" onClick={alCerrar} aria-label="Cerrar"><X size={22} /></button></div>
      <div className="tarjeta" style={{ padding: 14, boxShadow: 'none', background: 'var(--color-papel)' }}>
        <div className="texto-fuerte">{fechaLarga(fecha)}, {hora(celda.horaInicio)}</div>
        <div>{columna.subtitulo.split(',')[0]} con {columna.titulo}</div>
        <div className="texto-secundario texto-pequeno">{columna.subtitulo}</div>
      </div>
      {hecho ? (
        <div className="aviso aviso--exito" role="status">Cita reservada. La confirmación se enviará por WhatsApp.</div>
      ) : (
        <>
          <form className="pila--chica" onSubmit={buscar}>
            <label className="etiqueta" htmlFor="rut-rapido">Paciente</label>
            <div className="fila" style={{ flexWrap: 'nowrap' }}>
              <input id="rut-rapido" className="control" placeholder="RUT del paciente" value={rut} onChange={(e) => setRut(e.target.value)} />
              <button className="boton" type="submit">Buscar</button>
            </div>
          </form>
          <MensajeError error={errorBusqueda} />
          {paciente && (
            <div className="tarjeta" style={{ padding: 14, boxShadow: 'none' }}>
              <div className="fila fila--entre"><span className="texto-fuerte">{paciente.nombreCompleto}</span>
                {paciente.habilitado ? <span className="chip chip--exito">Habilitada</span> : <span className="chip chip--alerta">Sin verificación</span>}</div>
              <div className="texto-secundario texto-pequeno">RUT {paciente.documentoFormateado}, {paciente.edad} años</div>
            </div>
          )}
          {paciente && !paciente.habilitado && (
            <div className="aviso aviso--alerta">Este paciente aún no tiene registrada la verificación de FONASA y CESFAM. <Link to={`/pacientes/${paciente.id}`}>Ir a su ficha</Link></div>
          )}
          <MensajeError error={reservar.error} />
          <button className="boton" disabled={!paciente?.habilitado || reservar.isPending} onClick={() => reservar.mutate()}>Confirmar reserva</button>
        </>
      )}
    </aside>
  );
}
