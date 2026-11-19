import { expect, test } from '@playwright/test';
import { rutAleatorio, sinScrollHorizontal } from './ayudas';

/** Recorrido H: reserva en línea de un paciente nuevo; la cita queda pendiente de verificación (RN-13). */
test('reserva en línea con un paciente nuevo', async ({ page }) => {
  await page.goto('/');
  await sinScrollHorizontal(page);
  await page.getByRole('link', { name: 'Reservar hora' }).first().click();

  await expect(page.getByRole('heading', { name: 'Reserva tu hora' })).toBeVisible();
  await sinScrollHorizontal(page);
  await page.getByLabel('RUT').fill(rutAleatorio());
  await page.getByLabel('Fecha de nacimiento').fill('1980-06-15');
  await page.getByRole('button', { name: 'Continuar' }).click();

  await expect(page.getByText('No encontramos una ficha con estos datos')).toBeVisible();
  await page.getByLabel('Nombres').fill('Prueba');
  await page.getByLabel('Apellidos').fill('Automática Ficticia');
  await page.getByLabel('Teléfono celular').fill('+56 9 7777 1234');
  await page.getByLabel('Dirección').fill('Calle de Prueba 123');
  await page.getByLabel('Género').selectOption('Femenino');
  await sinScrollHorizontal(page);
  await page.getByRole('button', { name: 'Continuar' }).click();

  await expect(page.getByRole('heading', { name: 'Elige tu hora' })).toBeVisible();
  await expect(page.getByText('Paciente identificado')).toBeVisible();
  await page.getByLabel('Profesión').selectOption({ label: 'Medicina' });
  const cupo = page.locator('button.cupo').first();
  await expect(cupo).toBeVisible();
  await cupo.click();
  await sinScrollHorizontal(page);
  await page.getByRole('button', { name: 'Reservar esta hora' }).click();

  await expect(page.getByRole('heading', { name: '¿Cómo quieres entregar tus certificados?' })).toBeVisible();
  await page.getByText('Los llevaré a mi primera atención').click();
  await page.getByRole('button', { name: 'Continuar' }).click();

  await expect(page.getByRole('heading', { name: /pendiente de verificación/ })).toBeVisible();
  await expect(page.getByText('Trae a tu primera atención')).toBeVisible();
  await sinScrollHorizontal(page);
});
