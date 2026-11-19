/** Formatos chilenos (RNF-02): «Martes 13 de octubre de 2026», «13/10/2026», «09:30». */

const DIAS = ['domingo', 'lunes', 'martes', 'miércoles', 'jueves', 'viernes', 'sábado'];
const MESES = ['enero', 'febrero', 'marzo', 'abril', 'mayo', 'junio', 'julio', 'agosto', 'septiembre', 'octubre', 'noviembre', 'diciembre'];

export function aFecha(iso: string): Date {
  const [a, m, d] = iso.slice(0, 10).split('-').map(Number);
  return new Date(a, m - 1, d);
}

export function iso(fecha: Date): string {
  const m = String(fecha.getMonth() + 1).padStart(2, '0');
  const d = String(fecha.getDate()).padStart(2, '0');
  return `${fecha.getFullYear()}-${m}-${d}`;
}

export function hoyIso(): string {
  return iso(new Date());
}

export function sumarDias(isoFecha: string, dias: number): string {
  const f = aFecha(isoFecha);
  f.setDate(f.getDate() + dias);
  return iso(f);
}

export function lunesDe(isoFecha: string): string {
  const f = aFecha(isoFecha);
  const dia = (f.getDay() + 6) % 7;
  f.setDate(f.getDate() - dia);
  return iso(f);
}

export function capitalizar(t: string) {
  return t ? t.charAt(0).toUpperCase() + t.slice(1) : t;
}

/** «Martes 13 de octubre de 2026». */
export function fechaLargaConAno(isoFecha: string) {
  const f = aFecha(isoFecha);
  return `${capitalizar(DIAS[f.getDay()])} ${f.getDate()} de ${MESES[f.getMonth()]} de ${f.getFullYear()}`;
}

/** «Martes 13 de octubre». */
export function fechaLarga(isoFecha: string) {
  const f = aFecha(isoFecha);
  return `${capitalizar(DIAS[f.getDay()])} ${f.getDate()} de ${MESES[f.getMonth()]}`;
}

/** «Martes 13». */
export function diaCorto(isoFecha: string) {
  const f = aFecha(isoFecha);
  return `${capitalizar(DIAS[f.getDay()])} ${f.getDate()}`;
}

/** «13/10/2026». */
export function fechaCorta(isoFecha: string) {
  const f = aFecha(isoFecha);
  return `${String(f.getDate()).padStart(2, '0')}/${String(f.getMonth() + 1).padStart(2, '0')}/${f.getFullYear()}`;
}

/** «13/10». */
export function fechaDiaMes(isoFecha: string) {
  return fechaCorta(isoFecha).slice(0, 5);
}

/** «09:30». */
export function hora(h: string) {
  return h ? h.slice(0, 5) : '';
}

/** «13/10, 09:30» desde un datetime ISO. */
export function fechaHoraCorta(dt: string) {
  return `${fechaDiaMes(dt.slice(0, 10))}, ${dt.slice(11, 16)}`;
}

export function nombreMes(isoFecha: string) {
  const f = aFecha(isoFecha);
  return `${capitalizar(MESES[f.getMonth()])} ${f.getFullYear()}`;
}

export function mesCorto(clave: string) {
  const [, m] = clave.split('-').map(Number);
  return capitalizar(MESES[m - 1].slice(0, 3));
}

export function rangoSemana(lunes: string) {
  const viernes = sumarDias(lunes, 4);
  const a = aFecha(lunes);
  const b = aFecha(viernes);
  const mes = a.getMonth() === b.getMonth() ? '' : ` de ${MESES[a.getMonth()]}`;
  return `${a.getDate()}${mes} al ${b.getDate()} de ${MESES[b.getMonth()]} de ${b.getFullYear()}`;
}

export function porcentaje(n: number) {
  return `${n.toLocaleString('es-CL', { minimumFractionDigits: 1, maximumFractionDigits: 1 })} %`;
}

export function numero(n: number) {
  return n.toLocaleString('es-CL');
}

export function edad(isoNacimiento: string, referencia = new Date()) {
  const n = aFecha(isoNacimiento);
  let e = referencia.getFullYear() - n.getFullYear();
  const m = referencia.getMonth() - n.getMonth();
  if (m < 0 || (m === 0 && referencia.getDate() < n.getDate())) e--;
  return e;
}

export const DIAS_SEMANA = ['Lunes', 'Martes', 'Miércoles', 'Jueves', 'Viernes', 'Sábado', 'Domingo'];
