/** RUT chileno: dígito verificador módulo 11 (D-14: solo se valida si el tipo es RUT). */

export function limpiarRut(valor: string) {
  return valor.replace(/[^0-9kK]/g, '').toUpperCase().replace(/^0+/, '');
}

export function calcularDv(cuerpo: string) {
  let suma = 0;
  let factor = 2;
  for (let i = cuerpo.length - 1; i >= 0; i--) {
    suma += Number(cuerpo[i]) * factor;
    factor = factor === 7 ? 2 : factor + 1;
  }
  const resto = 11 - (suma % 11);
  return resto === 11 ? '0' : resto === 10 ? 'K' : String(resto);
}

export function rutValido(valor: string) {
  const limpio = limpiarRut(valor);
  if (!/^\d{1,9}[0-9K]$/.test(limpio)) return false;
  return calcularDv(limpio.slice(0, -1)) === limpio.slice(-1);
}

/** «12.345.678-5». */
export function formatearRut(valor: string) {
  const limpio = limpiarRut(valor);
  if (limpio.length < 2) return limpio;
  const cuerpo = limpio.slice(0, -1).replace(/\B(?=(\d{3})+(?!\d))/g, '.');
  return `${cuerpo}-${limpio.slice(-1)}`;
}

/** «+56 9 1234 5678». */
export function formatearTelefono(t?: string | null) {
  if (!t) return '';
  const d = t.replace(/[^\d+]/g, '');
  const m = d.match(/^\+?569(\d{4})(\d{4})$/);
  return m ? `+56 9 ${m[1]} ${m[2]}` : t;
}
