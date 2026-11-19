import { expect, type Page } from '@playwright/test';

export const CONTRASENA = 'Hermandad2026!';

export async function ingresar(page: Page, usuario: string, perfil: 'TENS' | 'ADMINISTRACION') {
  await page.goto('/ingreso');
  await page.getByLabel('Usuario').fill(usuario);
  await page.getByLabel('Contraseña').fill(CONTRASENA);
  await page.getByRole('button', { name: perfil === 'TENS' ? 'Ingresar como TENS/TONS' : 'Ingresar como Administración' }).click();
  await expect(page.getByRole('navigation', { name: 'Menú principal' })).toBeVisible();
}

/** RUT aleatorio con dígito verificador correcto (módulo 11). */
export function rutAleatorio() {
  const cuerpo = String(30_000_000 + Math.floor(Math.random() * 9_000_000));
  let suma = 0;
  let factor = 2;
  for (let i = cuerpo.length - 1; i >= 0; i--) {
    suma += Number(cuerpo[i]) * factor;
    factor = factor === 7 ? 2 : factor + 1;
  }
  const r = 11 - (suma % 11);
  return `${cuerpo}-${r === 11 ? '0' : r === 10 ? 'K' : r}`;
}

export async function sinScrollHorizontal(page: Page) {
  const ancho = await page.evaluate(() => [document.documentElement.scrollWidth, window.innerWidth]);
  expect(ancho[0], 'no debe haber scroll horizontal').toBeLessThanOrEqual(ancho[1]);
}
