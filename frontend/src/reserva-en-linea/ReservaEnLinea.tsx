import { useMemo, useRef, useState, type FormEvent } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { CalendarCheck, Check, FileText, Info, MessageCircle, Upload } from 'lucide-react';
import { api, consulta, ErrorDeApi } from '../compartido/api';
import { Cargando, Etiqueta, MensajeError, Obligatorio } from '../compartido/componentes';
import { edad, fechaLarga, hora, hoyIso } from '../compartido/formato';
import { formatearRut, rutValido } from '../compartido/rut';
import { GENEROS, RELACIONES } from '../panel/pacientes/esquemaPaciente';
import { CabeceraPublica } from './CabeceraPublica';
import type { PoliclinicoPublico } from './Portada';

interface Identificacion { token: string; estado: 'IDENTIFICADO' | 'NUEVO'; documentoEnmascarado: string; nombrePila: string | null }
interface CupoPublico { id: number; fecha: string; hora: string; duracion: number; idProfesional: number; profesional: string; profesion: string; idPoliclinico: number; policlinico: string; direccion: string; box: string }
interface Comprobante { idCita: number; estado: string; nombrePila: string; policlinico: string; direccion: string; profesional: string; profesion: string; box: string; fecha: string; hora: string; fechaTexto: string; duracion: number; pendienteVerificacion: boolean; mensaje: string }
type Paso = 'identificacion' | 'datos' | 'elegir' | 'certificados' | 'listo';

const CABECERA = 'X-Token-Reserva';

/** M-08 Reserva en línea (CU-08): 1 Identificación, 2 Elegir hora, 3 Confirmación. Sin cuenta y sin mostrar la ficha (RES-06). */
export function ReservaEnLinea() {
  const [params] = useSearchParams();
  const [paso, setPaso] = useState<Paso>('identificacion');
  const [ident, setIdent] = useState<Identificacion | null>(null);
  const [nacimiento, setNacimiento] = useState('');
  const [comprobante, setComprobante] = useState<Comprobante | null>(null);
  const [certificadosRecibidos, setCertificadosRecibidos] = useState(false);
  const reiniciar = () => { setPaso('identificacion'); setIdent(null); setComprobante(null); };
  const numeroPaso = paso === 'identificacion' ? 1 : paso === 'datos' || paso === 'elegir' ? 2 : 3;

  return (
    <div className="publico">
      <CabeceraPublica modoReserva />
      <main className="reserva">
        <div className="reserva__interior">
          <ol className="pasos" aria-label="Pasos de la reserva" style={{ listStyle: 'none', padding: 0, margin: 0 }}>
            {['Identificación', 'Elegir hora', 'Confirmación'].map((t, i) => (
              <li key={t} className="fila" style={{ gap: 14 }}>
                {i > 0 && <span className="pasos__linea" aria-hidden="true" />}
                <span className={'pasos__item' + (numeroPaso === i + 1 ? ' pasos__item--activo' : numeroPaso > i + 1 ? ' pasos__item--hecho' : '')} aria-current={numeroPaso === i + 1 ? 'step' : undefined}>
                  <span className="pasos__numero">{numeroPaso > i + 1 ? <Check size={16} /> : i + 1}</span> {t}
                </span>
              </li>
            ))}
          </ol>
          {ident?.estado === 'IDENTIFICADO' && paso !== 'identificacion' && (
            <div className="fila" style={{ gap: 8 }}><span className="chip chip--exito">Paciente identificado</span><span className="texto-secundario texto-pequeno">RUT {ident.documentoEnmascarado}</span></div>
          )}
          {paso === 'identificacion' && <PasoIdentificacion alListo={(i, n) => { setIdent(i); setNacimiento(n); setPaso(i.estado === 'IDENTIFICADO' ? 'elegir' : 'datos'); }} />}
          {paso === 'datos' && ident && <PasoDatos token={ident.token} nacimiento={nacimiento} alVolver={reiniciar}
            alListo={(i) => { setIdent(i); setPaso('elegir'); }} alVencer={reiniciar} />}
          {paso === 'elegir' && ident && <PasoElegir token={ident.token} policlinicoInicial={params.get('policlinico')} alVencer={reiniciar}
            alReservar={(c) => { setComprobante(c); setPaso(c.pendienteVerificacion ? 'certificados' : 'listo'); }} />}
          {paso === 'certificados' && ident && comprobante && <PasoCertificados token={ident.token} comprobante={comprobante}
            alListo={(subidos) => { setCertificadosRecibidos(subidos); setPaso('listo'); }} />}
          {paso === 'listo' && comprobante && <PasoListo c={comprobante} certificados={certificadosRecibidos} />}
        </div>
      </main>
    </div>
  );
}

function PasoIdentificacion({ alListo }: { alListo: (i: Identificacion, nacimiento: string) => void }) {
  const [tipo, setTipo] = useState<'RUT' | 'PASAPORTE' | 'OTRO'>('RUT');
  const [numero, setNumero] = useState('');
  const [fecha, setFecha] = useState('');
  const [errores, setErrores] = useState<{ numero?: string; fecha?: string }>({});
  const [error, setError] = useState<unknown>(null);
  const [enviando, setEnviando] = useState(false);
  const enviar = async (e: FormEvent) => {
    e.preventDefault();
    const er: typeof errores = {};
    if (!numero.trim()) er.numero = tipo === 'RUT' ? 'Escribe el RUT.' : 'Escribe el número de documento.';
    else if (tipo === 'RUT' && !rutValido(numero)) er.numero = 'El RUT no es válido. Revisa el número y el dígito verificador.';
    if (!fecha) er.fecha = 'Escribe la fecha de nacimiento.';
    else if (fecha > hoyIso()) er.fecha = 'La fecha de nacimiento no puede ser futura.';
    setErrores(er);
    if (er.numero || er.fecha) return;
    setEnviando(true);
    setError(null);
    try {
      alListo(await api.post<Identificacion>('/api/publico/identificacion', { tipoDocumento: tipo, numero, fechaNacimiento: fecha }), fecha);
    } catch (err) {
      if (err instanceof ErrorDeApi && err.campos.numeroDocumento) setErrores({ numero: err.campos.numeroDocumento });
      else setError(err);
    } finally {
      setEnviando(false);
    }
  };
  return (
    <div style={{ display: 'grid', gridTemplateColumns: 'minmax(0, 1.4fr) minmax(260px, 1fr)', gap: 20, alignItems: 'start' }} className="reserva-grilla">
      <form className="tarjeta tarjeta--padding pila" onSubmit={enviar} noValidate>
        <h1>Reserva tu hora</h1>
        <p className="texto-secundario">Ingresa el RUT y la fecha de nacimiento de la persona que se va a atender. Si reservas para un familiar, usa sus datos.</p>
        <div className="grilla-2">
          <div className="campo"><label htmlFor="id-tipo">Tipo de documento <Obligatorio /></label>
            <select id="id-tipo" className="control" value={tipo} onChange={(e) => setTipo(e.target.value as typeof tipo)}>
              <option value="RUT">RUT</option><option value="PASAPORTE">Pasaporte</option><option value="OTRO">Otro documento</option>
            </select></div>
          <div className="campo"><label htmlFor="id-num">{tipo === 'RUT' ? 'RUT' : 'Número de documento'} <Obligatorio /></label>
            <input id="id-num" className="control" value={numero} placeholder={tipo === 'RUT' ? '12.345.678-5' : ''}
              onChange={(e) => setNumero(e.target.value)} onBlur={() => tipo === 'RUT' && rutValido(numero) && setNumero(formatearRut(numero))}
              aria-invalid={!!errores.numero} aria-describedby={errores.numero ? 'id-num-error' : undefined} autoComplete="off" />
            {errores.numero && <span id="id-num-error" className="campo-error">{errores.numero}</span>}</div>
        </div>
        <div className="campo" style={{ maxWidth: 280 }}><label htmlFor="id-fecha">Fecha de nacimiento <Obligatorio /></label>
          <input id="id-fecha" type="date" className="control" value={fecha} max={hoyIso()} onChange={(e) => setFecha(e.target.value)}
            aria-invalid={!!errores.fecha} aria-describedby={errores.fecha ? 'id-fecha-error' : undefined} />
          {errores.fecha && <span id="id-fecha-error" className="campo-error">{errores.fecha}</span>}</div>
        <p className="texto-secundario texto-pequeno fila" style={{ gap: 8, flexWrap: 'nowrap', alignItems: 'flex-start' }}><Info size={18} aria-hidden="true" style={{ flexShrink: 0 }} /> Usamos estos datos solo para gestionar tu hora. Por seguridad, no mostramos información de tu ficha.</p>
        <MensajeError error={error} />
        <button className="boton" type="submit" disabled={enviando} style={{ alignSelf: 'flex-start' }}>Continuar</button>
      </form>
      <aside className="tarjeta tarjeta--padding pila--chica">
        <h2>¿Prefieres pedir tu hora en persona o por teléfono?</h2>
        <p className="texto-secundario">Acércate a tu policlínico o escríbenos por WhatsApp. Una TENS/TONS te ayudará con tu reserva.</p>
        <p className="texto-secundario">Si es tu primera atención, deberás presentar tu certificado de FONASA y tu inscripción en el CESFAM.</p>
      </aside>
    </div>
  );
}

function PasoDatos({ token, nacimiento, alVolver, alListo, alVencer }: { token: string; nacimiento: string; alVolver: () => void; alListo: (i: Identificacion) => void; alVencer: () => void }) {
  const menor = edad(nacimiento) < 18;
  const [d, setD] = useState({ nombres: '', apellidos: '', telefonoCelular: '', correo: '', direccion: '', genero: '', nacionalidad: 'Chilena' });
  const [a, setA] = useState({ tipoDocumento: 'RUT', numeroDocumento: '', nombreCompleto: '', telefono: '', relacion: '' });
  const [errores, setErrores] = useState<Record<string, string>>({});
  const [error, setError] = useState<unknown>(null);
  const [enviando, setEnviando] = useState(false);
  const enviar = async (e: FormEvent) => {
    e.preventDefault();
    const er: Record<string, string> = {};
    if (!d.nombres.trim()) er.nombres = 'Escribe los nombres.';
    if (!d.apellidos.trim()) er.apellidos = 'Escribe los apellidos.';
    if (d.telefonoCelular.replace(/\D/g, '').length < 8) er.telefonoCelular = 'Escribe un celular válido, por ejemplo +56 9 1234 5678.';
    if (!d.direccion.trim()) er.direccion = 'Escribe la dirección.';
    if (!d.genero) er.genero = 'Elige una opción.';
    if (!d.nacionalidad.trim()) er.nacionalidad = 'Escribe la nacionalidad.';
    if (d.correo && !/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(d.correo)) er.correo = 'Escribe un correo válido o déjalo vacío.';
    if (menor) {
      if (!a.nombreCompleto.trim()) er['adulto.nombreCompleto'] = 'Escribe el nombre del adulto responsable.';
      if (!a.numeroDocumento.trim()) er['adulto.numeroDocumento'] = 'Escribe el RUT del adulto responsable.';
      else if (a.tipoDocumento === 'RUT' && !rutValido(a.numeroDocumento)) er['adulto.numeroDocumento'] = 'El RUT no es válido.';
      if (a.telefono.replace(/\D/g, '').length < 8) er['adulto.telefono'] = 'Escribe un celular válido.';
      if (!a.relacion) er['adulto.relacion'] = 'Indica la relación.';
    }
    setErrores(er);
    if (Object.keys(er).length) return;
    setEnviando(true);
    setError(null);
    try {
      alListo(await api.post<Identificacion>('/api/publico/pacientes', { ...d, correo: d.correo || null, adulto: menor ? a : null }, { [CABECERA]: token }));
    } catch (err) {
      if (err instanceof ErrorDeApi && err.codigo === 'TOKEN_VENCIDO') alVencer();
      else if (err instanceof ErrorDeApi && Object.keys(err.campos).length) setErrores(err.campos);
      else setError(err);
    } finally {
      setEnviando(false);
    }
  };
  const campo = (k: keyof typeof d, etiqueta: string, props: Record<string, unknown> = {}) => (
    <div className="campo"><label htmlFor={'nd-' + k}><Etiqueta texto={etiqueta} /></label>
      <input id={'nd-' + k} className="control" value={d[k]} onChange={(e) => setD({ ...d, [k]: e.target.value })} aria-invalid={!!errores[k]} {...props} />
      {errores[k] && <span className="campo-error">{errores[k]}</span>}</div>
  );
  const campoA = (k: 'numeroDocumento' | 'nombreCompleto' | 'telefono', etiqueta: string, props: Record<string, unknown> = {}) => (
    <div className="campo"><label htmlFor={'na-' + k}><Etiqueta texto={etiqueta} /></label>
      <input id={'na-' + k} className="control" value={a[k]} onChange={(e) => setA({ ...a, [k]: e.target.value })} aria-invalid={!!errores['adulto.' + k]} {...props} />
      {errores['adulto.' + k] && <span className="campo-error">{errores['adulto.' + k]}</span>}</div>
  );
  return (
    <form className="tarjeta tarjeta--padding pila" onSubmit={enviar} noValidate>
      <div className="aviso aviso--info pila--chica" style={{ flexDirection: 'column', gap: 2 }}>
        <strong>No encontramos una ficha con estos datos</strong>
        <span>Completa los datos una sola vez. Quedarán guardados para tus próximas horas.</span>
      </div>
      <h2>Datos del paciente</h2>
      <div className="grilla-2">{campo('nombres', 'Nombres *', { autoComplete: 'given-name' })}{campo('apellidos', 'Apellidos *', { autoComplete: 'family-name' })}</div>
      <div className="grilla-2">{campo('telefonoCelular', 'Teléfono celular *', { inputMode: 'tel', placeholder: '+56 9 1234 5678', autoComplete: 'tel' })}{campo('correo', 'Correo (opcional)', { type: 'email', autoComplete: 'email' })}</div>
      <div className="grilla-3">
        {campo('direccion', 'Dirección *', { autoComplete: 'street-address' })}
        <div className="campo"><label htmlFor="nd-genero">Género <Obligatorio /></label>
          <select id="nd-genero" className="control" value={d.genero} onChange={(e) => setD({ ...d, genero: e.target.value })} aria-invalid={!!errores.genero}>
            <option value="">Elige una opción</option>{GENEROS.map((g) => <option key={g}>{g}</option>)}
          </select>{errores.genero && <span className="campo-error">{errores.genero}</span>}</div>
        {campo('nacionalidad', 'Nacionalidad *')}
      </div>
      <div className="fila fila--entre"><span>¿La persona es menor de edad? Si lo es, te pediremos los datos del adulto responsable.</span><span className={'chip ' + (menor ? 'chip--alerta' : 'chip--gris')}>{menor ? 'Sí' : 'No'}</span></div>
      {menor && (
        <section className="pila" aria-label="Datos del adulto responsable">
          <h2>Datos del adulto responsable</h2>
          <div className="grilla-2">{campoA('nombreCompleto', 'Nombre completo *')}{campoA('numeroDocumento', 'RUT *', { placeholder: '12.345.678-5' })}</div>
          <div className="grilla-2">
            {campoA('telefono', 'Teléfono celular *', { inputMode: 'tel', placeholder: '+56 9 1234 5678' })}
            <div className="campo"><label htmlFor="na-relacion">Relación con el paciente <Obligatorio /></label>
              <select id="na-relacion" className="control" value={a.relacion} onChange={(e) => setA({ ...a, relacion: e.target.value })} aria-invalid={!!errores['adulto.relacion']}>
                <option value="">Elige una opción</option>{RELACIONES.map((r) => <option key={r} value={r}>{r.charAt(0).toUpperCase() + r.slice(1)}</option>)}
              </select>{errores['adulto.relacion'] && <span className="campo-error">{errores['adulto.relacion']}</span>}</div>
          </div>
          <p className="texto-secundario texto-pequeno">Enviaremos la confirmación y el recordatorio de la hora por WhatsApp al celular del adulto responsable.</p>
        </section>
      )}
      <p className="fila" style={{ gap: 8, flexWrap: 'nowrap' }}><MessageCircle size={20} aria-hidden="true" style={{ color: 'var(--color-tinta)', flexShrink: 0 }} /> Te enviaremos la confirmación y el recordatorio de tu hora por WhatsApp a este celular.</p>
      <MensajeError error={error} />
      <div className="fila"><button type="button" className="boton boton--secundario" onClick={alVolver}>Volver</button><button className="boton" type="submit" disabled={enviando}>Continuar</button></div>
    </form>
  );
}

interface ProfesionPublica { id: number; nombre: string }
interface ProfesionalPublico { id: number; nombre: string; idProfesion: number; idsPoliclinico: number[] }

function PasoElegir({ token, policlinicoInicial, alReservar, alVencer }: { token: string; policlinicoInicial: string | null; alReservar: (c: Comprobante) => void; alVencer: () => void }) {
  const policlinicos = useQuery({ queryKey: ['publico', 'policlinicos'], queryFn: () => api.get<PoliclinicoPublico[]>('/api/publico/policlinicos') });
  const profesiones = useQuery({ queryKey: ['publico', 'profesiones'], queryFn: () => api.get<ProfesionPublica[]>('/api/publico/profesiones') });
  const profesionales = useQuery({ queryKey: ['publico', 'profesionales'], queryFn: () => api.get<ProfesionalPublico[]>('/api/publico/profesionales') });
  const [policlinico, setPoliclinico] = useState<number | ''>(policlinicoInicial ? Number(policlinicoInicial) : '');
  const [profesion, setProfesion] = useState<number | ''>('');
  const [profesional, setProfesional] = useState<number | ''>('');
  const [elegido, setElegido] = useState<CupoPublico | null>(null);
  const [error, setError] = useState<unknown>(null);
  const [enviando, setEnviando] = useState(false);
  const pol = policlinico || policlinicos.data?.[0]?.id || '';
  const cupos = useQuery({
    queryKey: ['publico', 'cupos', pol, profesion, profesional],
    queryFn: () => api.get<CupoPublico[]>('/api/publico/cupos' + consulta({ policlinico: pol, profesion, profesional })),
    enabled: !!pol && !!profesion,
  });
  const porDia = useMemo(() => {
    const m = new Map<string, CupoPublico[]>();
    (cupos.data ?? []).forEach((c) => m.set(c.fecha, [...(m.get(c.fecha) ?? []), c]));
    return [...m.entries()].slice(0, 6);
  }, [cupos.data]);
  const reservar = async () => {
    if (!elegido) return;
    setEnviando(true);
    setError(null);
    try {
      alReservar(await api.post<Comprobante>('/api/publico/reservas', { idCupo: elegido.id }, { [CABECERA]: token }));
    } catch (err) {
      if (err instanceof ErrorDeApi && err.codigo === 'TOKEN_VENCIDO') { alVencer(); return; }
      if (err instanceof ErrorDeApi && err.estado === 409) { setElegido(null); cupos.refetch(); }
      setError(err);
    } finally {
      setEnviando(false);
    }
  };
  const profesionalesFiltrados = (profesionales.data ?? []).filter((p) => (!profesion || p.idProfesion === profesion) && (!pol || p.idsPoliclinico.includes(Number(pol))));
  return (
    <div style={{ display: 'grid', gridTemplateColumns: 'minmax(0, 1.6fr) minmax(260px, 1fr)', gap: 20, alignItems: 'start' }} className="reserva-grilla">
      <section className="tarjeta tarjeta--padding pila">
        <h1>Elige tu hora</h1>
        <div className="grilla-3">
          <div className="campo"><label htmlFor="e-pol">Policlínico <Obligatorio /></label>
            <select id="e-pol" className="control" value={pol} onChange={(e) => { setPoliclinico(Number(e.target.value)); setElegido(null); setProfesional(''); }}>
              {policlinicos.data?.map((p) => <option key={p.id} value={p.id}>{p.nombre}</option>)}
            </select></div>
          <div className="campo"><label htmlFor="e-prof">Profesión <Obligatorio /></label>
            <select id="e-prof" className="control" value={profesion} onChange={(e) => { setProfesion(e.target.value ? Number(e.target.value) : ''); setElegido(null); setProfesional(''); }}>
              <option value="">Elige una atención</option>
              {profesiones.data?.map((p) => <option key={p.id} value={p.id}>{p.nombre}</option>)}
            </select></div>
          <div className="campo"><label htmlFor="e-pro">Profesional (opcional)</label>
            <select id="e-pro" className="control" value={profesional} onChange={(e) => { setProfesional(e.target.value ? Number(e.target.value) : ''); setElegido(null); }}>
              <option value="">Cualquiera</option>
              {profesionalesFiltrados.map((p) => <option key={p.id} value={p.id}>{p.nombre}</option>)}
            </select></div>
        </div>
        {!profesion && <p className="texto-secundario">Elige la atención que necesitas para ver las horas disponibles.</p>}
        {cupos.isLoading && <Cargando />}
        {cupos.data?.length === 0 && (
          <div className="aviso aviso--alerta">No hay horas disponibles en línea para esta atención en los próximos días. Pide tu hora en tu policlínico o por teléfono.</div>
        )}
        <div className="columnas" style={{ gridAutoColumns: 'minmax(160px, 1fr)' }}>
          {porDia.map(([fecha, lista]) => (
            <section key={fecha} aria-label={fechaLarga(fecha)}>
              <h3 style={{ color: 'var(--color-tinta)', marginBottom: 8 }}>{fechaLarga(fecha)}</h3>
              {lista.map((c) => (
                <button key={c.id} className={'cupo' + (elegido?.id === c.id ? ' cupo--elegido' : '')} aria-pressed={elegido?.id === c.id} onClick={() => setElegido(c)}>
                  <span className="hora">{hora(c.hora)}</span><br /><span className="texto-secundario texto-pequeno">{c.profesional}</span>
                </button>
              ))}
            </section>
          ))}
        </div>
      </section>
      <aside className="tarjeta tarjeta--padding pila--chica" aria-live="polite">
        <h2>Tu hora</h2>
        {!elegido ? <p className="texto-secundario">Elige un horario para ver el detalle.</p> : (
          <>
            <div className="texto-fuerte">{fechaLarga(elegido.fecha)}, {hora(elegido.hora)}</div>
            <div>{elegido.profesion} con {elegido.profesional}</div>
            <div>Policlínico {elegido.policlinico}, {elegido.box}</div>
            <div className="texto-secundario texto-pequeno">{elegido.direccion}</div>
            <MensajeError error={error} />
            <button className="boton" disabled={enviando} onClick={reservar}>Reservar esta hora</button>
            <p className="texto-secundario texto-pequeno">Recibirás la confirmación en el celular registrado.</p>
          </>
        )}
        {!elegido && <MensajeError error={error} />}
      </aside>
    </div>
  );
}

interface Archivo { archivo: File; tipo: 'CERTIFICADO_FONASA' | 'CERTIFICADO_CESFAM' }

function PasoCertificados({ token, comprobante, alListo }: { token: string; comprobante: Comprobante; alListo: (subidos: boolean) => void }) {
  const [modo, setModo] = useState<'subir' | 'llevar' | ''>('');
  const [archivos, setArchivos] = useState<Archivo[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [enviando, setEnviando] = useState(false);
  const entrada = useRef<HTMLInputElement>(null);
  const [tipoNuevo, setTipoNuevo] = useState<Archivo['tipo']>('CERTIFICADO_FONASA');
  const agregar = (f: File | undefined) => {
    if (!f) return;
    if (!['application/pdf', 'image/jpeg', 'image/png'].includes(f.type)) { setError('Solo se aceptan fotos JPG o PNG y documentos PDF.'); return; }
    if (f.size > 5 * 1024 * 1024) { setError('El archivo pesa más de 5 MB. Sube uno más liviano.'); return; }
    setError(null);
    setArchivos((x) => [...x, { archivo: f, tipo: tipoNuevo }]);
    setTipoNuevo(tipoNuevo === 'CERTIFICADO_FONASA' ? 'CERTIFICADO_CESFAM' : 'CERTIFICADO_FONASA');
  };
  const enviar = async () => {
    if (modo === 'llevar') { alListo(false); return; }
    setEnviando(true);
    setError(null);
    try {
      for (const a of archivos) {
        const fd = new FormData();
        fd.append('tipo', a.tipo);
        fd.append('archivo', a.archivo);
        await api.post(`/api/publico/reservas/${comprobante.idCita}/certificados`, fd, { [CABECERA]: token });
      }
      alListo(true);
    } catch (e) {
      setError(e instanceof ErrorDeApi ? e.mensaje : 'No pudimos subir los archivos.');
    } finally {
      setEnviando(false);
    }
  };
  return (
    <section className="tarjeta tarjeta--padding pila">
      <div className="aviso aviso--exito"><Check size={20} aria-hidden="true" /> Reservamos tu hora del {comprobante.fechaTexto.toLowerCase()} a las {hora(comprobante.hora)}.</div>
      <h1>¿Cómo quieres entregar tus certificados?</h1>
      <p className="texto-secundario">Es tu primera atención: necesitamos tu certificado de FONASA y tu inscripción en el CESFAM.</p>
      <label className="opcion-radio"><input type="radio" name="modo" checked={modo === 'subir'} onChange={() => setModo('subir')} />
        <span><span className="texto-fuerte">Subirlos ahora</span><br /><span className="texto-secundario texto-pequeno">Una foto o un PDF de cada certificado. El de FONASA lo puedes descargar en su página.</span></span></label>
      <label className="opcion-radio"><input type="radio" name="modo" checked={modo === 'llevar'} onChange={() => setModo('llevar')} />
        <span><span className="texto-fuerte">Los llevaré a mi primera atención</span><br /><span className="texto-secundario texto-pequeno">Muéstralos en el policlínico antes de atenderte.</span></span></label>
      {modo === 'subir' && (
        <div className="pila--chica">
          {archivos.map((a, i) => (
            <div key={i} className="tarjeta fila fila--entre" style={{ padding: '10px 14px', boxShadow: 'none' }}>
              <span className="fila" style={{ gap: 10, flexWrap: 'nowrap' }}><FileText size={22} aria-hidden="true" style={{ color: 'var(--color-tinta)' }} />
                <span><span className="texto-fuerte">{a.archivo.name}</span><br /><span className="texto-secundario texto-pequeno">
                  {a.tipo === 'CERTIFICADO_FONASA' ? 'Certificado de afiliación a FONASA' : 'Certificado de inscripción en CESFAM'} · {a.archivo.type === 'application/pdf' ? 'PDF' : 'foto'}, {Math.round(a.archivo.size / 1024)} KB</span></span></span>
              <button className="boton boton--texto boton--compacto" onClick={() => setArchivos(archivos.filter((_, j) => j !== i))}>Quitar</button>
            </div>
          ))}
          <div className="fila">
            <select aria-label="Tipo de certificado" className="control" style={{ maxWidth: 320 }} value={tipoNuevo} onChange={(e) => setTipoNuevo(e.target.value as Archivo['tipo'])}>
              <option value="CERTIFICADO_FONASA">Certificado de afiliación a FONASA</option>
              <option value="CERTIFICADO_CESFAM">Certificado de inscripción en CESFAM</option>
            </select>
            <button className="boton boton--secundario" onClick={() => entrada.current?.click()}><Upload size={18} aria-hidden="true" /> Agregar otro archivo (JPG, PNG o PDF)</button>
            <input ref={entrada} type="file" accept="image/jpeg,image/png,application/pdf" className="sr-only" aria-label="Archivo del certificado"
              onChange={(e) => { agregar(e.target.files?.[0]); e.target.value = ''; }} />
          </div>
          <p className="texto-secundario texto-pequeno">Solo el personal del policlínico podrá ver estos documentos.</p>
        </div>
      )}
      {modo === 'llevar' && <div className="aviso aviso--alerta">Tu hora quedará pendiente hasta que la TENS revise los certificados en el policlínico.</div>}
      {error && <div className="aviso aviso--error" role="alert">{error}</div>}
      <button className="boton" style={{ alignSelf: 'flex-start' }} disabled={!modo || (modo === 'subir' && archivos.length === 0) || enviando} onClick={enviar}>Continuar</button>
    </section>
  );
}

function PasoListo({ c, certificados }: { c: Comprobante; certificados: boolean }) {
  return (
    <section className="tarjeta tarjeta--padding pila" style={{ maxWidth: 720 }}>
      <div className="fila"><span className="iniciales"><CalendarCheck size={20} aria-hidden="true" /></span>
        <h1>{c.pendienteVerificacion ? 'Tu hora quedó reservada, pendiente de verificación' : 'Tu hora quedó reservada'}</h1></div>
      <div className="tarjeta pila--chica" style={{ padding: 16, background: 'var(--color-papel)', boxShadow: 'none' }}>
        <div className="texto-fuerte" style={{ fontSize: '1.25rem' }}>{c.fechaTexto}, {hora(c.hora)}</div>
        <div>{c.profesion} con {c.profesional} ({c.duracion} minutos)</div>
        <div>Policlínico {c.policlinico}, {c.box} · {c.direccion}</div>
      </div>
      {c.pendienteVerificacion && (
        <div className="aviso aviso--alerta pila--chica" style={{ flexDirection: 'column', gap: 4 }}>
          {certificados ? (
            <><strong>Recibimos</strong><span>• Certificado de afiliación a FONASA ✓</span><span>• Certificado de inscripción en tu CESFAM ✓</span><span>• El día de tu hora, trae tu RUT</span></>
          ) : (
            <><strong>Trae a tu primera atención</strong><span>• Certificado de afiliación a FONASA</span><span>• Certificado de inscripción en tu CESFAM</span><span>• Tu RUT</span></>
          )}
          <span className="texto-pequeno">La inscripción en el CESFAM es necesaria para derivarte a la red de salud si lo necesitas.</span>
        </div>
      )}
      <div className="pila--chica">
        <h2>Qué sigue</h2>
        <p><strong>Ahora:</strong> te enviamos la confirmación al celular registrado.</p>
        <p><strong>El día antes:</strong> te recordaremos tu hora. Si no puedes asistir, responde ese mensaje y la hora quedará libre para otra persona.</p>
      </div>
      <Link to="/" className="boton boton--secundario" style={{ alignSelf: 'flex-start' }}>Volver al inicio</Link>
    </section>
  );
}
