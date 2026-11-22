import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { servidor } from './servidor';
import { cita, manana, MARIA, POLICLINICOS, PROFESIONALES, PROFESIONES, renderizar } from './utilidades';

const cupo = (id: number, horaInicio: string) => ({
  id, fecha: manana(), horaInicio, horaTermino: '10:00', duracion: 20, estado: 'DISPONIBLE', habilitadoWeb: true, motivoBloqueo: null,
  idProfesional: 1, profesional: 'Paula Rojas', idProfesion: 1, profesion: 'Medicina', idBox: 1, box: 'Box 1', idPoliclinico: 1,
  policlinico: 'Casa Central', direccion: 'San Isidro 218', paciente: null,
});

function base() {
  servidor.use(
    http.get('/api/policlinicos', () => HttpResponse.json(POLICLINICOS)),
    http.get('/api/profesiones', () => HttpResponse.json(PROFESIONES)),
    http.get('/api/profesionales', () => HttpResponse.json(PROFESIONALES)),
    http.get('/api/pacientes', ({ request }) => new URL(request.url).searchParams.get('documento') === '12.345.678-5'
      ? HttpResponse.json(MARIA)
      : HttpResponse.json({ codigo: 'PACIENTE_NO_ENCONTRADO', mensaje: 'No encontramos un paciente.', detalles: { ofrecerRegistro: true } }, { status: 404 })),
    http.get('/api/pacientes/1', () => HttpResponse.json(MARIA)),
  );
}

describe('Reservar cita: recorrido A (CU-04)', () => {
  it('busca al paciente, filtra, elige un cupo y confirma', async () => {
    base();
    let cuerpo: unknown = null;
    servidor.use(
      http.get('/api/cupos/disponibles', () => HttpResponse.json([cupo(100, '09:40'), cupo(101, '10:40'), cupo(102, '15:00')])),
      http.post('/api/citas', async ({ request }) => {
        cuerpo = await request.json();
        return HttpResponse.json({ cita: cita({ id: 50, fecha: manana(), horaInicio: '10:40', idCupo: 101 }), inscripcionesEnEspera: [] }, { status: 201 });
      }),
    );
    const u = userEvent.setup();
    renderizar('/reservar-cita');
    await u.type(await screen.findByLabelText('Paciente *'), '12.345.678-5');
    await u.click(within(screen.getByLabelText('Paciente *').closest('form')!).getByRole('button', { name: 'Buscar' }));
    expect(await screen.findByText('María González Pérez')).toBeInTheDocument();
    expect(screen.getByText('Habilitada')).toBeInTheDocument();
    await u.selectOptions(await screen.findByLabelText('Profesión *'), 'Medicina');
    await u.click(screen.getByRole('button', { name: /Buscar cupos/ }));
    expect(await screen.findByText('MAÑANA')).toBeInTheDocument();
    expect(screen.getByText('TARDE')).toBeInTheDocument();
    await u.click(screen.getByRole('button', { name: /10:40/ }));
    expect(screen.getByText('10:40 (20 minutos)')).toBeInTheDocument();
    expect(screen.getByText(/Se enviará al \+56 9 1234 5678/)).toBeInTheDocument();
    await u.click(screen.getByRole('button', { name: /Confirmar reserva/ }));
    expect(await screen.findByRole('heading', { name: 'Reserva confirmada' })).toBeInTheDocument();
    expect(cuerpo).toEqual({ idPaciente: 1, idCupo: 101 });
  });

  it('«Primera hora disponible» elige el primer cupo', async () => {
    base();
    servidor.use(http.get('/api/cupos/disponibles', () => HttpResponse.json([cupo(100, '09:40'), cupo(101, '10:40')])));
    const u = userEvent.setup();
    renderizar('/reservar-cita?paciente=1');
    await u.selectOptions(await screen.findByLabelText('Profesión *'), 'Medicina');
    await u.click(screen.getByRole('button', { name: /Buscar cupos/ }));
    await u.click(await screen.findByRole('button', { name: /Primera hora disponible/ }));
    expect(screen.getByText('09:40 (20 minutos)')).toBeInTheDocument();
  });

  it('quita de la lista de espera aunque una inscripción ya estuviera retirada (doble clic)', async () => {
    base();
    const retiradas: string[] = [];
    servidor.use(
      http.get('/api/cupos/disponibles', () => HttpResponse.json([cupo(100, '09:40')])),
      http.post('/api/citas', () => HttpResponse.json({ cita: cita({ id: 50, fecha: manana(), horaInicio: '09:40', idCupo: 100 }), inscripcionesEnEspera: [7, 8] }, { status: 201 })),
      http.post('/api/lista-espera/:id/retirar', ({ params }) => {
        retiradas.push(String(params.id));
        return params.id === '7'
          ? HttpResponse.json({ codigo: 'INSCRIPCION_RETIRADA', mensaje: 'Esta persona ya no está en la lista de espera.', detalles: {} }, { status: 422 })
          : HttpResponse.json({ id: 8 });
      }),
    );
    const u = userEvent.setup();
    renderizar('/reservar-cita?paciente=1');
    await u.selectOptions(await screen.findByLabelText('Profesión *'), 'Medicina');
    await u.click(screen.getByRole('button', { name: /Buscar cupos/ }));
    await u.click(await screen.findByRole('button', { name: /09:40/ }));
    await u.click(screen.getByRole('button', { name: /Confirmar reserva/ }));
    await u.click(await screen.findByRole('button', { name: 'Quitar de la lista de espera' }));
    expect(await screen.findByText(/Se quitó de la lista de espera/)).toBeInTheDocument();
    expect(retiradas).toEqual(['7', '8']);
    expect(screen.queryByText('Esta persona ya no está en la lista de espera.')).not.toBeInTheDocument();
  });

  it('si otra persona tomó el horario muestra el 409 en lenguaje simple', async () => {
    base();
    servidor.use(
      http.get('/api/cupos/disponibles', () => HttpResponse.json([cupo(100, '09:40')])),
      http.post('/api/citas', () => HttpResponse.json({ codigo: 'CUPO_TOMADO', mensaje: 'Otra persona tomó este horario hace un momento. Elige otro.', detalles: {} }, { status: 409 })),
    );
    const u = userEvent.setup();
    renderizar('/reservar-cita?paciente=1');
    await u.selectOptions(await screen.findByLabelText('Profesión *'), 'Medicina');
    await u.click(screen.getByRole('button', { name: /Buscar cupos/ }));
    await u.click(await screen.findByRole('button', { name: /09:40/ }));
    await u.click(screen.getByRole('button', { name: /Confirmar reserva/ }));
    expect(await screen.findByText('Otra persona tomó este horario hace un momento. Elige otro.')).toBeInTheDocument();
  });
});
