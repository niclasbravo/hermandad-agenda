import { useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../compartido/api';
import type { Policlinico, Profesion, Profesional } from '../compartido/tipos';

/** Cuando algo cambia (reserva, anulación o asistencia) se invalidan la agenda, los cupos y la ficha. */
export function useInvalidarAgenda() {
  const qc = useQueryClient();
  return () => {
    ['agenda-dia', 'cupos', 'paciente', 'historial', 'avisos', 'lista-espera', 'agenda-semana', 'cita'].forEach((k) =>
      qc.invalidateQueries({ queryKey: [k] }),
    );
  };
}

export function usePoliclinicos() {
  return useQuery({ queryKey: ['catalogo', 'policlinicos'], queryFn: () => api.get<Policlinico[]>('/api/policlinicos'), staleTime: 60_000 });
}

export function useProfesiones() {
  return useQuery({ queryKey: ['catalogo', 'profesiones'], queryFn: () => api.get<Profesion[]>('/api/profesiones'), staleTime: 60_000 });
}

export function useProfesionales() {
  return useQuery({ queryKey: ['catalogo', 'profesionales'], queryFn: () => api.get<Profesional[]>('/api/profesionales'), staleTime: 60_000 });
}
