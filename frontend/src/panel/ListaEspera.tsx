import { useState } from 'react';
import { useMutation, useQuery } from '@tanstack/react-query';
import { api, consulta } from '../compartido/api';
import { Cargando, MensajeError, Modal, Vacio } from '../compartido/componentes';
import { fechaHoraCorta } from '../compartido/formato';
import { useSesion } from '../compartido/sesion';
import type { Inscripcion } from '../compartido/tipos';
import { Cabecera } from './DisenoPanel';
import { useInvalidarAgenda, useProfesiones } from './consultas';

/** M-06 Lista de espera (222:4720): por orden de llegada (RN-06); la TENS/TONS adelanta con motivo (RF-42). */
export function ListaEspera() {
  const { usuario } = useSesion();
  const [profesion, setProfesion] = useState<number | ''>('');
  const [adelantar, setAdelantar] = useState<Inscripcion | null>(null);
  const [quitar, setQuitar] = useState<Inscripcion | null>(null);
  const profesiones = useProfesiones();
  const lista = useQuery({
    queryKey: ['lista-espera', profesion],
    queryFn: () => api.get<Inscripcion[]>('/api/lista-espera' + consulta({ profesion })),
  });
  return (
    <>
      <Cabecera titulo="Lista de espera" subtitulo={`Policlínico ${usuario?.policlinicoBase ?? ''} · por orden de llegada`} />
      <div className="cuerpo">
        <div className="campo" style={{ maxWidth: 280 }}>
          <label htmlFor="filtro-profesion">Atención</label>
          <select id="filtro-profesion" className="control" value={profesion} onChange={(e) => setProfesion(e.target.value ? Number(e.target.value) : '')}>
            <option value="">Todas</option>
            {profesiones.data?.map((p) => <option key={p.id} value={p.id}>{p.nombre}</option>)}
          </select>
        </div>
        {lista.isLoading && <Cargando />}
        <MensajeError error={lista.error} />
        {lista.data && (
          <div className="tarjeta tabla-contenedor">
            {lista.data.length === 0 ? <Vacio>No hay personas esperando.</Vacio> : (
              <table className="tabla">
                <thead><tr><th>N.º</th><th>Paciente</th><th>Atención solicitada</th><th>Inscrito el</th><th>Acciones</th></tr></thead>
                <tbody>
                  {lista.data.map((i) => (
                    <tr key={i.id}>
                      <td><span className="hora">{i.posicion}</span></td>
                      <td><span className="texto-fuerte">{i.paciente}</span><br /><span className="texto-secundario texto-pequeno">{i.documento} · {i.telefono}</span></td>
                      <td>{i.profesion}{i.adelantada && <><br /><span className="chip chip--info">Adelantada</span></>}</td>
                      <td>{fechaHoraCorta(i.fechaInscripcion)}</td>
                      <td>
                        <div className="fila" style={{ gap: 8 }}>
                          {i.posicion > 1 && <button className="boton boton--secundario boton--compacto" onClick={() => setAdelantar(i)}>Adelantar</button>}
                          <button className="boton boton--secundario boton--compacto" onClick={() => setQuitar(i)}>Quitar de la lista</button>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </div>
        )}
      </div>
      {adelantar && <ModalAdelantar inscripcion={adelantar} alCerrar={() => setAdelantar(null)} />}
      {quitar && <ModalQuitar inscripcion={quitar} alCerrar={() => setQuitar(null)} />}
    </>
  );
}

function ModalAdelantar({ inscripcion, alCerrar }: { inscripcion: Inscripcion; alCerrar: () => void }) {
  const [motivo, setMotivo] = useState('');
  const [posicion, setPosicion] = useState(1);
  const [error, setError] = useState<string | null>(null);
  const invalidar = useInvalidarAgenda();
  const m = useMutation({
    mutationFn: () => api.post(`/api/lista-espera/${inscripcion.id}/adelantar`, { motivo, posicion }),
    onSuccess: () => { invalidar(); alCerrar(); },
  });
  return (
    <Modal titulo={`Adelantar a ${inscripcion.paciente}`} alCerrar={alCerrar}
      descripcion={`Hoy está en el puesto ${inscripcion.posicion} para ${inscripcion.profesion}. Al adelantarla, queda registrado quién lo hizo y por qué.`}>
      <div className="campo">
        <label htmlFor="posicion">Nuevo puesto</label>
        <select id="posicion" className="control" value={posicion} onChange={(e) => setPosicion(Number(e.target.value))}>
          {Array.from({ length: inscripcion.posicion - 1 }, (_, k) => k + 1).map((n) => <option key={n} value={n}>{n === 1 ? '1 (primera)' : n}</option>)}
        </select>
      </div>
      <div className="campo">
        <label htmlFor="motivo-adelantar">Motivo *</label>
        <textarea id="motivo-adelantar" className="control" value={motivo} onChange={(e) => setMotivo(e.target.value)}
          aria-invalid={!!error} aria-describedby={error ? 'motivo-adelantar-error' : undefined} />
        {error && <span id="motivo-adelantar-error" className="campo-error">{error}</span>}
      </div>
      <MensajeError error={m.error} />
      <div className="modal__acciones">
        <button className="boton boton--secundario" onClick={alCerrar}>Cancelar</button>
        <button className="boton" disabled={m.isPending} onClick={() => {
          if (!motivo.trim()) { setError('Escribe el motivo para adelantar a esta persona.'); return; }
          m.mutate();
        }}>Adelantar</button>
      </div>
    </Modal>
  );
}

function ModalQuitar({ inscripcion, alCerrar }: { inscripcion: Inscripcion; alCerrar: () => void }) {
  const [motivo, setMotivo] = useState<'HORA_ASIGNADA' | 'DESISTE' | 'OTRO' | ''>('');
  const [detalle, setDetalle] = useState('');
  const invalidar = useInvalidarAgenda();
  const m = useMutation({
    mutationFn: () => api.post(`/api/lista-espera/${inscripcion.id}/retirar`, { motivo, detalle: detalle || null }),
    onSuccess: () => { invalidar(); alCerrar(); },
  });
  return (
    <Modal titulo={`Quitar a ${inscripcion.paciente} de la lista`} alCerrar={alCerrar}
      descripcion="Indica por qué sale de la lista. El registro se usa en el reporte de demanda.">
      <fieldset className="pila--chica" style={{ border: 0, padding: 0, margin: 0 }}>
        <legend className="sr-only">Motivo</legend>
        {([['HORA_ASIGNADA', 'Se le asignó una hora'], ['DESISTE', 'Ya no necesita la atención'], ['OTRO', 'No fue posible contactarla']] as const).map(([v, t]) => (
          <label key={v} className="opcion-radio"><input type="radio" name="motivo-retiro" checked={motivo === v} onChange={() => setMotivo(v)} /> {t}</label>
        ))}
      </fieldset>
      {motivo === 'OTRO' && (
        <div className="campo">
          <label htmlFor="detalle-retiro">Detalle *</label>
          <input id="detalle-retiro" className="control" value={detalle} onChange={(e) => setDetalle(e.target.value)} placeholder="Ej.: se llamó 3 veces sin respuesta" />
        </div>
      )}
      <MensajeError error={m.error} />
      <div className="modal__acciones">
        <button className="boton boton--secundario" onClick={alCerrar}>Cancelar</button>
        <button className="boton" disabled={!motivo || (motivo === 'OTRO' && !detalle.trim()) || m.isPending} onClick={() => m.mutate()}>Quitar de la lista</button>
      </div>
    </Modal>
  );
}
