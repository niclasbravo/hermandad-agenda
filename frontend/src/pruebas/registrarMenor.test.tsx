import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { servidor } from './servidor';
import { renderizar } from './utilidades';

describe('Registrar paciente menor sin adulto responsable (RN-05, CA-RF12-1)', () => {
  it('muestra el error y no envía el formulario', async () => {
    let enviado = false;
    servidor.use(http.post('/api/pacientes', () => { enviado = true; return HttpResponse.json({}, { status: 201 }); }));
    const u = userEvent.setup();
    renderizar('/pacientes/nuevo?documento=24.567.890-8');
    expect(await screen.findByRole('heading', { name: 'Registrar paciente' })).toBeInTheDocument();
    await u.type(screen.getByLabelText('Fecha de nacimiento *'), '2017-03-14');
    await u.type(screen.getByLabelText('Nombres *'), 'Tomás');
    await u.type(screen.getByLabelText('Apellidos *'), 'Fuentes Vega');
    await u.type(screen.getByLabelText('Celular *'), '+56 9 4444 1212');
    await u.type(screen.getByLabelText('Dirección *'), 'Calle Los Aromos 456');
    await u.selectOptions(screen.getByLabelText('Género *'), 'Masculino');
    expect(screen.getByText(/La persona es menor de edad/)).toBeInTheDocument();
    await u.click(screen.getByRole('button', { name: /Registrar paciente/ }));
    expect(await screen.findByText('Escribe el nombre del adulto responsable.')).toBeInTheDocument();
    expect(screen.getByText('Escribe el documento del adulto responsable.')).toBeInTheDocument();
    expect(screen.getByRole('alert')).toHaveTextContent('Faltan datos obligatorios');
    expect(enviado).toBe(false);
  });
});
