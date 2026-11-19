import { screen } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { servidor } from './servidor';
import { ADMIN, POLICLINICOS, PROFESIONALES, PROFESIONES, renderizar, TENS } from './utilidades';

function manejadoresBase() {
  servidor.use(
    http.get('/api/agenda-dia', () => HttpResponse.json({ fecha: '2026-11-18', idPoliclinico: 1, vista: 'LISTA',
      resumen: { citasHoy: 0, confirmadas: 0, porLlamar: 0, sinRespuesta: 0, enListaEspera: 0, pendientesVerificacion: 0 }, citas: [], columnas: [] })),
    http.get('/api/avisos-cupo', () => HttpResponse.json([])),
    http.get('/api/policlinicos', () => HttpResponse.json(POLICLINICOS)),
    http.get('/api/profesiones', () => HttpResponse.json(PROFESIONES)),
    http.get('/api/profesionales', () => HttpResponse.json(PROFESIONALES)),
    http.get('/api/agenda/semana', () => HttpResponse.json([])),
    http.get('/api/bloques', () => HttpResponse.json([])),
  );
}

describe('Permisos del menú por perfil (RNF-04)', () => {
  it('la TENS/TONS ve solo sus opciones', async () => {
    manejadoresBase();
    renderizar('/agenda-dia', TENS);
    const menu = await screen.findByRole('navigation', { name: 'Menú principal' });
    for (const t of ['Agenda del día', 'Pacientes', 'Reservar cita', 'Reportes']) expect(menu).toHaveTextContent(t);
    expect(menu).not.toHaveTextContent('Agenda de cupos');
    expect(menu).not.toHaveTextContent('Policlínicos y profesionales');
    expect(menu).toHaveTextContent('TENS/TONS · Casa Central');
  });

  it('la Administración ve solo sus opciones', async () => {
    manejadoresBase();
    renderizar('/agenda-cupos', ADMIN);
    const menu = await screen.findByRole('navigation', { name: 'Menú principal' });
    for (const t of ['Agenda de cupos', 'Policlínicos y profesionales', 'Reportes']) expect(menu).toHaveTextContent(t);
    expect(menu).not.toHaveTextContent('Agenda del día');
    expect(menu).not.toHaveTextContent('Reservar cita');
  });

  it('la Administración que entra a una pantalla de la TENS/TONS vuelve a su inicio', async () => {
    manejadoresBase();
    renderizar('/reservar-cita', ADMIN);
    expect(await screen.findByRole('heading', { name: 'Agenda de cupos' })).toBeInTheDocument();
  });

  it('sin sesión no se muestra información y se redirige al ingreso (CA-RNF04-2)', async () => {
    renderizar('/agenda-dia', null);
    expect(await screen.findByRole('heading', { name: 'Ingresar' })).toBeInTheDocument();
    expect(screen.queryByText('Agenda del día')).not.toBeInTheDocument();
  });
});
