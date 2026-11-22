import { render, screen } from '@testing-library/react';
import { Asistencia, Demanda, Ocupacion } from '../panel/reportes/graficos';

const conteo = (etiqueta: string, reservadas: number, asistidas: number, inasistencias: number) => ({
  etiqueta, reservadas, asistidas, inasistencias,
  porcentajeInasistencia: asistidas + inasistencias ? Math.round((inasistencias * 1000) / (asistidas + inasistencias)) / 10 : 0,
});
const meses = ['2026-05', '2026-06', '2026-07', '2026-08', '2026-09', '2026-10'];
const filaOcupacion = (policlinico: string, profesional: string) => ({
  policlinico, profesional, profesion: 'Medicina', ofrecidos: 10, reservados: 8, disponibles: 2, porcentajeOcupacion: 80,
});

describe('Reportes: sin gráficos repetidos', () => {
  it('Asistencia con una sola profesión no muestra las barras por profesión', () => {
    render(<Asistencia d={{ hasta: '2026-10-31', total: conteo('Total', 10, 8, 2), anterior: conteo('Anterior', 10, 9, 1),
      porProfesion: [conteo('Medicina', 10, 8, 2)], evolucion: meses.map((m) => conteo(m, 10, 8, 2)) }} />);
    expect(screen.getByRole('heading', { name: 'Resultado de las citas' })).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: 'Inasistencia por profesión' })).not.toBeInTheDocument();
  });

  it('Asistencia con varias profesiones sí las muestra', () => {
    render(<Asistencia d={{ hasta: '2026-10-31', total: conteo('Total', 20, 16, 4), anterior: conteo('Anterior', 10, 9, 1),
      porProfesion: [conteo('Medicina', 10, 8, 2), conteo('Kinesiología', 10, 8, 2)], evolucion: meses.map((m) => conteo(m, 10, 8, 2)) }} />);
    expect(screen.getByRole('heading', { name: 'Inasistencia por profesión' })).toBeInTheDocument();
  });

  it('Asistencia sin resultados registrados no muestra 0 % ni la comparación', () => {
    render(<Asistencia d={{ hasta: '2026-11-29', total: conteo('Total', 38, 0, 0), anterior: conteo('Anterior', 40, 30, 10),
      porProfesion: [conteo('Medicina', 38, 0, 0)], evolucion: meses.map((m) => conteo(m, 0, 0, 0)) }} />);
    expect(screen.getByText(/todavía no tienen asistencia registrada/)).toBeInTheDocument();
    expect(screen.queryByText(/vs período anterior/)).not.toBeInTheDocument();
    expect(screen.queryByText('0,0 %')).not.toBeInTheDocument();
  });

  it('Ocupación filtrada por un policlínico se desglosa por profesional', () => {
    render(<Ocupacion d={{ ofrecidos: 20, reservados: 16, disponibles: 4, porcentajeOcupacion: 80, anterior: { ofrecidos: 0, porcentajeOcupacion: 0 },
      filas: [filaOcupacion('Casa Central', 'Paula Rojas'), filaOcupacion('Casa Central', 'Andrés Vidal')] }} />);
    expect(screen.getByRole('heading', { name: 'Ocupación por profesional' })).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: 'Ocupación por policlínico' })).not.toBeInTheDocument();
    // El período anterior no tiene cupos: no se compara.
    expect(screen.queryByText(/vs período anterior/)).not.toBeInTheDocument();
  });

  it('Ocupación de un solo profesional en un solo policlínico deja solo el anillo, a todo el ancho', () => {
    const { container } = render(<Ocupacion d={{ ofrecidos: 10, reservados: 8, disponibles: 2, porcentajeOcupacion: 80,
      anterior: { ofrecidos: 10, porcentajeOcupacion: 70 }, filas: [filaOcupacion('Casa Central', 'Paula Rojas')] }} />);
    expect(screen.queryByRole('heading', { name: /Ocupación por/ })).not.toBeInTheDocument();
    expect(container.querySelector('.anillo-tarjeta--ancha')).not.toBeNull();
  });

  it('Demanda con un solo profesional no muestra las barras', () => {
    render(<Demanda d={{ reservas: 3, enEspera: 1, filas: [{ profesional: 'Paula Rojas', profesion: 'Medicina', policlinicos: 'Casa Central', reservas: 3, enEspera: 1, demanda: 4 }] }} />);
    expect(screen.queryByRole('heading', { name: 'Demanda por profesional' })).not.toBeInTheDocument();
    expect(screen.getByText('personas esperando hoy (no depende del período)')).toBeInTheDocument();
  });
});
