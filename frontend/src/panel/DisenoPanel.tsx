import { NavLink, useNavigate } from 'react-router-dom';
import { useState, type FormEvent, type ReactNode } from 'react';
import { BarChart3, Building2, CalendarCheck, CalendarDays, CalendarPlus, LogOut, Search, Users } from 'lucide-react';
import { useSesion } from '../compartido/sesion';
import { Logo } from '../compartido/componentes';

const MENU_TENS = [
  { a: '/agenda-dia', texto: 'Agenda del día', icono: CalendarDays },
  { a: '/pacientes', texto: 'Pacientes', icono: Users },
  { a: '/reservar-cita', texto: 'Reservar cita', icono: CalendarPlus },
  { a: '/reportes', texto: 'Reportes', icono: BarChart3 },
];

const MENU_ADMIN = [
  { a: '/agenda-cupos', texto: 'Agenda de cupos', icono: CalendarCheck },
  { a: '/catalogo', texto: 'Policlínicos y profesionales', icono: Building2 },
  { a: '/reportes', texto: 'Reportes', icono: BarChart3 },
];

export function DisenoPanel({ children }: { children: ReactNode }) {
  const { usuario, salir } = useSesion();
  const navegar = useNavigate();
  if (!usuario) return null;
  const menu = usuario.perfil === 'TENS' ? MENU_TENS : MENU_ADMIN;
  return (
    <div className="panel">
      <nav className="menu" aria-label="Menú principal">
        <div className="menu__logo"><Logo compacto /></div>
        <div className="menu__subtitulo">Agenda de atenciones</div>
        <ul className="menu__lista">
          {menu.map(({ a, texto, icono: Icono }) => (
            <li key={a}>
              <NavLink to={a} className="menu__item" end={a === '/pacientes' ? false : undefined}>
                <Icono size={20} aria-hidden="true" /> {texto}
              </NavLink>
            </li>
          ))}
        </ul>
        <div className="menu__pie">
          <div className="texto-fuerte">{usuario.nombre}</div>
          <div className="texto-secundario texto-pequeno">
            {usuario.perfil === 'TENS' ? `TENS/TONS · ${usuario.policlinicoBase}` : 'Administración'}
          </div>
          <button className="boton boton--texto" style={{ marginTop: 8, paddingLeft: 0 }}
            onClick={async () => { await salir(); navegar('/ingreso'); }}>
            <LogOut size={20} aria-hidden="true" /> Cerrar sesión
          </button>
        </div>
      </nav>
      <div className="contenido">{children}</div>
    </div>
  );
}

/** Cabecera de cada pantalla: título en Bitter, subtítulo y, para la TENS/TONS, el buscador por RUT (RNF-01). */
export function Cabecera({ titulo, subtitulo, children, buscador = true }: { titulo: string; subtitulo?: ReactNode; children?: ReactNode; buscador?: boolean }) {
  const { usuario } = useSesion();
  return (
    <header className="cabecera">
      <div className="cabecera__titulo">
        <h1>{titulo}</h1>
        {subtitulo && <p>{subtitulo}</p>}
      </div>
      <div className="cabecera__acciones">
        {children}
        {buscador && usuario?.perfil === 'TENS' && <BuscadorRut />}
      </div>
    </header>
  );
}

export function BuscadorRut({ inicial = '' }: { inicial?: string }) {
  const [valor, setValor] = useState(inicial);
  const navegar = useNavigate();
  const enviar = (e: FormEvent) => {
    e.preventDefault();
    if (valor.trim()) navegar('/pacientes?documento=' + encodeURIComponent(valor.trim()));
  };
  return (
    <form className="buscador" role="search" onSubmit={enviar}>
      <label className="sr-only" htmlFor="buscar-rut">Buscar paciente por RUT</label>
      <input id="buscar-rut" className="control" placeholder="Buscar paciente por RUT" value={valor} onChange={(e) => setValor(e.target.value)} />
      <button className="boton" type="submit"><Search size={20} aria-hidden="true" /> Buscar</button>
    </form>
  );
}
