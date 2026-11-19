import { useEffect, useId, useRef, type ReactNode } from 'react';
import { Loader2, AlertCircle } from 'lucide-react';
import type { EstadoCita, EstadoConfirmacion } from './tipos';
import { ErrorDeApi } from './api';

/** Logo institucional. TODO (DECISIONES): reemplazar por frontend/public/logo.png descargado del Figma. */
export function Logo({ compacto = false }: { compacto?: boolean }) {
  return (
    <div className="fila" style={{ gap: 10, flexWrap: 'nowrap' }} aria-label="Instituto de Caridad Hermandad de Dolores, más de 200 años al servicio de Chile">
      <svg width={compacto ? 40 : 52} height={compacto ? 40 : 52} viewBox="0 0 52 52" aria-hidden="true">
        <circle cx="26" cy="26" r="24" fill="#fff" stroke="#5b6472" strokeWidth="2" />
        <circle cx="26" cy="26" r="17" fill="none" stroke="#5b6472" strokeWidth="1" strokeDasharray="2 2" />
        <path d="M26 13 L36 21 L33 36 L19 36 L16 21 Z" fill="none" stroke="#2b2f36" strokeWidth="1.6" />
        <path d="M26 19 v12 M21 25 h10" stroke="#00687a" strokeWidth="2.2" strokeLinecap="round" />
      </svg>
      <div style={{ lineHeight: 1.1 }}>
        <div style={{ color: '#1a8fa3', fontWeight: 800, fontSize: compacto ? 11 : 13, letterSpacing: '.01em' }}>INSTITUTO DE CARIDAD</div>
        <div style={{ color: '#1a8fa3', fontWeight: 800, fontSize: compacto ? 11 : 13, letterSpacing: '.01em' }}>HERMANDAD DE DOLORES</div>
        <div style={{ color: '#1a8fa3', fontWeight: 600, fontSize: compacto ? 8.5 : 10 }}>+200 Años al servicio de Chile</div>
      </div>
    </div>
  );
}

export function Cargando({ texto = 'Cargando…' }: { texto?: string }) {
  return (
    <div className="cargando" role="status">
      <Loader2 className="girar" size={20} aria-hidden="true" /> {texto}
    </div>
  );
}

export function MensajeError({ error }: { error: unknown }) {
  if (!error) return null;
  const mensaje = error instanceof ErrorDeApi ? error.mensaje : 'Ocurrió un problema. Inténtalo de nuevo.';
  return (
    <div className="aviso aviso--error" role="alert">
      <AlertCircle size={20} aria-hidden="true" style={{ flexShrink: 0, marginTop: 2 }} />
      <span>{mensaje}</span>
    </div>
  );
}

export function Iniciales({ texto, alerta = false }: { texto: string; alerta?: boolean }) {
  return <span className={'iniciales' + (alerta ? ' iniciales--alerta' : '')} aria-hidden="true">{texto}</span>;
}

/** Chips de estado (sección 12.1). */
export function ChipConfirmacion({ estado, sufijo }: { estado: EstadoConfirmacion; sufijo?: string }) {
  const mapa: Record<EstadoConfirmacion, [string, string]> = {
    CONFIRMADA: ['Confirmada', 'chip--exito'],
    CONFIRMADA_TELEFONO: ['Confirmada por teléfono', 'chip--exito'],
    NO_ENVIADA: ['No enviada', 'chip--alerta'],
    SIN_RESPUESTA: ['Sin respuesta', 'chip--gris'],
    ENVIADA: ['Enviada', 'chip--info'],
    SIN_ENVIAR: ['Por enviar', 'chip--gris'],
  };
  const [t, c] = mapa[estado];
  return <span className={'chip ' + c}>{t}{sufijo ? ' ' + sufijo : ''}</span>;
}

export function ChipCita({ estado, texto }: { estado: EstadoCita; texto?: string }) {
  const mapa: Record<EstadoCita, [string, string]> = {
    RESERVADA: ['Próxima', 'chip--info'],
    PENDIENTE_VERIFICACION: ['Pendiente de verificación', 'chip--alerta'],
    ASISTIDA: ['Asistió', 'chip--exito'],
    INASISTIDA: ['No asistió', 'chip--error'],
    ANULADA: ['Anulada', 'chip--gris'],
  };
  const [t, c] = mapa[estado];
  return <span className={'chip ' + c}>{texto ?? t}</span>;
}

interface ModalProps {
  titulo: string;
  children: ReactNode;
  alCerrar: () => void;
  ancho?: boolean;
  descripcion?: ReactNode;
}

export function Modal({ titulo, children, alCerrar, ancho, descripcion }: ModalProps) {
  const id = useId();
  const ref = useRef<HTMLDivElement>(null);
  useEffect(() => {
    const previo = document.activeElement as HTMLElement | null;
    const primero = ref.current?.querySelector<HTMLElement>('input, select, textarea, button');
    primero?.focus();
    const tecla = (e: KeyboardEvent) => e.key === 'Escape' && alCerrar();
    document.addEventListener('keydown', tecla);
    return () => {
      document.removeEventListener('keydown', tecla);
      previo?.focus?.();
    };
  }, [alCerrar]);
  return (
    <div className="velo" onMouseDown={(e) => e.target === e.currentTarget && alCerrar()}>
      <div className={'modal' + (ancho ? ' modal--ancho' : '')} role="dialog" aria-modal="true" aria-labelledby={id} ref={ref}>
        <h2 id={id} style={{ fontSize: '1.5rem', fontWeight: 600 }}>{titulo}</h2>
        {descripcion && <div className="texto-secundario">{descripcion}</div>}
        {children}
      </div>
    </div>
  );
}

interface CampoProps {
  etiqueta: string;
  error?: string;
  ayuda?: ReactNode;
  children: (props: { id: string; 'aria-invalid': boolean; 'aria-describedby'?: string }) => ReactNode;
}

/** Campo con etiqueta, ayuda y error junto al campo. */
export function Campo({ etiqueta, error, ayuda, children }: CampoProps) {
  const id = useId();
  const idError = id + '-error';
  const idAyuda = id + '-ayuda';
  const describe = [error ? idError : null, ayuda ? idAyuda : null].filter(Boolean).join(' ') || undefined;
  return (
    <div className="campo">
      <label htmlFor={id}>{etiqueta}</label>
      {children({ id, 'aria-invalid': !!error, 'aria-describedby': describe })}
      {ayuda && <span id={idAyuda} className="campo-ayuda">{ayuda}</span>}
      {error && <span id={idError} className="campo-error">{error}</span>}
    </div>
  );
}

export function Vacio({ children }: { children: ReactNode }) {
  return <div className="vacio">{children}</div>;
}
