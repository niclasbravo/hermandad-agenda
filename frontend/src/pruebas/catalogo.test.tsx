import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { servidor } from './servidor';
import { ADMIN, POLICLINICOS, renderizar } from './utilidades';

describe('Catálogo: policlínicos y box (CU-01)', () => {
  it('el box agregado aparece al tiro en el modal del policlínico', async () => {
    let boxes = [...POLICLINICOS[0].boxes];
    servidor.use(
      http.get('/api/policlinicos', () => HttpResponse.json([{ ...POLICLINICOS[0], boxes }])),
      http.post('/api/policlinicos/1/box', async ({ request }) => {
        const { nombre } = (await request.json()) as { nombre: string };
        boxes = [...boxes, { id: 2, nombre, activo: true }];
        return HttpResponse.json({ id: 2, nombre, activo: true }, { status: 201 });
      }),
    );
    const u = userEvent.setup();
    renderizar('/catalogo', ADMIN);
    await u.click(await screen.findByRole('tab', { name: 'Policlínicos y box' }));
    await u.click(await screen.findByRole('button', { name: 'Editar Casa Central' }));
    const modal = await screen.findByRole('dialog');
    await u.type(within(modal).getByLabelText('Nombre del nuevo box'), 'Box 2');
    await u.click(within(modal).getByRole('button', { name: /Agregar box/ }));
    expect(await within(modal).findByText('Box: Box 1, Box 2')).toBeInTheDocument();
  });
});
