import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Area, AreaChart, Bar, BarChart, CartesianGrid, Cell, LabelList, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { BarChart3 } from 'lucide-react';
import { api, consulta } from '../../compartido/api';
import { Cargando, MensajeError, Vacio } from '../../compartido/componentes';
import { fechaDiaMes, hoyIso, mesCorto, nombreMes, numero, porcentaje } from '../../compartido/formato';
import { useSesion } from '../../compartido/sesion';
import { Cabecera } from '../DisenoPanel';
import { usePoliclinicos, useProfesionales } from '../consultas';

type Tipo = 'ocupacion' | 'asistencia' | 'demanda' | 'caracterizacion' | 'inasistencia-reiterada';
const NOMBRES: Record<Tipo, string> = {
  ocupacion: 'Ocupación', asistencia: 'Asistencia', demanda: 'Demanda', caracterizacion: 'Caracterización', 'inasistencia-reiterada': 'Inasistencia reiterada',
};
const TINTA = '#00687a';
const NARANJO = '#d9822b';
const AMARILLO = '#e2b100';
const SERIES = ['#00687a', '#d9822b', '#6a4c93', '#1f7a4d', '#c0392b', '#2d6cdf'];

interface Filtro { desde: string; hasta: string; policlinico: number | ''; profesional: number | '' }

function rangoMes(mes: string) {
  const [a, m] = mes.split('-').map(Number);
  const fin = new Date(a, m, 0).getDate();
  return { desde: `${mes}-01`, hasta: `${mes}-${String(fin).padStart(2, '0')}` };
}

/** M-07 Reportes de gestión (222:8334 …). Calculados en vivo (RNF-12). La TENS/TONS solo ve la inasistencia reiterada de su policlínico. */
export function Reportes() {
  const { usuario } = useSesion();
  const esTens = usuario?.perfil === 'TENS';
  const [tipo, setTipo] = useState<Tipo>(esTens ? 'inasistencia-reiterada' : 'ocupacion');
  const [mes, setMes] = useState(hoyIso().slice(0, 7));
  const [rango, setRango] = useState(false);
  const [desde, setDesde] = useState(rangoMes(hoyIso().slice(0, 7)).desde);
  const [hasta, setHasta] = useState(hoyIso());
  const [policlinico, setPoliclinico] = useState<number | ''>('');
  const [profesional, setProfesional] = useState<number | ''>('');
  const periodo = rango ? { desde, hasta } : rangoMes(mes);
  const [filtro, setFiltro] = useState<Filtro>({ ...periodo, policlinico: '', profesional: '' });
  const policlinicos = usePoliclinicos();
  const profesionales = useProfesionales();
  const pestanas: Tipo[] = esTens ? ['inasistencia-reiterada'] : ['ocupacion', 'asistencia', 'demanda', 'caracterizacion', 'inasistencia-reiterada'];

  const reporte = useQuery({
    queryKey: ['reporte', tipo, filtro],
    queryFn: () => api.get<Record<string, unknown>>(`/api/reportes/${tipo}` + consulta({ desde: filtro.desde, hasta: filtro.hasta,
      policlinico: filtro.policlinico, profesional: tipo === 'inasistencia-reiterada' ? '' : filtro.profesional })),
  });
  const textoPeriodo = rango ? `${fechaDiaMes(filtro.desde)} al ${fechaDiaMes(filtro.hasta)}` : nombreMes(filtro.desde).toLowerCase();

  return (
    <>
      <Cabecera titulo="Reportes de gestión" subtitulo={esTens ? 'Pacientes de este policlínico que faltaron varias veces. Los demás reportes son de la Administración.' : 'Calculados desde los registros vigentes, sin consolidar planillas'} />
      <div className="cuerpo">
        <div className="pestanas" role="tablist">
          {pestanas.map((t) => <button key={t} role="tab" aria-selected={tipo === t} onClick={() => setTipo(t)}>{NOMBRES[t]}</button>)}
        </div>
        <form className="fila" style={{ alignItems: 'flex-end' }} onSubmit={(e) => { e.preventDefault(); setFiltro({ ...periodo, policlinico, profesional }); }}>
          {!rango ? (
            <div className="campo"><label htmlFor="rep-mes">Período</label>
              <input id="rep-mes" type="month" className="control" value={mes} onChange={(e) => setMes(e.target.value)} /></div>
          ) : (
            <>
              <div className="campo"><label htmlFor="rep-desde">Desde</label><input id="rep-desde" type="date" className="control" value={desde} onChange={(e) => setDesde(e.target.value)} /></div>
              <div className="campo"><label htmlFor="rep-hasta">Hasta</label><input id="rep-hasta" type="date" className="control" value={hasta} onChange={(e) => setHasta(e.target.value)} /></div>
            </>
          )}
          <label className="casilla" style={{ minHeight: 48, alignItems: 'center' }}><input type="checkbox" checked={rango} onChange={(e) => setRango(e.target.checked)} /> Rango de fechas</label>
          {!esTens && (
            <>
              <div className="campo" style={{ minWidth: 200 }}><label htmlFor="rep-pol">Policlínico</label>
                <select id="rep-pol" className="control" value={policlinico} onChange={(e) => setPoliclinico(e.target.value ? Number(e.target.value) : '')}>
                  <option value="">Todos</option>{policlinicos.data?.map((p) => <option key={p.id} value={p.id}>{p.nombre}</option>)}
                </select></div>
              {tipo !== 'inasistencia-reiterada' && (
                <div className="campo" style={{ minWidth: 200 }}><label htmlFor="rep-pro">Profesional</label>
                  <select id="rep-pro" className="control" value={profesional} onChange={(e) => setProfesional(e.target.value ? Number(e.target.value) : '')}>
                    <option value="">Todos</option>{profesionales.data?.map((p) => <option key={p.id} value={p.id}>{p.nombreCompleto}</option>)}
                  </select></div>
              )}
            </>
          )}
          <button className="boton" type="submit"><BarChart3 size={20} aria-hidden="true" /> Generar reporte</button>
        </form>
        {reporte.isLoading && <Cargando />}
        <MensajeError error={reporte.error} />
        {reporte.data?.sinDatos === true && tipo !== 'inasistencia-reiterada' && (
          <div className="tarjeta"><Vacio><strong>No hay registros en el período seleccionado.</strong><br />Elige un período con citas, por ejemplo uno anterior a la fecha actual.</Vacio></div>
        )}
        {reporte.data && (reporte.data.sinDatos !== true || tipo === 'inasistencia-reiterada') && (
          <>
            {tipo === 'ocupacion' && <Ocupacion d={reporte.data as never} />}
            {tipo === 'asistencia' && <Asistencia d={reporte.data as never} />}
            {tipo === 'demanda' && <Demanda d={reporte.data as never} />}
            {tipo === 'caracterizacion' && <Caracterizacion d={reporte.data as never} />}
            {tipo === 'inasistencia-reiterada' && <Inasistencia d={reporte.data as never} periodo={textoPeriodo} esTens={esTens} policlinico={usuario?.policlinicoBase ?? null} />}
          </>
        )}
      </div>
    </>
  );
}

function Indicador({ titulo, valor, pie, comparacion }: { titulo: string; valor: string; pie?: string; comparacion?: { delta: number; mejorSiBaja?: boolean } }) {
  const c = comparacion;
  const bueno = c ? (c.mejorSiBaja ? c.delta <= 0 : c.delta >= 0) : true;
  return (
    <div className="indicador">
      <div className="indicador__texto">{titulo}</div>
      <div className="fila" style={{ gap: 10 }}>
        <span className="indicador__valor" style={{ fontSize: '2.25rem', lineHeight: '2.75rem' }}>{valor}</span>
        {c && <span className={'chip ' + (bueno ? 'chip--exito' : 'chip--error')}>{c.delta >= 0 ? '▲' : '▼'} {Math.abs(c.delta).toLocaleString('es-CL', { maximumFractionDigits: 1, minimumFractionDigits: 1 })} pts vs período anterior</span>}
      </div>
      {pie && <div className="texto-secundario texto-pequeno">{pie}</div>}
    </div>
  );
}

interface FilaOcupacion { policlinico: string; profesional: string; profesion: string; ofrecidos: number; reservados: number; disponibles: number; porcentajeOcupacion: number }

function Ocupacion({ d }: { d: { ofrecidos: number; reservados: number; disponibles: number; porcentajeOcupacion: number; anterior: { porcentajeOcupacion: number }; filas: FilaOcupacion[] } }) {
  const porPol = Object.values(d.filas.reduce<Record<string, { nombre: string; ofrecidos: number; reservados: number }>>((acc, f) => {
    acc[f.policlinico] ??= { nombre: 'Policlínico ' + f.policlinico, ofrecidos: 0, reservados: 0 };
    acc[f.policlinico].ofrecidos += f.ofrecidos;
    acc[f.policlinico].reservados += f.reservados;
    return acc;
  }, {})).map((x) => ({ ...x, pct: x.ofrecidos ? Math.round((x.reservados * 1000) / x.ofrecidos) / 10 : 0 })).sort((a, b) => b.pct - a.pct);
  return (
    <>
      <div className="grilla-4">
        <Indicador titulo="Cupos ofrecidos" valor={numero(d.ofrecidos)} pie="publicados y no bloqueados" />
        <Indicador titulo="Cupos reservados" valor={numero(d.reservados)} />
        <Indicador titulo="Porcentaje de ocupación" valor={porcentaje(d.porcentajeOcupacion)} pie="reservados / publicados"
          comparacion={{ delta: d.porcentajeOcupacion - d.anterior.porcentajeOcupacion }} />
        <Indicador titulo="Disponibles sin reservar" valor={numero(d.disponibles)} />
      </div>
      <section className="tarjeta tarjeta--padding pila">
        <h2>Ocupación por policlínico</h2>
        <div style={{ height: Math.max(220, porPol.length * 44) }}>
          <ResponsiveContainer>
            <BarChart data={porPol} layout="vertical" margin={{ left: 40, right: 60 }}>
              <XAxis type="number" domain={[0, 100]} hide />
              <YAxis type="category" dataKey="nombre" width={200} tickLine={false} axisLine={false} />
              <Tooltip formatter={(v: number) => porcentaje(v)} />
              <Bar dataKey="pct" name="Ocupación" radius={[6, 6, 6, 6]} barSize={16}>
                {porPol.map((_, i) => <Cell key={i} fill={SERIES[i % SERIES.length]} />)}
                <LabelList dataKey="pct" position="right" formatter={(v: number) => porcentaje(v)} />
              </Bar>
            </BarChart>
          </ResponsiveContainer>
        </div>
      </section>
      <section className="tarjeta tabla-contenedor">
        <table className="tabla">
          <thead><tr><th>Policlínico</th><th>Profesional</th><th>Ofrecidos</th><th>Reservados</th><th>Disponibles</th><th>Ocupación</th></tr></thead>
          <tbody>{d.filas.map((f, i) => <tr key={i}><td>{f.policlinico}</td><td>{f.profesional}<br /><span className="texto-pequeno texto-secundario">{f.profesion}</span></td>
            <td>{numero(f.ofrecidos)}</td><td>{numero(f.reservados)}</td><td>{numero(f.disponibles)}</td><td>{porcentaje(f.porcentajeOcupacion)}</td></tr>)}</tbody>
        </table>
      </section>
    </>
  );
}

interface Conteo { etiqueta: string; reservadas: number; asistidas: number; inasistencias: number; porcentajeInasistencia: number }

function Asistencia({ d }: { d: { total: Conteo; anterior: Conteo; porProfesion: Conteo[]; evolucion: Conteo[] } }) {
  const promedio = d.total.porcentajeInasistencia;
  const profesiones = [...d.porProfesion].sort((a, b) => b.porcentajeInasistencia - a.porcentajeInasistencia);
  return (
    <>
      <div className="grilla-4">
        <Indicador titulo="Citas reservadas" valor={numero(d.total.reservadas)} />
        <Indicador titulo="Asistidas" valor={numero(d.total.asistidas)} />
        <Indicador titulo="Inasistencias" valor={numero(d.total.inasistencias)} />
        <Indicador titulo="Porcentaje de inasistencia" valor={porcentaje(promedio)}
          pie={`${numero(d.total.inasistencias)} / ${numero(d.total.asistidas + d.total.inasistencias)} (asistidas + inasistencias)`}
          comparacion={{ delta: promedio - d.anterior.porcentajeInasistencia, mejorSiBaja: true }} />
      </div>
      <section className="tarjeta tarjeta--padding pila">
        <h2>Inasistencia por profesión</h2>
        <div style={{ height: Math.max(200, profesiones.length * 42) }}>
          <ResponsiveContainer>
            <BarChart data={profesiones} layout="vertical" margin={{ left: 20, right: 60 }}>
              <XAxis type="number" hide domain={[0, 'dataMax']} />
              <YAxis type="category" dataKey="etiqueta" width={130} tickLine={false} axisLine={false} />
              <Tooltip formatter={(v: number) => porcentaje(v)} />
              <Bar dataKey="porcentajeInasistencia" name="Inasistencia" radius={[6, 6, 6, 6]} barSize={16}>
                {profesiones.map((p, i) => <Cell key={i} fill={p.porcentajeInasistencia > promedio ? NARANJO : TINTA} />)}
                <LabelList dataKey="porcentajeInasistencia" position="right" formatter={(v: number) => porcentaje(v)} />
              </Bar>
            </BarChart>
          </ResponsiveContainer>
        </div>
        <div className="fila texto-pequeno texto-secundario" style={{ gap: 16 }}>
          <span><span style={{ display: 'inline-block', width: 12, height: 12, borderRadius: 3, background: NARANJO, marginRight: 6 }} />Sobre el promedio del período ({porcentaje(promedio)})</span>
          <span><span style={{ display: 'inline-block', width: 12, height: 12, borderRadius: 3, background: TINTA, marginRight: 6 }} />Igual o bajo el promedio</span>
        </div>
      </section>
      <section className="tarjeta tarjeta--padding pila">
        <div className="fila fila--entre"><h2>Evolución de la inasistencia</h2><span className="texto-secundario texto-pequeno">Últimos 6 meses · % de citas reservadas</span></div>
        <div style={{ height: 220 }}>
          <ResponsiveContainer>
            <AreaChart data={d.evolucion.map((e) => ({ ...e, mes: mesCorto(e.etiqueta) }))} margin={{ top: 24, right: 24, left: 0, bottom: 0 }}>
              <defs><linearGradient id="relleno" x1="0" y1="0" x2="0" y2="1"><stop offset="0%" stopColor={TINTA} stopOpacity={0.25} /><stop offset="100%" stopColor={TINTA} stopOpacity={0} /></linearGradient></defs>
              <CartesianGrid vertical={false} stroke="#dce3ea" />
              <XAxis dataKey="mes" tickLine={false} axisLine={false} />
              <YAxis tickFormatter={(v) => `${v} %`} tickLine={false} axisLine={false} width={50} domain={['dataMin - 2', 'dataMax + 2']} />
              <Tooltip formatter={(v: number) => porcentaje(v)} />
              <Area type="monotone" dataKey="porcentajeInasistencia" name="Inasistencia" stroke={TINTA} strokeWidth={2.5} fill="url(#relleno)" dot={{ r: 4, fill: '#fff', stroke: TINTA, strokeWidth: 2 }}>
                <LabelList dataKey="porcentajeInasistencia" position="top" formatter={(v: number) => porcentaje(v)} style={{ fontSize: 12 }} />
              </Area>
            </AreaChart>
          </ResponsiveContainer>
        </div>
      </section>
    </>
  );
}

interface FilaDemanda { profesional: string; profesion: string; policlinicos: string; reservas: number; enEspera: number; demanda: number }

function Demanda({ d }: { d: { reservas: number; enEspera: number; filas: FilaDemanda[] } }) {
  const filas = [...d.filas].filter((f) => f.demanda > 0).sort((a, b) => b.demanda - a.demanda);
  return (
    <>
      <div className="grilla-3">
        <Indicador titulo="Citas reservadas" valor={numero(d.reservas)} />
        <Indicador titulo="En lista de espera" valor={numero(d.enEspera)} />
        <Indicador titulo="Demanda total" valor={numero(d.reservas + d.enEspera)} pie="reservas + lista de espera" />
      </div>
      <section className="tarjeta tarjeta--padding pila">
        <h2>Demanda por profesional</h2>
        <div style={{ height: Math.max(220, filas.length * 44) }}>
          <ResponsiveContainer>
            <BarChart data={filas} layout="vertical" margin={{ left: 20, right: 40 }}>
              <XAxis type="number" hide />
              <YAxis type="category" dataKey="profesional" width={170} tickLine={false} axisLine={false} />
              <Tooltip />
              <Bar dataKey="reservas" name="Citas reservadas" stackId="d" fill={TINTA} barSize={18} />
              <Bar dataKey="enEspera" name="En lista de espera" stackId="d" fill={AMARILLO} radius={[0, 6, 6, 0]} barSize={18}>
                <LabelList dataKey="demanda" position="right" />
              </Bar>
            </BarChart>
          </ResponsiveContainer>
        </div>
        <p className="texto-secundario texto-pequeno">Tinta: citas reservadas con el profesional. Amarillo: pacientes en lista de espera de su profesión.</p>
      </section>
      <section className="tarjeta tabla-contenedor">
        <table className="tabla">
          <thead><tr><th>Profesional</th><th>Profesión</th><th>Policlínicos</th><th>Reservas</th><th>En espera</th><th>Demanda</th></tr></thead>
          <tbody>{d.filas.map((f, i) => <tr key={i}><td>{f.profesional}</td><td>{f.profesion}</td><td>{f.policlinicos}</td><td>{numero(f.reservas)}</td><td>{numero(f.enEspera)}</td><td className="texto-fuerte">{numero(f.demanda)}</td></tr>)}</tbody>
        </table>
      </section>
    </>
  );
}

interface Cat { nombre: string; cantidad: number }

function Caracterizacion({ d }: { d: { total: number; porEdad: Cat[]; porGenero: Cat[]; porNacionalidad: Cat[]; adultos: number; ninos: number; adultosMayores: number; porcentajeAdultosMayores: number } }) {
  const extranjera = d.porNacionalidad.find((x) => x.nombre === 'Otra')?.cantidad ?? 0;
  const grafico = (titulo: string, datos: Cat[]) => (
    <section className="tarjeta tarjeta--padding pila--chica">
      <h2>{titulo}</h2>
      <div style={{ height: Math.max(140, datos.length * 40) }}>
        <ResponsiveContainer>
          <BarChart data={datos} layout="vertical" margin={{ left: 10, right: 60 }}>
            <XAxis type="number" hide />
            <YAxis type="category" dataKey="nombre" width={120} tickLine={false} axisLine={false} />
            <Tooltip />
            <Bar dataKey="cantidad" name="Pacientes" radius={[6, 6, 6, 6]} barSize={16}>
              {datos.map((_, i) => <Cell key={i} fill={SERIES[i % SERIES.length]} />)}
              <LabelList dataKey="cantidad" position="right" formatter={(v: number) => `${numero(v)} (${porcentaje(d.total ? (v * 100) / d.total : 0)})`} />
            </Bar>
          </BarChart>
        </ResponsiveContainer>
      </div>
    </section>
  );
  return (
    <>
      <div className="grilla-4">
        <Indicador titulo="Pacientes atendidos" valor={numero(d.total)} pie="con al menos una cita asistida" />
        <Indicador titulo="Adultos mayores" valor={porcentaje(d.porcentajeAdultosMayores)} pie={`${numero(d.adultosMayores)} de 60 años o más`} />
        <Indicador titulo="Adultos y niños" valor={`${numero(d.adultos)} / ${numero(d.ninos)}`} pie="18 años o más / menores de 18" />
        <Indicador titulo="Nacionalidad extranjera" valor={porcentaje(d.total ? (extranjera * 100) / d.total : 0)} pie={`${numero(extranjera)} pacientes`} />
      </div>
      <div className="grilla-3">
        {grafico('Grupo etario', d.porEdad)}
        {grafico('Género', d.porGenero)}
        {grafico('Nacionalidad', d.porNacionalidad)}
      </div>
    </>
  );
}

interface FilaInasistencia { idPaciente: number; paciente: string; documento: string; inasistencias: number; ultimaFecha: string; ultimaProfesion: string; telefono: string }

function Inasistencia({ d, periodo, esTens, policlinico }: { d: { umbral: number; filas: FilaInasistencia[] }; periodo: string; esTens: boolean; policlinico: string | null }) {
  return (
    <section className="tarjeta tarjeta--padding pila">
      <h2>Pacientes con {d.umbral} o más inasistencias{esTens && policlinico ? `, Policlínico ${policlinico}` : ''} ({periodo})</h2>
      {d.filas.length === 0 ? <Vacio>No hay registros en el período seleccionado.</Vacio> : (
        <div className="tabla-contenedor">
          <table className="tabla">
            <thead><tr><th>Paciente</th><th>RUT</th><th>Inasistencias</th><th>Última inasistencia</th><th>Teléfono</th></tr></thead>
            <tbody>{d.filas.map((f) => <tr key={f.idPaciente}><td className="texto-fuerte">{f.paciente}</td><td>{f.documento}</td>
              <td><span className="chip chip--error">{f.inasistencias}</span></td><td>{fechaDiaMes(f.ultimaFecha)}, {f.ultimaProfesion}</td><td>{f.telefono}</td></tr>)}</tbody>
          </table>
        </div>
      )}
      <p className="texto-secundario texto-pequeno">{esTens ? 'Úsalo para llamar al paciente antes de su próxima hora y confirmar que podrá asistir.' : 'Este listado también lo ve la TENS/TONS desde Reportes. Sirve para contactar al paciente antes de su próxima hora.'}</p>
    </section>
  );
}
