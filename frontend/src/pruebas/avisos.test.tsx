import { render, screen, act } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ProveedorAvisos, useAviso } from '../compartido/avisos';

function Disparador() {
  const avisar = useAviso();
  return <button onClick={() => avisar('Cita reservada', { detalle: 'La confirmación por WhatsApp sale en menos de un minuto.' })}>Reservar</button>;
}

describe('Avisos flotantes', () => {
  it('muestra el aviso al confirmar una acción y lo quita solo después de unos segundos', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    const u = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    render(<ProveedorAvisos><Disparador /></ProveedorAvisos>);
    await u.click(screen.getByRole('button', { name: 'Reservar' }));
    expect(screen.getByText('Cita reservada')).toBeInTheDocument();
    await act(async () => { vi.advanceTimersByTime(5000); });
    expect(screen.queryByText('Cita reservada')).not.toBeInTheDocument();
    vi.useRealTimers();
  });
});
