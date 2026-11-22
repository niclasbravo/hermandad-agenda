import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { BarChart3 } from 'lucide-react';
import { api, consulta } from '../../compartido/api';
import { Cargando, MensajeError, Vacio } from '../../compartido/componentes';
import { fechaDiaMes, hoyIso, nombreMes } from '../../compartido/formato';
import { useSesion } from '../../compartido/sesion';
import { Cabecera } from '../DisenoPanel';
import { usePoliclinicos, useProfesionales } from '../consultas';
import { Asistencia, Caracterizacion, Demanda, Ocupacion } from './graficos';

type Tipo = 'ocupacion' | 'asistencia' | 'demanda' | 'caracterizacion' | 'inasistencia-reiterada';
const NOMBRES: Record<Tipo, string> = {
  ocupacion: 'Ocupación', asistencia: 'Asistencia', demanda: 'Demanda', caracterizacion: 'Caracterización', 'inasistencia-reiterada': 'Inasistencia reiterada',
};
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
        {reporte.isLoading && <Cargando forma="tarjetas" texto="Calculando el reporte…" />}
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
