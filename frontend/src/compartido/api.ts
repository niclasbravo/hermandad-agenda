/** Cliente de la API. La sesión va en una cookie HttpOnly; el token CSRF se lee de la cookie XSRF-TOKEN. */

export interface ErrorApi {
  estado: number;
  codigo: string;
  mensaje: string;
  detalles: Record<string, unknown>;
}

export class ErrorDeApi extends Error implements ErrorApi {
  estado: number;
  codigo: string;
  detalles: Record<string, unknown>;
  mensaje: string;

  constructor(e: ErrorApi) {
    super(e.mensaje);
    this.estado = e.estado;
    this.codigo = e.codigo;
    this.mensaje = e.mensaje;
    this.detalles = e.detalles ?? {};
  }

  /** Errores por campo (400). */
  get campos(): Record<string, string> {
    return (this.detalles?.campos as Record<string, string>) ?? {};
  }
}

function leerCookie(nombre: string): string | null {
  const m = document.cookie.match(new RegExp('(?:^|; )' + nombre + '=([^;]*)'));
  return m ? decodeURIComponent(m[1]) : null;
}

let csrfListo: Promise<void> | null = null;

async function asegurarCsrf() {
  if (leerCookie('XSRF-TOKEN')) return;
  if (!csrfListo) {
    csrfListo = fetch('/api/auth/csrf', { credentials: 'same-origin' }).then(() => undefined).finally(() => {
      csrfListo = null;
    });
  }
  await csrfListo;
}

/** Se llama cuando una consulta interna recibe 401 (la interfaz redirige al ingreso). */
let alPerderSesion: (() => void) | null = null;
export function siSePierdeLaSesion(fn: () => void) {
  alPerderSesion = fn;
}

export async function solicitar<T>(metodo: string, ruta: string, cuerpo?: unknown, cabeceras: Record<string, string> = {}): Promise<T> {
  const escritura = metodo !== 'GET';
  if (escritura) await asegurarCsrf();
  const esFormulario = cuerpo instanceof FormData;
  const headers: Record<string, string> = { Accept: 'application/json', ...cabeceras };
  if (cuerpo !== undefined && !esFormulario) headers['Content-Type'] = 'application/json';
  const token = leerCookie('XSRF-TOKEN');
  if (escritura && token) headers['X-XSRF-TOKEN'] = token;
  let respuesta: Response;
  try {
    respuesta = await fetch(ruta, {
      method: metodo,
      credentials: 'same-origin',
      headers,
      body: cuerpo === undefined ? undefined : esFormulario ? (cuerpo as FormData) : JSON.stringify(cuerpo),
    });
  } catch {
    throw new ErrorDeApi({ estado: 0, codigo: 'SIN_CONEXION', mensaje: 'No pudimos conectarnos. Revisa tu conexión a internet e inténtalo de nuevo.', detalles: {} });
  }
  if (respuesta.status === 204) return undefined as T;
  const texto = await respuesta.text();
  let datos: unknown = null;
  try {
    datos = texto ? JSON.parse(texto) : null;
  } catch {
    datos = null;
  }
  if (!respuesta.ok) {
    const d = (datos ?? {}) as Partial<ErrorApi>;
    const error = new ErrorDeApi({
      estado: respuesta.status,
      codigo: d.codigo ?? 'ERROR',
      mensaje: d.mensaje ?? 'Ocurrió un problema. Inténtalo de nuevo en un momento.',
      detalles: (d.detalles as Record<string, unknown>) ?? {},
    });
    if (respuesta.status === 401 && ruta.startsWith('/api/') && !ruta.startsWith('/api/publico') && !ruta.startsWith('/api/auth/ingresar')) {
      alPerderSesion?.();
    }
    throw error;
  }
  return datos as T;
}

export const api = {
  get: <T>(ruta: string) => solicitar<T>('GET', ruta),
  post: <T>(ruta: string, cuerpo?: unknown, cabeceras?: Record<string, string>) => solicitar<T>('POST', ruta, cuerpo ?? {}, cabeceras),
  put: <T>(ruta: string, cuerpo?: unknown) => solicitar<T>('PUT', ruta, cuerpo ?? {}),
};

/** Arma una query string ignorando los valores vacíos. */
export function consulta(params: Record<string, string | number | null | undefined>) {
  const q = new URLSearchParams();
  Object.entries(params).forEach(([k, v]) => {
    if (v !== undefined && v !== null && v !== '') q.set(k, String(v));
  });
  const s = q.toString();
  return s ? '?' + s : '';
}
