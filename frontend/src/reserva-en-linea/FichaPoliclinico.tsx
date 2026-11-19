import { Link, useParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { MapPin } from 'lucide-react';
import { api } from '../compartido/api';
import { Cargando } from '../compartido/componentes';
import { CabeceraPublica } from './CabeceraPublica';
import type { PoliclinicoPublico } from './Portada';

/** Ficha del policlínico (246:1601 … 246:1686). */
export function FichaPoliclinico() {
  const { id } = useParams();
  const { data, isLoading } = useQuery({ queryKey: ['publico', 'policlinicos'], queryFn: () => api.get<PoliclinicoPublico[]>('/api/publico/policlinicos') });
  const p = data?.find((x) => String(x.id) === id);
  return (
    <div className="publico">
      <CabeceraPublica />
      <main className="seccion">
        {isLoading && <Cargando />}
        {!isLoading && !p && <p>No encontramos ese policlínico. <Link to="/">Volver al inicio</Link></p>}
        {p && (
          <div className="tarjeta tarjeta--padding pila" style={{ maxWidth: 560 }}>
            <h1>Policlínico {p.nombre}</h1>
            <p className="fila" style={{ gap: 8 }}><MapPin size={18} aria-hidden="true" /> {p.direccion}, {p.comuna}</p>
            <p className="texto-secundario">Atención de salud gratuita. Puedes reservar en línea o pedir tu hora en el policlínico.</p>
            {p.profesiones.length > 0 && (
              <div className="pila--chica">
                <span className="etiqueta">Atenciones</span>
                <div className="fila" style={{ gap: 8 }}>{p.profesiones.map((x) => <span key={x} className="chip chip--info">{x}</span>)}</div>
              </div>
            )}
            <div className="fila">
              <Link to={`/reservar?policlinico=${p.id}`} className="boton">Reservar hora aquí</Link>
              <a className="boton boton--secundario" target="_blank" rel="noreferrer"
                href={`https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(p.direccion + ', ' + p.comuna + ', Chile')}`}>
                Cómo llegar →
              </a>
            </div>
            <Link to="/">← Volver</Link>
          </div>
        )}
      </main>
    </div>
  );
}
