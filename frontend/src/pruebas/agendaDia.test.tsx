import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { servidor } from './servidor';
import { cita, manana, renderizar } from './utilidades';

const resumen = { citasHoy: 1, confirmadas: 1, porLlamar: 0, sinRespuesta: 0, enListaEspera: 1, pendientesVerificacion: 0 };

describe('Agenda del día (CU-06)', () => {
  it('registra la asistencia con el botón «Asistió» (RF-22)', async () => {
    let llamada: { id: string; cuerpo: unknown } | null = null;
    let asistio = false;
    servidor.use(
      http.get('/api/agenda-dia', () => HttpResponse.json({ fecha: '', idPoliclinico: 1, vista: 'LISTA', resumen, columnas: [],
        citas: [{ cita: cita(asistio ? { estado: 'ASISTIDA' } : {}), requiereLlamada: false, avisadoA: null, tieneDocumentos: false }] })),
      http.get('/api/avisos-cupo', () => HttpResponse.json([])),
      http.post('/api/citas/:id/asistencia', async ({ params, request }) => {
        llamada = { id: params.id as string, cuerpo: await request.json() };
        asistio = true;
        return HttpResponse.json({});
      }),
    );
    const u = userEvent.setup();
    renderizar('/agenda-dia');
    await u.click(await screen.findByRole('button', { name: 'Registrar que María González Pérez asistió' }));
    expect(llamada).toEqual({ id: '10', cuerpo: { asistio: true } });
    expect(await screen.findByText('Asistió', { selector: '.chip' })).toBeInTheDocument();
  });

  it('al anular muestra el aviso amarillo del cupo liberado con la primera de la lista de espera (RF-17, RF-26)', async () => {
    let anulada = false;
    let motivo: unknown = null;
    const aviso = { id: 1, idCupo: 100, fecha: '', hora: '09:00', profesion: 'Medicina', profesional: 'Paula Rojas', box: 'Box 1',
      idInscripcion: 5, idPaciente: 20, primera: 'Gloria Fuentes Lagos', telefono: '+56 9 5846 3242',
      mensaje: 'Se liberó un cupo de Medicina a las 09:00. Primera en la lista: Gloria Fuentes Lagos, +56 9 5846 3242.' };
    servidor.use(
      http.get('/api/agenda-dia', ({ request }) => {
        const fecha = new URL(request.url).searchParams.get('fecha')!;
        aviso.fecha = fecha;
        return HttpResponse.json({ fecha, idPoliclinico: 1, vista: 'LISTA', resumen, columnas: [],
          citas: anulada ? [] : [{ cita: cita({ fecha }), requiereLlamada: false, avisadoA: null, tieneDocumentos: false }] });
      }),
      http.get('/api/avisos-cupo', () => HttpResponse.json(anulada ? [aviso] : [])),
      http.post('/api/citas/:id/anular', async ({ request }) => { motivo = await request.json(); anulada = true; return HttpResponse.json(cita({ estado: 'ANULADA' })); }),
    );
    const u = userEvent.setup();
    renderizar('/agenda-dia');
    await u.click(await screen.findByRole('button', { name: 'Anular la cita de María González Pérez' }));
    const dialogo = await screen.findByRole('dialog', { name: '¿Anular la cita de María González Pérez?' });
    await u.type(within(dialogo).getByLabelText('Motivo (opcional)'), 'Avisó por teléfono');
    await u.click(within(dialogo).getByRole('button', { name: 'Anular cita' }));
    expect(motivo).toEqual({ origen: 'TENS', motivo: 'Avisó por teléfono' });
    const fila = (await screen.findByText(/Se liberó un cupo de Medicina a las 09:00/)).closest('tr')!;
    expect(fila).toHaveClass('fila--resaltada');
    expect(within(fila).getByRole('button', { name: 'Reservar para Gloria' })).toBeInTheDocument();
  });

  it('no permite registrar asistencia de una cita futura', async () => {
    servidor.use(
      http.get('/api/agenda-dia', () => HttpResponse.json({ fecha: manana(), idPoliclinico: 1, vista: 'LISTA', resumen, columnas: [],
        citas: [{ cita: cita({ fecha: manana() }), requiereLlamada: false, avisadoA: null, tieneDocumentos: false }] })),
      http.get('/api/avisos-cupo', () => HttpResponse.json([])),
    );
    renderizar('/agenda-dia');
    expect(await screen.findByRole('button', { name: 'Registrar que María González Pérez asistió' })).toBeDisabled();
  });
});
