import { Navigate, Route, Routes, useLocation } from 'react-router-dom';
import { lazy, Suspense, type ReactNode } from 'react';
const SimuladorWhatsApp = lazy(() => import('./panel/SimuladorWhatsApp').then((m) => ({ default: m.SimuladorWhatsApp })));
const Reportes = lazy(() => import('./panel/reportes/Reportes').then((m) => ({ default: m.Reportes })));
const Catalogo = lazy(() => import('./panel/catalogo/Catalogo').then((m) => ({ default: m.Catalogo })));
const AgendaCupos = lazy(() => import('./panel/agenda-cupos/AgendaCupos').then((m) => ({ default: m.AgendaCupos })));
import { useSesion } from './compartido/sesion';
import { Cargando } from './compartido/componentes';
import type { Perfil } from './compartido/tipos';
import { Portada } from './reserva-en-linea/Portada';
import { Privacidad } from './reserva-en-linea/Privacidad';
import { FichaPoliclinico } from './reserva-en-linea/FichaPoliclinico';
import { ReservaEnLinea } from './reserva-en-linea/ReservaEnLinea';
import { Ingreso } from './panel/Ingreso';
import { DisenoPanel } from './panel/DisenoPanel';
import { AgendaDia } from './panel/agenda-dia/AgendaDia';
import { ListaEspera } from './panel/ListaEspera';
import { BuscarPaciente } from './panel/pacientes/BuscarPaciente';
import { FichaPaciente } from './panel/pacientes/FichaPaciente';
import { FormularioPaciente } from './panel/pacientes/FormularioPaciente';
import { ReservarCita } from './panel/reservar/ReservarCita';

/** Sin sesión no se muestra ninguna información (CA-RNF04-2). */
function Protegido({ perfiles, children }: { perfiles?: Perfil[]; children: ReactNode }) {
  const { usuario, cargando } = useSesion();
  const ubicacion = useLocation();
  if (cargando) return <Cargando />;
  if (!usuario) return <Navigate to="/ingreso" replace state={{ desde: ubicacion.pathname }} />;
  if (perfiles && !perfiles.includes(usuario.perfil)) return <Navigate to={inicioDe(usuario.perfil)} replace />;
  return <DisenoPanel><Suspense fallback={<Cargando />}>{children}</Suspense></DisenoPanel>;
}

export function inicioDe(perfil: Perfil) {
  return perfil === 'TENS' ? '/agenda-dia' : '/agenda-cupos';
}

export function App() {
  return (
    <Routes>
      <Route path="/" element={<Portada />} />
      <Route path="/policlinicos/:id" element={<FichaPoliclinico />} />
      <Route path="/reservar" element={<ReservaEnLinea />} />
      <Route path="/ingreso" element={<Ingreso />} />
      <Route path="/privacidad" element={<Privacidad />} />
      <Route path="/simulador-whatsapp" element={<Suspense fallback={<Cargando />}><SimuladorWhatsApp /></Suspense>} />

      <Route path="/agenda-dia" element={<Protegido><AgendaDia /></Protegido>} />
      <Route path="/lista-espera" element={<Protegido perfiles={['TENS']}><ListaEspera /></Protegido>} />
      <Route path="/pacientes" element={<Protegido><BuscarPaciente /></Protegido>} />
      <Route path="/pacientes/nuevo" element={<Protegido perfiles={['TENS']}><FormularioPaciente /></Protegido>} />
      <Route path="/pacientes/:id" element={<Protegido><FichaPaciente /></Protegido>} />
      <Route path="/pacientes/:id/editar" element={<Protegido perfiles={['TENS']}><FormularioPaciente /></Protegido>} />
      <Route path="/reservar-cita" element={<Protegido perfiles={['TENS']}><ReservarCita /></Protegido>} />
      <Route path="/agenda-cupos" element={<Protegido perfiles={['ADMINISTRACION']}><AgendaCupos /></Protegido>} />
      <Route path="/catalogo" element={<Protegido perfiles={['ADMINISTRACION']}><Catalogo /></Protegido>} />
      <Route path="/reportes" element={<Protegido><Reportes /></Protegido>} />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
