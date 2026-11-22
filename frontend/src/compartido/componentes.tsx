import { useEffect, useId, useRef, type ReactNode } from 'react';
import { Loader2, AlertCircle } from 'lucide-react';
import type { EstadoCita, EstadoConfirmacion } from './tipos';
import { ErrorDeApi } from './api';

const ANCHO_LOGO = { compacto: 196, normal: 250, grande: 360 } as const;

/** Logo oficial de la Hermandad de Dolores (frontend/public/logo.png, PNG con fondo transparente; DI-36). */
export function Logo({ compacto = false, tamano }: { compacto?: boolean; tamano?: keyof typeof ANCHO_LOGO }) {
  const ancho = ANCHO_LOGO[tamano ?? (compacto ? 'compacto' : 'normal')];
  return (
    <img src="/logo.png" width={ancho} height={Math.round(ancho * 162 / 600)} className="logo"
      alt="Instituto de Caridad Hermandad de Dolores, más de 200 años al servicio de Chile" />
  );
}

/** Marca de campo obligatorio. El asterisco es visual; el control lleva aria-required para los lectores de pantalla. */
export function Obligatorio() {
  return <span className="obligatorio" aria-hidden="true">*</span>;
}

/** Texto de etiqueta que termina en « *» (convención de los formularios) → texto + marca de obligatorio. */
export function Etiqueta({ texto }: { texto: string }) {
  return texto.endsWith(' *') ? <>{texto.slice(0, -2)} <Obligatorio /></> : <>{texto}</>;
}

/**
 * Indicador de carga. Con `forma`, muestra siluetas con la forma del contenido que viene (tabla o tarjetas):
 * la pantalla no salta al llegar los datos y la espera se percibe más corta. Sin `forma`, el texto con ícono de siempre.
 */
export function Cargando({ texto = 'Cargando…', forma }: { texto?: string; forma?: 'tabla' | 'tarjetas' }) {
  if (forma) {
    return (
      <div className="siluetas" role="status" aria-label={texto}>
        {forma === 'tarjetas' && (
          <div className="grilla-4">{[0, 1, 2, 3].map((i) => <div key={i} className="silueta silueta--tarjeta" />)}</div>
        )}
        <div className="tarjeta siluetas__tabla">
          {[0, 1, 2, 3, 4].map((i) => (
            <div key={i} className="siluetas__fila">
              <div className="silueta silueta--corta" /><div className="silueta silueta--circulo" />
              <div className="silueta silueta--larga" /><div className="silueta silueta--media" />
            </div>
          ))}
        </div>
      </div>
    );
  }
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
