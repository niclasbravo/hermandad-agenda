import { expect, test } from '@playwright/test';
import { ingresar } from './ayudas';

/** Recorrido A del prototipo: la TENS/TONS busca a un paciente habilitado y le reserva una hora. */
test('la TENS/TONS reserva una hora para un paciente registrado', async ({ page }) => {
  await ingresar(page, 'c.munoz', 'TENS');
  await expect(page.getByRole('heading', { name: 'Agenda del día' })).toBeVisible();

  await page.getByLabel('Buscar paciente por RUT').fill('15.432.109-8');
  await page.getByRole('search').getByRole('button', { name: 'Buscar' }).click();
  await expect(page.getByRole('heading', { name: 'Pedro Lagos Vidal' })).toBeVisible();
  await expect(page.getByText('Habilitada para reservar')).toBeVisible();

  await page.getByRole('button', { name: 'Reservar cita' }).click();
  await expect(page.getByRole('heading', { name: 'Reservar cita' })).toBeVisible();
  await page.getByLabel('Profesión').selectOption({ label: 'Medicina' });
  await page.getByLabel('Hasta').fill(new Date(Date.now() + 14 * 864e5).toISOString().slice(0, 10));
  await page.getByRole('button', { name: 'Buscar cupos' }).click();
  await page.getByRole('button', { name: 'Primera hora disponible' }).click();
  await expect(page.getByText('Confirmación automática')).toBeVisible();
  await page.getByRole('button', { name: 'Confirmar reserva' }).click();

  await expect(page.getByRole('heading', { name: 'Reserva confirmada' })).toBeVisible();
  await expect(page.getByText('Reservado, ya no aparece en la búsqueda')).toBeVisible();
});
