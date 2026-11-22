import { useEffect, useState } from 'react';
import { Area, AreaChart, Bar, BarChart, CartesianGrid, Cell, LabelList, Line, LineChart, Pie, PieChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { mesCorto, numero, porcentaje } from '../../compartido/formato';

/*
 * Paleta de gráficos validada con el validador de dataviz (modo claro, superficie blanca): banda de luminosidad, croma,
 * separación para daltonismo (ΔE ≥ 16) y contraste ≥ 3:1. El verde azulado es una variante algo más viva de la tinta
 * institucional (#00687a se lee casi gris como marca de datos). Orden categórico fijo: VERDE_AZULADO, NARANJO, VIOLETA;
 * lo que sobra se agrupa en NEUTRO. Las barras de magnitud usan un solo color: el color sigue a la entidad, nunca al ranking.
 */
export const VERDE_AZULADO = '#00849e';
export const NARANJO = '#c06512';
export const VIOLETA = '#6a4c93';
export const NEUTRO = '#aab3bd';
const PISTA = '#eef1f4';
const CATEGORICOS = [VERDE_AZULADO, NARANJO, VIOLETA];
const ANIMACION = { animationDuration: 550, animationEasing: 'ease-out' as const };
const TOOLTIP = {
  contentStyle: { borderRadius: 8, border: '1px solid #dce3ea', boxShadow: '0 4px 14px rgba(16,24,40,.10)', fontSize: 14, padding: '8px 12px' },
  labelStyle: { color: '#2b2f36', fontWeight: 600, marginBottom: 2 },
  itemStyle: { color: '#2b2f36' },
  cursor: { fill: 'rgba(0, 104, 122, 0.06)' },
};

export function Indicador({ titulo, valor, pie, comparacion, serie }: {
  titulo: string; valor: string; pie?: string; comparacion?: { delta: number; mejorSiBaja?: boolean }; serie?: (number | null)[];
}) {
  const c = comparacion;
  const bueno = c ? (c.mejorSiBaja ? c.delta <= 0 : c.delta >= 0) : true;
  return (
    <div className="indicador">
      <div className="indicador__texto">{titulo}</div>
      <div className="fila" style={{ gap: 10 }}>
        <span className="indicador__valor" style={{ fontSize: '2.25rem', lineHeight: '2.75rem', fontVariantNumeric: 'tabular-nums' }}>
          <NumeroAnimado texto={valor} />
        </span>
        {c && <span className={'chip ' + (bueno ? 'chip--exito' : 'chip--error')}>{c.delta >= 0 ? '▲' : '▼'} {Math.abs(c.delta).toLocaleString('es-CL', { maximumFractionDigits: 1, minimumFractionDigits: 1 })} pts vs período anterior</span>}
      </div>
      {serie && serie.filter((v) => v != null).length > 1 && <MiniTendencia serie={serie} />}
      {pie && <div className="texto-secundario texto-pequeno">{pie}</div>}
    </div>
  );
}

/**
 * Cuenta desde 0 hasta el valor en 600 ms con ease-out al cargar el reporte (solo formatos «1.724» o «15,1 %»; lo demás,
 * como «628 / 91», se muestra fijo). El lector de pantalla recibe directamente el valor final.
 */
function NumeroAnimado({ texto }: { texto: string }) {
  const m = /^([\d.]+)(?:,(\d+))?(\s?%)?$/.exec(texto);
  const objetivo = m ? Number(m[1].replace(/\./g, '') + (m[2] ? '.' + m[2] : '')) : null;
  const decimales = m?.[2]?.length ?? 0;
  const [actual, setActual] = useState(0);
  useEffect(() => {
    if (objetivo == null) return;
    if (window.matchMedia?.('(prefers-reduced-motion: reduce)').matches) { setActual(objetivo); return; }
    let cuadro = 0;
    const inicio = performance.now();
    const paso = (t: number) => {
      const avance = Math.min(1, (t - inicio) / 600);
      setActual(objetivo * (1 - Math.pow(1 - avance, 3)));
      if (avance < 1) cuadro = requestAnimationFrame(paso);
    };
    cuadro = requestAnimationFrame(paso);
    return () => cancelAnimationFrame(cuadro);
  }, [objetivo]);
  if (objetivo == null || !m) return <>{texto}</>;
  const visible = actual.toLocaleString('es-CL', { minimumFractionDigits: decimales, maximumFractionDigits: decimales }) + (m[3] ?? '');
  return <span aria-label={texto}><span aria-hidden="true">{visible}</span></span>;
}

/** Mini tendencia sin ejes dentro de un indicador (decorativa: el número grande es el dato; el detalle está en «Evolución»). */
function MiniTendencia({ serie }: { serie: (number | null)[] }) {
  const datos = serie.map((v, i) => ({ i, v }));
  return (
    <div className="mini-tendencia" aria-hidden="true">
      <ResponsiveContainer>
        <LineChart data={datos} margin={{ top: 4, right: 4, bottom: 4, left: 4 }}>
          <YAxis hide domain={['dataMin - 1', 'dataMax + 1']} />
          <Line type="monotone" dataKey="v" stroke={VERDE_AZULADO} strokeWidth={2} dot={false} isAnimationActive={false} connectNulls />
        </LineChart>
      </ResponsiveContainer>
    </div>
  );
}

interface Parte { nombre: string; valor: number; color: string }

/** Anillo parte-del-todo (2 a 4 partes) con el dato principal al centro y leyenda con valores (nunca solo color). */
function Anillo({ titulo, partes, centro, subtitulo, ancho = false }: {
  titulo: string; partes: Parte[]; centro: string; subtitulo: string; ancho?: boolean;
}) {
  const total = partes.reduce((a, p) => a + p.valor, 0);
  return (
    <section className={'tarjeta tarjeta--padding pila' + (ancho ? ' anillo-tarjeta--ancha' : '')}>
      <h2>{titulo}</h2>
      <div className="anillo-cuerpo">
      <div className="anillo">
        <ResponsiveContainer>
          <PieChart>
            <Pie data={partes} dataKey="valor" nameKey="nombre" innerRadius="68%" outerRadius="96%" paddingAngle={2} cornerRadius={4}
              stroke="#fff" strokeWidth={2} startAngle={90} endAngle={-270} {...ANIMACION}>
              {partes.map((p) => <Cell key={p.nombre} fill={p.color} />)}
            </Pie>
            <Tooltip {...TOOLTIP} formatter={(v: number) => `${numero(v)} (${porcentaje(total ? (v * 100) / total : 0)})`} />
          </PieChart>
        </ResponsiveContainer>
        <div className="anillo__centro">
          <span className="anillo__valor">{centro}</span>
          <span className="anillo__texto">{subtitulo}</span>
        </div>
      </div>
      <ul className="leyenda">
        {partes.map((p) => (
          <li key={p.nombre}>
            <span className="leyenda__marca" style={{ background: p.color }} />
            <span>{p.nombre}</span>
            <span className="leyenda__valor">{numero(p.valor)} · {porcentaje(total ? (p.valor * 100) / total : 0)}</span>
          </li>
        ))}
      </ul>
      </div>
    </section>
  );
}

/** Barras horizontales de magnitud: un color, pista de fondo, etiqueta al final y extremos redondeados de 4 px. */
function BarrasHorizontales<T extends object>({ datos, clave, etiqueta, ancho, formato, colorDe, nombre }: {
  datos: T[]; clave: keyof T & string; etiqueta: keyof T & string; ancho: number; formato: (v: number) => string;
  colorDe?: (fila: T) => string; nombre: string;
}) {
  return (
    <div style={{ height: Math.max(180, datos.length * 42) }}>
      <ResponsiveContainer>
        <BarChart data={datos} layout="vertical" margin={{ left: 8, right: 72 }} barCategoryGap={10}>
          <XAxis type="number" hide domain={[0, 'dataMax']} />
          <YAxis type="category" dataKey={etiqueta} width={ancho} tickLine={false} axisLine={false} tick={{ fill: '#2b2f36', fontSize: 15 }} />
          <Tooltip {...TOOLTIP} formatter={(v: number) => formato(v)} />
          <Bar dataKey={clave} name={nombre} radius={[4, 4, 4, 4]} barSize={14} background={{ fill: PISTA, radius: 4 }} {...ANIMACION}>
            {datos.map((f, i) => <Cell key={i} fill={colorDe ? colorDe(f) : VERDE_AZULADO} />)}
            <LabelList dataKey={clave} position="right" formatter={(v: number) => formato(v)} style={{ fill: '#2b2f36', fontSize: 14, fontWeight: 600 }} />
          </Bar>
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
}

interface FilaOcupacion { policlinico: string; profesional: string; profesion: string; ofrecidos: number; reservados: number; disponibles: number; porcentajeOcupacion: number }

/** Agrupa las filas de ocupación por policlínico o por profesional, con su porcentaje. */
function ocupacionPor(filas: FilaOcupacion[], clave: 'policlinico' | 'profesional') {
  return Object.values(filas.reduce<Record<string, { nombre: string; ofrecidos: number; reservados: number }>>((acc, f) => {
    acc[f[clave]] ??= { nombre: f[clave], ofrecidos: 0, reservados: 0 };
    acc[f[clave]].ofrecidos += f.ofrecidos;
    acc[f[clave]].reservados += f.reservados;
    return acc;
  }, {})).map((x) => ({ ...x, pct: x.ofrecidos ? Math.round((x.reservados * 1000) / x.ofrecidos) / 10 : 0 })).sort((a, b) => b.pct - a.pct);
}

export function Ocupacion({ d }: { d: { ofrecidos: number; reservados: number; disponibles: number; porcentajeOcupacion: number; anterior: { ofrecidos: number; porcentajeOcupacion: number }; filas: FilaOcupacion[] } }) {
  // Con un solo policlínico (por filtro) se desglosa por profesional; si también es uno solo, las barras repetirían el anillo.
  const porPol = ocupacionPor(d.filas, 'policlinico');
  const porProf = ocupacionPor(d.filas, 'profesional');
  const desglose = porPol.length > 1 ? { titulo: 'Ocupación por policlínico', datos: porPol, ancho: 170 }
    : porProf.length > 1 ? { titulo: 'Ocupación por profesional', datos: porProf, ancho: 190 } : null;
  const anillo = (
    <Anillo titulo="Cupos del período" centro={porcentaje(d.porcentajeOcupacion)} subtitulo="ocupación" ancho={!desglose}
      partes={[{ nombre: 'Reservados', valor: d.reservados, color: VERDE_AZULADO }, { nombre: 'Disponibles', valor: d.disponibles, color: NEUTRO }]} />
  );
  return (
    <>
      <div className="grilla-4">
        <Indicador titulo="Cupos ofrecidos" valor={numero(d.ofrecidos)} pie="publicados y no bloqueados" />
        <Indicador titulo="Cupos reservados" valor={numero(d.reservados)} />
        <Indicador titulo="Porcentaje de ocupación" valor={porcentaje(d.porcentajeOcupacion)} pie="reservados / publicados"
          comparacion={d.anterior.ofrecidos > 0 ? { delta: d.porcentajeOcupacion - d.anterior.porcentajeOcupacion } : undefined} />
        <Indicador titulo="Disponibles sin reservar" valor={numero(d.disponibles)} />
      </div>
      {desglose ? (
        <div className="grilla-graficos">
          {anillo}
          <section className="tarjeta tarjeta--padding pila">
            <div className="fila fila--entre"><h2>{desglose.titulo}</h2><span className="texto-secundario texto-pequeno">% de cupos publicados que se reservaron</span></div>
            <BarrasHorizontales datos={desglose.datos} clave="pct" etiqueta="nombre" ancho={desglose.ancho} nombre="Ocupación" formato={porcentaje} />
          </section>
        </div>
      ) : anillo}
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

/** Fecha ISO → «10 oct.» para avisar que el último mes de la evolución está incompleto. */
function diaMes(iso: string) {
  const [a, m, d] = iso.split('-').map(Number);
  return new Date(a, m - 1, d).toLocaleDateString('es-CL', { day: 'numeric', month: 'short' });
}

export function Asistencia({ d }: { d: { hasta: string; total: Conteo; anterior: Conteo; porProfesion: Conteo[]; evolucion: Conteo[] } }) {
  const conResultado = (c: Conteo) => c.asistidas + c.inasistencias > 0;
  // Si todavía no se registra asistencia (p. ej. una semana futura), un 0 % sería engañoso.
  const hayResultados = conResultado(d.total);
  const promedio = d.total.porcentajeInasistencia;
  const profesiones = [...d.porProfesion].filter(conResultado).sort((a, b) => b.porcentajeInasistencia - a.porcentajeInasistencia);
  // Meses sin asistencia registrada van como null: la línea no baja a 0 %.
  const evolucion = d.evolucion.map((e) => ({ ...e, mes: mesCorto(e.etiqueta), valor: conResultado(e) ? e.porcentajeInasistencia : null }));
  const ultimo = evolucion.map((e) => e.valor != null).lastIndexOf(true);
  const [a, m, dia] = d.hasta.split('-').map(Number);
  const mesIncompleto = dia < new Date(a, m, 0).getDate();
  return (
    <>
      <div className="grilla-4">
        <Indicador titulo="Citas reservadas" valor={numero(d.total.reservadas)} />
        <Indicador titulo="Asistidas" valor={numero(d.total.asistidas)} />
        <Indicador titulo="Inasistencias" valor={numero(d.total.inasistencias)} />
        <Indicador titulo="Porcentaje de inasistencia" valor={hayResultados ? porcentaje(promedio) : '—'}
          pie={hayResultados ? `${numero(d.total.inasistencias)} / ${numero(d.total.asistidas + d.total.inasistencias)} (asistidas + inasistencias)` : 'aún sin asistencia registrada'}
          comparacion={hayResultados && conResultado(d.anterior) ? { delta: promedio - d.anterior.porcentajeInasistencia, mejorSiBaja: true } : undefined}
          serie={evolucion.map((e) => e.valor)} />
      </div>
      {!hayResultados ? (
        <div className="aviso aviso--info">
          {d.total.reservadas === 1 ? 'La cita' : `Las ${numero(d.total.reservadas)} citas`} de este período todavía no {d.total.reservadas === 1 ? 'tiene' : 'tienen'} asistencia registrada.
          El porcentaje de inasistencia se calcula sobre las citas asistidas e inasistidas.
        </div>
      ) : (() => {
        const anillo = (
          <Anillo titulo="Resultado de las citas" centro={porcentaje(promedio)} subtitulo="inasistencia" ancho={profesiones.length <= 1}
            partes={[{ nombre: 'Asistidas', valor: d.total.asistidas, color: VERDE_AZULADO }, { nombre: 'Inasistencias', valor: d.total.inasistencias, color: NARANJO }]} />
        );
        // Con una sola profesión (p. ej. al filtrar por profesional) la barra sería igual al anillo.
        return profesiones.length <= 1 ? anillo : (
          <div className="grilla-graficos">
            {anillo}
            <section className="tarjeta tarjeta--padding pila">
              <h2>Inasistencia por profesión</h2>
              <BarrasHorizontales datos={profesiones} clave="porcentajeInasistencia" etiqueta="etiqueta" ancho={130} nombre="Inasistencia"
                formato={porcentaje} colorDe={(p) => (p.porcentajeInasistencia > promedio ? NARANJO : VERDE_AZULADO)} />
              <ul className="leyenda leyenda--fila">
                <li><span className="leyenda__marca" style={{ background: NARANJO }} />Sobre el promedio del período ({porcentaje(promedio)})</li>
                <li><span className="leyenda__marca" style={{ background: VERDE_AZULADO }} />Igual o bajo el promedio</li>
              </ul>
            </section>
          </div>
        );
      })()}
      <section className="tarjeta tarjeta--padding pila">
        <div className="fila fila--entre"><h2>Evolución de la inasistencia</h2>
          <span className="texto-secundario texto-pequeno">
            Últimos 6 meses · % sobre asistidas + inasistencias{mesIncompleto ? ` · ${mesCorto(d.hasta.slice(0, 7))} hasta el ${diaMes(d.hasta)}` : ''}
          </span></div>
        <div style={{ height: 240 }}>
          <ResponsiveContainer>
            <AreaChart data={evolucion} margin={{ top: 28, right: 28, left: 0, bottom: 0 }}>
              <defs><linearGradient id="relleno" x1="0" y1="0" x2="0" y2="1"><stop offset="0%" stopColor={VERDE_AZULADO} stopOpacity={0.22} /><stop offset="100%" stopColor={VERDE_AZULADO} stopOpacity={0} /></linearGradient></defs>
              <CartesianGrid vertical={false} stroke="#e6ebf0" />
              <XAxis dataKey="mes" tickLine={false} axisLine={false} tick={{ fill: '#5b6472', fontSize: 14 }} />
              <YAxis tickFormatter={(v) => `${v} %`} tickLine={false} axisLine={false} width={50} allowDecimals={false} tickCount={4}
                domain={[(min: number) => Math.max(0, Math.floor(min - 2)), (max: number) => Math.ceil(max + 2)]} tick={{ fill: '#5b6472', fontSize: 13 }} />
              <Tooltip {...TOOLTIP} cursor={{ stroke: '#aab3bd', strokeDasharray: '4 4' }} formatter={(v) => (typeof v === 'number' ? porcentaje(v) : 'sin registros')} />
              <Area type="monotone" dataKey="valor" name="Inasistencia" stroke={VERDE_AZULADO} strokeWidth={2} fill="url(#relleno)" {...ANIMACION}
                dot={(p: { cx?: number; cy?: number; index?: number; value?: number | null }) => (p.value == null || p.cy == null
                  ? <g key={p.index} />
                  : <circle key={p.index} cx={p.cx} cy={p.cy} r={p.index === ultimo ? 6 : 4} fill={p.index === ultimo ? VERDE_AZULADO : '#fff'}
                    stroke={p.index === ultimo ? '#fff' : VERDE_AZULADO} strokeWidth={2} />
                )}
                activeDot={{ r: 6, fill: VERDE_AZULADO, stroke: '#fff', strokeWidth: 2 }}>
                <LabelList dataKey="valor" position="top" offset={12} formatter={(v: number | null) => (v == null ? '' : porcentaje(v))} style={{ fontSize: 12, fill: '#5b6472' }} />
              </Area>
            </AreaChart>
          </ResponsiveContainer>
        </div>
      </section>
    </>
  );
}

interface FilaDemanda { profesional: string; profesion: string; policlinicos: string; reservas: number; enEspera: number; demanda: number }

export function Demanda({ d }: { d: { reservas: number; enEspera: number; filas: FilaDemanda[] } }) {
  const filas = [...d.filas].filter((f) => f.demanda > 0).sort((a, b) => b.demanda - a.demanda);
  return (
    <>
      <div className="grilla-3">
        <Indicador titulo="Citas reservadas" valor={numero(d.reservas)} pie="en el período" />
        <Indicador titulo="En lista de espera" valor={numero(d.enEspera)} pie="personas esperando hoy (no depende del período)" />
        <Indicador titulo="Demanda total" valor={numero(d.reservas + d.enEspera)} pie="reservas del período + lista de espera de hoy" />
      </div>
      {/* Con un solo profesional (p. ej. al filtrar) la barra repetiría los indicadores. */}
      {filas.length > 1 && <section className="tarjeta tarjeta--padding pila">
        <h2>Demanda por profesional</h2>
        <div style={{ height: Math.max(220, filas.length * 42) }}>
          <ResponsiveContainer>
            <BarChart data={filas} layout="vertical" margin={{ left: 8, right: 48 }} barCategoryGap={10}>
              <XAxis type="number" hide />
              <YAxis type="category" dataKey="profesional" width={170} tickLine={false} axisLine={false} tick={{ fill: '#2b2f36', fontSize: 15 }} />
              <Tooltip {...TOOLTIP} />
              {/* Segmentos apilados separados por 2 px de superficie (borde blanco) */}
              <Bar dataKey="reservas" name="Citas reservadas" stackId="d" fill={VERDE_AZULADO} barSize={14} stroke="#fff" strokeWidth={2} radius={[4, 0, 0, 4]} {...ANIMACION} />
              <Bar dataKey="enEspera" name="En lista de espera" stackId="d" fill={NARANJO} radius={[0, 4, 4, 0]} barSize={14} stroke="#fff" strokeWidth={2} {...ANIMACION}>
                <LabelList dataKey="demanda" position="right" style={{ fill: '#2b2f36', fontSize: 14, fontWeight: 600 }} />
              </Bar>
            </BarChart>
          </ResponsiveContainer>
        </div>
        <ul className="leyenda leyenda--fila">
          <li><span className="leyenda__marca" style={{ background: VERDE_AZULADO }} />Citas reservadas con el profesional</li>
          <li><span className="leyenda__marca" style={{ background: NARANJO }} />Lista de espera de hoy de su profesión (compartida entre los profesionales de la misma profesión)</li>
        </ul>
      </section>}
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

/** Hasta 3 categorías con color propio; el resto se agrupa en «Otros» en gris neutro (nunca un color generado). */
function partesDe(datos: Cat[]): Parte[] {
  const orden = [...datos].sort((a, b) => b.cantidad - a.cantidad);
  const principales = orden.slice(0, 3).map((c, i) => ({ nombre: c.nombre, valor: c.cantidad, color: CATEGORICOS[i] }));
  const otros = orden.slice(3).reduce((a, c) => a + c.cantidad, 0);
  return otros > 0 ? [...principales, { nombre: 'Otros', valor: otros, color: NEUTRO }] : principales;
}

export function Caracterizacion({ d }: { d: { total: number; porEdad: Cat[]; porGenero: Cat[]; porNacionalidad: Cat[]; adultos: number; ninos: number; adultosMayores: number; porcentajeAdultosMayores: number } }) {
  const extranjera = d.porNacionalidad.find((x) => x.nombre === 'Otra')?.cantidad ?? 0;
  const pct = (v: number) => `${numero(v)} (${porcentaje(d.total ? (v * 100) / d.total : 0)})`;
  return (
    <>
      <div className="grilla-4">
        <Indicador titulo="Pacientes atendidos" valor={numero(d.total)} pie="con al menos una cita asistida" />
        <Indicador titulo="Adultos mayores" valor={porcentaje(d.porcentajeAdultosMayores)} pie={`${numero(d.adultosMayores)} de 60 años o más`} />
        <Indicador titulo="Adultos y niños" valor={`${numero(d.adultos)} / ${numero(d.ninos)}`} pie="18 años o más / menores de 18" />
        <Indicador titulo="Nacionalidad extranjera" valor={porcentaje(d.total ? (extranjera * 100) / d.total : 0)} pie={`${numero(extranjera)} pacientes`} />
      </div>
      <div className="grilla-3">
        <section className="tarjeta tarjeta--padding pila">
          <div className="fila fila--entre"><h2>Grupo etario</h2><span className="texto-secundario texto-pequeno">años</span></div>
          {/* Columnas verticales: los tramos de edad son ordinales y se leen de izquierda a derecha */}
          <div style={{ height: 220 }}>
            <ResponsiveContainer>
              <BarChart data={d.porEdad} margin={{ top: 24, right: 8, left: 8, bottom: 0 }} barCategoryGap="22%">
                <XAxis dataKey="nombre" tickLine={false} axisLine={{ stroke: '#dce3ea' }} tick={{ fill: '#5b6472', fontSize: 13 }} interval={0}
                  tickFormatter={(t: string) => t.replace(' años', '')} />
                <YAxis hide />
                <Tooltip {...TOOLTIP} formatter={(v: number) => pct(v)} />
                <Bar dataKey="cantidad" name="Pacientes" fill={VERDE_AZULADO} radius={[4, 4, 0, 0]} maxBarSize={44} {...ANIMACION}>
                  <LabelList dataKey="cantidad" position="top" formatter={(v: number) => numero(v)} style={{ fill: '#2b2f36', fontSize: 13, fontWeight: 600 }} />
                </Bar>
              </BarChart>
            </ResponsiveContainer>
          </div>
        </section>
        <Anillo titulo="Género" partes={partesDe(d.porGenero)} centro={numero(d.total)} subtitulo="pacientes" />
        <section className="tarjeta tarjeta--padding pila">
          <h2>Nacionalidad</h2>
          <ul className="barras-simples">
            {[...d.porNacionalidad].sort((a, b) => b.cantidad - a.cantidad).map((n) => {
              const p = d.total ? (n.cantidad * 100) / d.total : 0;
              return (
                <li key={n.nombre} title={pct(n.cantidad)}>
                  <div className="fila fila--entre"><span>{n.nombre}</span><span className="texto-fuerte">{porcentaje(p)}</span></div>
                  <div className="barra"><div className="barras-simples__relleno" style={{ width: `${p}%` }} /></div>
                  <span className="texto-secundario texto-pequeno">{numero(n.cantidad)} pacientes</span>
                </li>
              );
            })}
          </ul>
        </section>
      </div>
    </>
  );
}
