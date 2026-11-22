import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { servidor } from './servidor';
import { manana, renderizar } from './utilidades';

describe('Reserva en línea con paciente nuevo (CU-08)', () => {
  it('identificación → NUEVO → datos → elegir hora → pendiente de verificación', async () => {
    let registro: Record<string, unknown> | null = null;
    let token: string | null = null;
    servidor.use(
      http.post('/api/publico/identificacion', () => HttpResponse.json({ token: 'tok-1', estado: 'NUEVO', documentoEnmascarado: '21.456.***-K', nombrePila: null })),
      http.post('/api/publico/pacientes', async ({ request }) => {
        registro = await request.json() as Record<string, unknown>;
        token = request.headers.get('X-Token-Reserva');
        return HttpResponse.json({ token: 'tok-1', estado: 'IDENTIFICADO', documentoEnmascarado: '21.456.***-K', nombrePila: 'Rosa' }, { status: 201 });
      }),
      http.get('/api/publico/policlinicos', () => HttpResponse.json([{ id: 1, nombre: 'Casa Central', direccion: 'San Isidro 218', comuna: 'Santiago Centro', profesiones: ['Kinesiología'] }])),
      http.get('/api/publico/profesiones', () => HttpResponse.json([{ id: 2, nombre: 'Kinesiología' }])),
      http.get('/api/publico/profesionales', () => HttpResponse.json([{ id: 3, nombre: 'Tomás Pinto', idProfesion: 2, idsPoliclinico: [1] }])),
      http.get('/api/publico/cupos', () => HttpResponse.json([{ id: 900, fecha: manana(), hora: '10:30', duracion: 30, idProfesional: 3, profesional: 'Tomás Pinto',
        profesion: 'Kinesiología', idPoliclinico: 1, policlinico: 'Casa Central', direccion: 'San Isidro 218, Santiago Centro', box: 'Box 2' }])),
      http.post('/api/publico/reservas', () => HttpResponse.json({ idCita: 77, estado: 'PENDIENTE_VERIFICACION', nombrePila: 'Rosa', policlinico: 'Casa Central',
        direccion: 'San Isidro 218, Santiago Centro', profesional: 'Tomás Pinto', profesion: 'Kinesiología', box: 'Box 2', fecha: manana(), hora: '10:30',
        fechaTexto: 'Jueves 19 de noviembre', duracion: 30, pendienteVerificacion: true, mensaje: '' }, { status: 201 })),
    );
    const u = userEvent.setup();
    renderizar('/reservar', null);
    await u.type(await screen.findByLabelText('RUT *'), '21.456.789-5');
    await u.click(screen.getByRole('button', { name: 'Continuar' }));
    expect(await screen.findByText('El RUT no es válido. Revisa el número y el dígito verificador.')).toBeInTheDocument();
    await u.clear(screen.getByLabelText('RUT *'));
    await u.type(screen.getByLabelText('RUT *'), '7.654.321-6');
    await u.type(screen.getByLabelText('Fecha de nacimiento *'), '1974-05-10');
    await u.click(screen.getByRole('button', { name: 'Continuar' }));
    expect(await screen.findByText('No encontramos una ficha con estos datos')).toBeInTheDocument();
    await u.type(screen.getByLabelText('Nombres *'), 'Rosa Elena');
    await u.type(screen.getByLabelText('Apellidos *'), 'Muñoz Díaz');
    await u.type(screen.getByLabelText('Teléfono celular *'), '+56 9 6123 4567');
    await u.type(screen.getByLabelText('Dirección *'), 'Pasaje Los Aromos 1234');
    await u.selectOptions(screen.getByLabelText('Género *'), 'Femenino');
    await u.click(screen.getByRole('button', { name: 'Continuar' }));
    expect(await screen.findByRole('heading', { name: 'Elige tu hora' })).toBeInTheDocument();
    expect(token).toBe('tok-1');
    expect(registro).toMatchObject({ nombres: 'Rosa Elena', apellidos: 'Muñoz Díaz', adulto: null });
    await u.selectOptions(screen.getByLabelText('Profesión *'), 'Kinesiología');
    await u.click(await screen.findByRole('button', { name: /10:30/ }));
    await u.click(screen.getByRole('button', { name: 'Reservar esta hora' }));
    expect(await screen.findByRole('heading', { name: '¿Cómo quieres entregar tus certificados?' })).toBeInTheDocument();
    await u.click(screen.getByLabelText(/Los llevaré a mi primera atención/));
    await u.click(screen.getByRole('button', { name: 'Continuar' }));
    expect(await screen.findByRole('heading', { name: /pendiente de verificación/ })).toBeInTheDocument();
    expect(screen.getByText('Trae a tu primera atención')).toBeInTheDocument();
  });
});
