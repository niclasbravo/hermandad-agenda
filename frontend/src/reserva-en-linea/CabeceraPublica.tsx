import { Link } from 'react-router-dom';
import { Lock } from 'lucide-react';
import { Logo } from '../compartido/componentes';

export function CabeceraPublica({ modoReserva = false }: { modoReserva?: boolean }) {
  return (
    <header className="publico__cabecera">
      <Link to="/" aria-label="Inicio" style={{ textDecoration: 'none' }}><Logo compacto /></Link>
      {modoReserva ? (
        <nav className="publico__nav" aria-label="Ayuda">
          <span style={{ fontFamily: 'var(--fuente-titulo)', color: 'var(--color-tinta)', fontSize: '1.0625rem' }}>Reserva de horas en línea</span>
          <span className="texto-secundario texto-pequeno oculto-movil">¿Prefieres ayuda? Pide tu hora en tu policlínico o por teléfono</span>
          <Link to="/ingreso" className="texto-pequeno" style={{ textDecoration: 'underline' }}>Ingreso del personal</Link>
        </nav>
      ) : (
        <nav className="publico__nav" aria-label="Sitio">
          <Link to="/reservar">Reservar hora</Link>
          <a href="/#requisitos">Requisitos</a>
          <a href="/#policlinicos">Policlínicos</a>
          <Link to="/ingreso" className="boton boton--gris-borde boton--compacto"><Lock size={16} aria-hidden="true" /> Ingreso del personal</Link>
        </nav>
      )}
    </header>
  );
}
