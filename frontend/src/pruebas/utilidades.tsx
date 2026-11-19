import { render } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { App } from '../App';
import { ProveedorSesion } from '../compartido/sesion';
import type { Usuario } from '../compartido/tipos';

export const TENS: Usuario = { idUsuario: 2, usuario: 'c.munoz', nombre: 'Carolina Muñoz', perfil: 'TENS', idPoliclinicoBase: 1, policlinicoBase: 'Casa Central' };
export const ADMIN: Usuario = { idUsuario: 1, usuario: 'l.martinez', nombre: 'Laura Martínez', perfil: 'ADMINISTRACION', idPoliclinicoBase: null, policlinicoBase: null };

export function renderizar(ruta: string, usuario: Usuario | null = TENS) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false, staleTime: Infinity }, mutations: { retry: false } } });
  return render(
    <QueryClientProvider client={qc}>
      <MemoryRouter initialEntries={[ruta]}>
        <ProveedorSesion inicial={usuario}>
          <App />
        </ProveedorSesion>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

export function hoy() {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

export function manana() {
  const d = new Date();
  d.setDate(d.getDate() + 1);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

export const POLICLINICOS = [{ id: 1, nombre: 'Casa Central', direccion: 'San Isidro 218', comuna: 'Santiago Centro', activo: true, boxes: [{ id: 1, nombre: 'Box 1', activo: true }] }];
export const PROFESIONES = [{ id: 1, nombre: 'Medicina', duracionMin: 20, duracionAlumnoMin: null }, { id: 2, nombre: 'Psicología', duracionMin: 45, duracionAlumnoMin: null }];
export const PROFESIONALES = [{
  id: 1, nombres: 'Paula', apellidos: 'Rojas', nombreCompleto: 'Paula Rojas', profesion: PROFESIONES[0], duracionAtencion: 20, esAlumnoPractica: false,
  credencial: { tituloProfesional: null, casaEstudios: null, registroPrestador: null }, policlinicos: [{ id: 1, nombre: 'Casa Central' }],
  plazoPublicacionDias: 7, activo: true, aviso: null,
}];

export function cita(extra: Record<string, unknown> = {}) {
  return {
    id: 10, estado: 'RESERVADA', estadoTexto: 'Próxima', estadoConfirmacion: 'CONFIRMADA', canalReserva: 'TENS', fecha: hoy(),
    horaInicio: '09:00', horaTermino: '09:20', duracion: 20, idCupo: 100, idPaciente: 1, paciente: 'María González Pérez', nombrePila: 'María',
    iniciales: 'MG', documento: '12.345.678-5', telefono: '+56 9 1234 5678', adultoResponsable: null, relacionAdulto: null, telefonoAdulto: null,
    idProfesional: 1, profesional: 'Paula Rojas', idProfesion: 1, profesion: 'Medicina', idBox: 1, box: 'Box 1', idPoliclinico: 1,
    policlinico: 'Casa Central', direccion: 'San Isidro 218, Santiago Centro', idSerie: null, fechaReserva: hoy() + 'T08:00:00',
    origenAnulacion: null, motivoAnulacion: null, fechaAnulacion: null, fechaAsistencia: null, ...extra,
  };
}

export const MARIA = {
  id: 1, tipoDocumento: 'RUT', numeroDocumento: '12345678-5', documentoFormateado: '12.345.678-5', nombres: 'María', apellidos: 'González Pérez',
  nombreCompleto: 'María González Pérez', iniciales: 'MG', fechaNacimiento: '1952-01-01', edad: 74, esMenor: false, telefonoCelular: '+56912345678',
  telefonoFormateado: '+56 9 1234 5678', correo: null, direccion: 'Los Aromos 123', genero: 'Femenino', nacionalidad: 'Chilena',
  adultoResponsable: null, canalRegistro: 'TENS', fechaAvisoMensajes: '2026-01-01T10:00:00', habilitado: true, estadoHabilitacion: 'HABILITADO',
  verificacionVigente: null,
};
