import { createContext, useCallback, useContext, useEffect, useRef, useState, type ReactNode } from 'react';
import { CheckCircle2, Info, X } from 'lucide-react';

/**
 * Avisos flotantes breves que confirman una acción (reservar, anular, registrar asistencia…).
 * - Entran desde abajo y salen por el mismo lado (consistencia espacial), con transiciones interrumpibles.
 * - Se van solos a los 4,5 s; el temporizador se pausa con el puntero encima o con la pestaña oculta.
 * - Región aria-live «polite»: el lector de pantalla los anuncia sin interrumpir.
 * Complementan, no reemplazan, la confirmación en pantalla: nunca son el único lugar donde se informa un resultado.
 */
type Tipo = 'exito' | 'info';
interface Aviso { id: number; texto: string; detalle?: string; tipo: Tipo; saliendo: boolean }

const Contexto = createContext<(texto: string, opciones?: { detalle?: string; tipo?: Tipo }) => void>(() => {});
const DURACION = 4500;
const SALIDA_MS = 180;

export function ProveedorAvisos({ children }: { children: ReactNode }) {
  const [avisos, setAvisos] = useState<Aviso[]>([]);
  const siguiente = useRef(1);

  const quitar = useCallback((id: number) => {
    setAvisos((lista) => lista.map((a) => (a.id === id ? { ...a, saliendo: true } : a)));
    window.setTimeout(() => setAvisos((lista) => lista.filter((a) => a.id !== id)), SALIDA_MS);
  }, []);

  const avisar = useCallback((texto: string, opciones?: { detalle?: string; tipo?: Tipo }) => {
    const id = siguiente.current++;
    // Como máximo 3 a la vez: el más antiguo se va.
    setAvisos((lista) => [...lista.slice(-2), { id, texto, detalle: opciones?.detalle, tipo: opciones?.tipo ?? 'exito', saliendo: false }]);
  }, []);

  return (
    <Contexto.Provider value={avisar}>
      {children}
      <div className="avisos-flotantes" role="status" aria-live="polite">
        {avisos.map((a) => <AvisoFlotante key={a.id} aviso={a} alCerrar={() => quitar(a.id)} />)}
      </div>
    </Contexto.Provider>
  );
}

function AvisoFlotante({ aviso, alCerrar }: { aviso: Aviso; alCerrar: () => void }) {
  const [pausado, setPausado] = useState(false);
  const restante = useRef(DURACION);
  const inicio = useRef(Date.now());

  useEffect(() => {
    if (pausado || aviso.saliendo) return;
    const alOcultar = () => setPausado(document.hidden);
    document.addEventListener('visibilitychange', alOcultar);
    inicio.current = Date.now();
    const t = window.setTimeout(alCerrar, restante.current);
    return () => {
      window.clearTimeout(t);
      restante.current -= Date.now() - inicio.current;
      document.removeEventListener('visibilitychange', alOcultar);
    };
  }, [pausado, aviso.saliendo, alCerrar]);

  const Icono = aviso.tipo === 'exito' ? CheckCircle2 : Info;
  return (
    <div className={'aviso-flotante' + (aviso.saliendo ? ' aviso-flotante--saliendo' : '')}
      onMouseEnter={() => setPausado(true)} onMouseLeave={() => setPausado(false)}>
      <Icono size={20} aria-hidden="true" className={'aviso-flotante__icono aviso-flotante__icono--' + aviso.tipo} />
      <div className="aviso-flotante__texto">
        <strong>{aviso.texto}</strong>
        {aviso.detalle && <span>{aviso.detalle}</span>}
      </div>
      <button className="aviso-flotante__cerrar" onClick={alCerrar} aria-label="Cerrar aviso"><X size={16} aria-hidden="true" /></button>
    </div>
  );
}

export function useAviso() {
  return useContext(Contexto);
}
