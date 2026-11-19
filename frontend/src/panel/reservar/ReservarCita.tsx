import { useEffect, useMemo, useState, type FormEvent } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { useMutation, useQuery } from '@tanstack/react-query';
import { CalendarCheck, Check, Clock, MessageCircle, Search } from 'lucide-react';
import { api, consulta, ErrorDeApi } from '../../compartido/api';
import { Cargando, Iniciales, MensajeError } from '../../compartido/componentes';
import { diaCorto, fechaCorta, fechaLarga, hora, hoyIso, lunesDe, sumarDias } from '../../compartido/formato';
import { useSesion } from '../../compartido/sesion';
import type { Cita, CupoVista, Paciente } from '../../compartido/tipos';
import { Cabecera } from '../DisenoPanel';
import { useInvalidarAgenda, usePoliclinicos, useProfesionales, useProfesiones } from '../consultas';

interface Previa { conCupo: { fecha: string; hora: string; idCupo: number }[]; sinCupo: { fecha: string; hora: string; motivo: string }[] }
interface ResultadoReserva { cita: Cita; inscripcionesEnEspera: number[] }

/** M-04 Reservar cita (222:5318 y estados). RF-14 a RF-16, RF-25. RNF-01: reservar en 2 minutos o menos. */
export function ReservarCita() {
  const { usuario } = useSesion();
  const [params] = useSearchParams();
  const invalidar = useInvalidarAgenda();
  const [idPaciente, setIdPaciente] = useState<number | null>(params.get('paciente') ? Number(params.get('paciente')) : null);
  const [rut, setRut] = useState('');
  const [errorPaciente, setErrorPaciente] = useState<unknown>(null);
  const [policlinico, setPoliclinico] = useState<number | ''>(usuario?.idPoliclinicoBase ?? '');
  const [profesion, setProfesion] = useState<number | ''>('');
  const [profesional, setProfesional] = useState<number | ''>('');
  const [desde, setDesde] = useState(hoyIso());
  const [hasta, setHasta] = useState(sumarDias(lunesDe(hoyIso()), 4) < hoyIso() ? sumarDias(hoyIso(), 6) : sumarDias(lunesDe(hoyIso()), 4));
  const [filtro, setFiltro] = useState<Record<string, string | number> | null>(null);
  const [elegido, setElegido] = useState<CupoVista | null>(null);
  const [serie, setSerie] = useState(false);
  const [cantidad, setCantidad] = useState(10);
  const [previa, setPrevia] = useState<Previa | null>(null);
  const [aceptadas, setAceptadas] = useState<Set<string>>(new Set());
  const [hecho, setHecho] = useState<{ citas: Cita[]; inscripciones: number[] } | null>(null);
  const [inscrita, setInscrita] = useState(false);
  const [retirada, setRetirada] = useState(false);

  const policlinicos = usePoliclinicos();
  const profesiones = useProfesiones();
  const profesionales = useProfesionales();
  const paciente = useQuery({ queryKey: ['paciente', idPaciente], queryFn: () => api.get<Paciente>(`/api/pacientes/${idPaciente}`), enabled: !!idPaciente });
  const cupos = useQuery({
    queryKey: ['cupos', filtro],
    queryFn: () => api.get<CupoVista[]>('/api/cupos/disponibles' + consulta(filtro!)),
    enabled: !!filtro,
  });

  // Viene desde el aviso de cupo liberado: el cupo queda preseleccionado.
  const idCupoAviso = params.get('cupo');
  const inscripcionAviso = params.get('inscripcion');
  useEffect(() => {
    if (!idCupoAviso || !policlinico) return;
    const f = { policlinico, desde: hoyIso(), hasta: sumarDias(hoyIso(), 60) };
    api.get<CupoVista[]>('/api/cupos/disponibles' + consulta(f)).then((lista) => {
      const c = lista.find((x) => String(x.id) === idCupoAviso);
      if (c) {
        setProfesion(c.idProfesion);
        setDesde(c.fecha);
        setHasta(c.fecha);
        setFiltro({ policlinico, profesion: c.idProfesion, desde: c.fecha, hasta: c.fecha });
        setElegido(c);
      }
    });
  }, [idCupoAviso, policlinico]);

  const profesionalesFiltrados = (profesionales.data ?? []).filter((p) => p.activo
    && (!profesion || p.profesion.id === profesion) && (!policlinico || p.policlinicos.some((x) => x.id === policlinico)));
  const nombreProfesion = profesiones.data?.find((p) => p.id === (filtro?.profesion ?? profesion))?.nombre ?? 'esta atención';

  const porDia = useMemo(() => {
    const m = new Map<string, CupoVista[]>();
    (cupos.data ?? []).forEach((c) => m.set(c.fecha, [...(m.get(c.fecha) ?? []), c]));
    return [...m.entries()];
  }, [cupos.data]);

  const reservar = useMutation({
    mutationFn: async () => {
      if (serie) {
        const r = await api.post<Cita[]>('/api/citas/serie', { idPaciente, idProfesional: elegido!.idProfesional, idCupoPrimero: elegido!.id, cantidad, fechasAceptadas: [...aceptadas] });
        return { citas: r, inscripciones: [] as number[] };
      }
      const r = await api.post<ResultadoReserva>('/api/citas', { idPaciente, idCupo: elegido!.id });
      return { citas: [r.cita], inscripciones: r.inscripcionesEnEspera };
    },
    onSuccess: (r) => { invalidar(); setHecho(r); },
    onError: (e) => { if (e instanceof ErrorDeApi && e.estado === 409) { cupos.refetch(); setElegido(null); } },
  });
  const previsualizar = useMutation({
    mutationFn: () => api.post<Previa>('/api/citas/serie/previsualizar', { idPaciente, idProfesional: elegido!.idProfesional, idCupoPrimero: elegido!.id, cantidad }),
    onSuccess: (p) => { setPrevia(p); setAceptadas(new Set(p.conCupo.map((x) => x.fecha))); },
  });
  const inscribir = useMutation({
    mutationFn: () => api.post('/api/lista-espera', { idPaciente, idPoliclinico: filtro?.policlinico ?? policlinico, idProfesion: filtro?.profesion ?? profesion }),
    onSuccess: () => { invalidar(); setInscrita(true); },
  });
  const retirar = useMutation({
    mutationFn: async () => {
      const ids = hecho!.inscripciones.length ? hecho!.inscripciones : inscripcionAviso ? [Number(inscripcionAviso)] : [];
      for (const i of ids) await api.post(`/api/lista-espera/${i}/retirar`, { motivo: 'HORA_ASIGNADA', idCita: hecho!.citas[0].id });
    },
    onSuccess: () => { invalidar(); setRetirada(true); },
  });

  const buscarPaciente = async (e: FormEvent) => {
    e.preventDefault();
    setErrorPaciente(null);
    try {
      const p = await api.get<Paciente>('/api/pacientes' + consulta({ documento: rut }));
      setIdPaciente(p.id);
    } catch (err) {
      setErrorPaciente(err);
    }
  };
  const buscarCupos = (e?: FormEvent) => {
    e?.preventDefault();
    setElegido(null); setPrevia(null); setSerie(false); setInscrita(false);
    setFiltro({ policlinico, profesion, profesional, desde, hasta });
  };

  const p = paciente.data;

  if (hecho) return <ReservaHecha hecho={hecho} paciente={p!} serie={serie} retirada={retirada}
    puedeRetirar={hecho.inscripciones.length > 0 || !!inscripcionAviso} alRetirar={() => retirar.mutate()} errorRetiro={retirar.error}
    nombreUsuario={usuario?.nombre ?? ''} alOtra={() => { setHecho(null); setElegido(null); setRetirada(false); cupos.refetch(); }} />;

  return (
    <>
      <Cabecera titulo="Reservar cita" subtitulo="Solo se muestran cupos publicados, no bloqueados y sin reserva" />
      <div className="cuerpo">
        {!p ? (
          <form className="tarjeta tarjeta--padding pila--chica" onSubmit={buscarPaciente} style={{ maxWidth: 640 }}>
            <label className="etiqueta" htmlFor="rut-reserva">Paciente</label>
            <div className="fila" style={{ flexWrap: 'nowrap' }}>
              <input id="rut-reserva" className="control" placeholder="RUT del paciente" value={rut} onChange={(e) => setRut(e.target.value)} autoFocus />
              <button className="boton" type="submit"><Search size={20} aria-hidden="true" /> Buscar</button>
            </div>
            {paciente.isLoading && <Cargando />}
            <MensajeError error={errorPaciente} />
            {errorPaciente instanceof ErrorDeApi && errorPaciente.estado === 404 && (
              <Link to={`/pacientes/nuevo?documento=${encodeURIComponent(rut)}`}>Registrar paciente</Link>
            )}
          </form>
        ) : (
          <section className="tarjeta fila fila--entre" style={{ padding: '14px 20px' }}>
            <div className="fila" style={{ gap: 12 }}>
              <Iniciales texto={p.iniciales} />
              <span className="texto-fuerte">{p.nombreCompleto}</span>
              <span className="texto-secundario">RUT {p.documentoFormateado}</span>
              {p.habilitado ? <span className="chip chip--exito">Habilitada</span> : <span className="chip chip--alerta">Sin verificación</span>}
            </div>
            <button className="boton boton--texto" onClick={() => { setIdPaciente(null); setRut(''); setElegido(null); }}>Cambiar paciente</button>
          </section>
        )}
        {p && !p.habilitado && (
          <div className="aviso aviso--alerta">Este paciente aún no tiene registrada la verificación de FONASA y CESFAM. <Link to={`/pacientes/${p.id}`}>Regístrala en su ficha</Link> antes de reservar.</div>
        )}

        {p && (
          <form className="grilla-4" style={{ gridTemplateColumns: 'repeat(4, minmax(0,1fr)) auto', alignItems: 'end' }} onSubmit={buscarCupos}>
            <div className="campo">
              <label htmlFor="r-pol">Policlínico</label>
              <select id="r-pol" className="control" value={policlinico} onChange={(e) => { setPoliclinico(Number(e.target.value)); setProfesional(''); }}>
                {policlinicos.data?.map((x) => <option key={x.id} value={x.id}>{x.nombre}</option>)}
              </select>
            </div>
            <div className="campo">
              <label htmlFor="r-prof">Profesión</label>
              <select id="r-prof" className="control" value={profesion} onChange={(e) => { setProfesion(e.target.value ? Number(e.target.value) : ''); setProfesional(''); }}>
                <option value="">Elige una profesión</option>
                {profesiones.data?.filter((x) => x.duracionMin != null).map((x) => <option key={x.id} value={x.id}>{x.nombre}</option>)}
              </select>
            </div>
            <div className="campo">
              <label htmlFor="r-pro">Profesional (opcional)</label>
              <select id="r-pro" className="control" value={profesional} onChange={(e) => setProfesional(e.target.value ? Number(e.target.value) : '')}>
                <option value="">Todos</option>
                {profesionalesFiltrados.map((x) => <option key={x.id} value={x.id}>{x.nombreCompleto}</option>)}
              </select>
            </div>
            <div className="campo">
              <span className="etiqueta" id="r-fechas">Fechas</span>
              <div className="fila" style={{ flexWrap: 'nowrap', gap: 6 }} role="group" aria-labelledby="r-fechas">
                <input aria-label="Desde" type="date" className="control" value={desde} min={hoyIso()} onChange={(e) => setDesde(e.target.value)} />
                <input aria-label="Hasta" type="date" className="control" value={hasta} min={desde} onChange={(e) => setHasta(e.target.value)} />
              </div>
            </div>
            <button className="boton" type="submit" disabled={!profesion}><Search size={20} aria-hidden="true" /> Buscar cupos</button>
          </form>
        )}

        {p && filtro && (
          <div style={{ display: 'grid', gridTemplateColumns: 'minmax(0, 1fr) 320px', gap: 20, alignItems: 'start' }} className="reserva-grilla">
            <section className="tarjeta tarjeta--padding pila">
              <div className="fila fila--entre">
                <div>
                  <h2>Horarios disponibles</h2>
                  <p className="texto-secundario texto-pequeno">{cupos.data?.length ?? 0} horarios · {nombreProfesion} · {policlinicos.data?.find((x) => x.id === filtro.policlinico)?.nombre}</p>
                </div>
                {(cupos.data?.length ?? 0) > 0 && (
                  <button className="boton boton--secundario boton--compacto" onClick={() => { setElegido(cupos.data![0]); setPrevia(null); }}>
                    <Clock size={18} aria-hidden="true" /> Primera hora disponible
                  </button>
                )}
              </div>
              {cupos.isLoading && <Cargando />}
              <MensajeError error={reservar.error && reservar.error instanceof ErrorDeApi && reservar.error.estado === 409 ? reservar.error : null} />
              {cupos.data?.length === 0 && (
                <div className="aviso aviso--alerta pila--chica" style={{ flexDirection: 'column' }}>
                  <strong>No hay cupos de {nombreProfesion} entre el {fechaCorta(String(filtro.desde))} y el {fechaCorta(String(filtro.hasta))}</strong>
                  <span>Puedes buscar en otras fechas o inscribir a {p.nombres.split(' ')[0]} en la lista de espera de {nombreProfesion}.</span>
                </div>
              )}
              <div className="columnas">
                {porDia.map(([fecha, lista]) => (
                  <section key={fecha} aria-label={fechaLarga(fecha)}>
                    <div className="columna__cabecera fila fila--entre"><span className="texto-fuerte">{diaCorto(fecha)}</span><span className="texto-secundario texto-pequeno">{lista.length} cupos</span></div>
                    {(['Mañana', 'Tarde'] as const).map((parte) => {
                      const deLaParte = lista.filter((c) => (parte === 'Mañana') === (c.horaInicio < '13:00'));
                      if (!deLaParte.length) return null;
                      return (
                        <div key={parte}>
                          <div className="texto-secundario" style={{ fontSize: '.75rem', fontWeight: 700, letterSpacing: '.06em', margin: '6px 0' }}>{parte.toUpperCase()}</div>
                          {deLaParte.map((c) => (
                            <button key={c.id} className={'cupo' + (elegido?.id === c.id ? ' cupo--elegido' : '')} aria-pressed={elegido?.id === c.id}
                              onClick={() => { setElegido(c); setPrevia(null); }}>
                              <span className="hora">{hora(c.horaInicio)}</span><br />
                              <span className="texto-secundario texto-pequeno">{c.profesional}, {c.box}</span>
                            </button>
                          ))}
                        </div>
                      );
                    })}
                  </section>
                ))}
              </div>
              <div className="tarjeta fila fila--entre" style={{ padding: 16, background: 'var(--color-papel)', boxShadow: 'none' }}>
                {inscrita ? (
                  <div className="aviso aviso--exito" style={{ flex: 1 }}>{p.nombres.split(' ')[0]} quedó registrada en la lista de espera de {nombreProfesion}. Puedes volver a la agenda del día o buscar otra atención.</div>
                ) : (
                  <>
                    <div><div className="texto-fuerte">¿Ningún horario le acomoda?</div>
                      <div className="texto-secundario texto-pequeno">Inscríbela en la lista de espera de {nombreProfesion}. Se atiende por orden de llegada.</div></div>
                    <button className="boton boton--secundario" disabled={inscribir.isPending} onClick={() => inscribir.mutate()}><Clock size={18} aria-hidden="true" /> Inscribir en lista de espera</button>
                  </>
                )}
              </div>
              <MensajeError error={inscribir.error} />
            </section>

            <aside className="tarjeta tarjeta--padding pila" aria-label="Resumen de la reserva" style={{ borderTop: '4px solid var(--color-tinta)' }}>
              <h2>Resumen de la reserva</h2>
              {!elegido ? <p className="texto-secundario">Elige un horario de la lista para ver el resumen y confirmar.</p> : (
                <>
                  {([['Profesional', `${elegido.profesional}, ${elegido.profesion}`], ['Lugar', `Policlínico ${elegido.policlinico}, ${elegido.box}`],
                    ['Fecha', fechaLarga(elegido.fecha)], ['Hora', `${hora(elegido.horaInicio)} (${elegido.duracion} minutos)`]] as const).map(([k, v]) => (
                    <div key={k}><div className="texto-secundario texto-pequeno">{k}</div><div className="texto-fuerte">{v}</div></div>
                  ))}
                  <label className="casilla"><input type="checkbox" checked={serie} onChange={(e) => { setSerie(e.target.checked); setPrevia(null); }} /> Reservar como serie de sesiones</label>
                  {serie && (
                    <div className="pila--chica">
                      <div className="campo">
                        <label htmlFor="cantidad">Cantidad de sesiones (la indica el profesional)</label>
                        <input id="cantidad" type="number" min={2} max={30} className="control" value={cantidad} onChange={(e) => { setCantidad(Number(e.target.value)); setPrevia(null); }} />
                      </div>
                      <p className="texto-secundario texto-pequeno">Semanal, el mismo día y a la misma hora.</p>
                      <button className="boton boton--secundario" disabled={cantidad < 2 || cantidad > 30 || previsualizar.isPending} onClick={() => previsualizar.mutate()}>Ver fechas de la serie</button>
                      <MensajeError error={previsualizar.error} />
                      {previa && (
                        <div className="pila--chica">
                          <span className="etiqueta">Fechas de la serie</span>
                          {previa.conCupo.map((f) => (
                            <label key={f.fecha} className="casilla"><input type="checkbox" checked={aceptadas.has(f.fecha)}
                              onChange={(e) => { const s = new Set(aceptadas); if (e.target.checked) s.add(f.fecha); else s.delete(f.fecha); setAceptadas(s); }} />
                              {fechaCorta(f.fecha)} a las {hora(f.hora)}</label>
                          ))}
                          {previa.sinCupo.length > 0 && (
                            <div className="aviso aviso--alerta pila--chica" style={{ flexDirection: 'column' }}>
                              <strong>Fechas sin cupo:</strong>
                              {previa.sinCupo.map((f) => <span key={f.fecha}>{fechaCorta(f.fecha)}: {f.motivo}</span>)}
                              <span className="texto-pequeno">Puedes reservar solo las fechas disponibles o cambiar el día o la hora.</span>
                            </div>
                          )}
                        </div>
                      )}
                    </div>
                  )}
                  <div className="aviso aviso--info pila--chica" style={{ flexDirection: 'column', gap: 2 }}>
                    <strong>Confirmación automática</strong>
                    <span>Se enviará al {p.telefonoFormateado}{p.adultoResponsable ? ` y a ${p.adultoResponsable.nombreCompleto}` : ''} apenas confirmes.</span>
                  </div>
                  <MensajeError error={reservar.error && !(reservar.error instanceof ErrorDeApi && reservar.error.estado === 409) ? reservar.error : null} />
                  <button className="boton" disabled={!p.habilitado || reservar.isPending || (serie && (!previa || aceptadas.size === 0))} onClick={() => reservar.mutate()}>
                    <Check size={20} aria-hidden="true" /> {serie ? `Confirmar ${aceptadas.size || ''} sesiones` : 'Confirmar reserva'}
                  </button>
                </>
              )}
            </aside>
          </div>
        )}
      </div>
    </>
  );
}

function ReservaHecha({ hecho, paciente, serie, retirada, puedeRetirar, alRetirar, errorRetiro, nombreUsuario, alOtra }: {
  hecho: { citas: Cita[] }; paciente: Paciente; serie: boolean; retirada: boolean; puedeRetirar: boolean; alRetirar: () => void;
  errorRetiro: unknown; nombreUsuario: string; alOtra: () => void;
}) {
  const c = hecho.citas[0];
  const ultima = hecho.citas[hecho.citas.length - 1];
  return (
    <>
      <Cabecera titulo={serie ? 'Serie reservada' : 'Cita reservada'} subtitulo="La confirmación se envía automáticamente al paciente" />
      <div className="cuerpo">
        <section className="tarjeta tarjeta--padding pila" style={{ maxWidth: 760 }}>
          <div className="fila"><span className="iniciales"><CalendarCheck size={20} /></span><h2>{serie ? 'Serie confirmada' : 'Reserva confirmada'}</h2></div>
          <div className="texto-fuerte" style={{ fontSize: '1.25rem' }}>
            {serie ? `${diaCorto(c.fecha).split(' ')[0]} a las ${hora(c.horaInicio)}, del ${fechaCorta(c.fecha).slice(0, 5)} al ${fechaCorta(ultima.fecha).slice(0, 5)}` : `${fechaLarga(c.fecha)}, ${hora(c.horaInicio)}`}
          </div>
          <div>{c.profesion} con {c.profesional}, Policlínico {c.policlinico}, {c.box}</div>
          <dl className="grilla-2" style={{ margin: 0 }}>
            <div><dt className="texto-secundario texto-pequeno">Paciente</dt><dd style={{ margin: 0 }}>{paciente.nombreCompleto}, RUT {paciente.documentoFormateado}</dd></div>
            <div><dt className="texto-secundario texto-pequeno">Duración</dt><dd style={{ margin: 0 }}>{c.duracion} minutos{serie ? ` por sesión · ${hecho.citas.length} sesiones` : ''}</dd></div>
            <div><dt className="texto-secundario texto-pequeno">Estado del horario</dt><dd style={{ margin: 0 }}>{serie ? `Los ${hecho.citas.length} horarios quedaron reservados` : 'Reservado, ya no aparece en la búsqueda'}</dd></div>
            <div><dt className="texto-secundario texto-pequeno">Registrado por</dt><dd style={{ margin: 0 }}>{nombreUsuario}, TENS/TONS</dd></div>
          </dl>
          <div className="aviso aviso--info"><MessageCircle size={20} aria-hidden="true" /><span>La confirmación por WhatsApp sale en menos de un minuto al {paciente.telefonoFormateado}{paciente.adultoResponsable ? ` y a ${paciente.adultoResponsable.nombreCompleto}` : ''}.</span></div>
          {puedeRetirar && !retirada && (
            <div className="aviso aviso--alerta fila fila--entre">
              <span>{paciente.nombres.split(' ')[0]} estaba en la lista de espera de {c.profesion}. ¿Quitarla de la lista porque se le asignó una hora?</span>
              <button className="boton boton--compacto" onClick={alRetirar}>Quitar de la lista de espera</button>
            </div>
          )}
          {retirada && <div className="aviso aviso--exito">Se quitó de la lista de espera con el motivo «Se le asignó una hora».</div>}
          <MensajeError error={errorRetiro} />
          <div className="fila">
            <Link className="boton boton--secundario" to="/agenda-dia">Volver a la agenda del día</Link>
            <Link className="boton boton--secundario" to={`/pacientes/${paciente.id}`}>Ver ficha del paciente</Link>
            <button className="boton boton--texto" onClick={alOtra}>Reservar otra hora</button>
          </div>
        </section>
      </div>
    </>
  );
}
