import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { Building2, CalendarCheck, Check, FileCheck2, IdCard, MapPin, MessageCircle, Stethoscope, ArrowRight } from 'lucide-react';
import { api } from '../compartido/api';
import { CabeceraPublica } from './CabeceraPublica';
import { Logo } from '../compartido/componentes';

export interface PoliclinicoPublico { id: number; nombre: string; direccion: string; comuna: string; profesiones: string[] }

/** P-00 Portada pública (242:1584). Sin fotos de personas reales: ilustración con formas. */
export function Portada() {
  const { data: policlinicos = [] } = useQuery({ queryKey: ['publico', 'policlinicos'], queryFn: () => api.get<PoliclinicoPublico[]>('/api/publico/policlinicos') });
  return (
    <div className="publico">
      <CabeceraPublica />
      <main>
        <section className="heroe">
          <span className="heroe__circulo" style={{ width: 420, height: 420, right: -60, top: 30 }} aria-hidden="true" />
          <span className="heroe__circulo" style={{ width: 160, height: 160, right: 330, top: 260, background: 'rgba(0,104,122,.22)' }} aria-hidden="true" />
          <div className="heroe__interior">
            <div className="pila" style={{ gap: 20, position: 'relative' }}>
              <span className="chip" style={{ background: '#fff', color: 'var(--color-tinta)', alignSelf: 'flex-start', padding: '6px 12px' }}>
                <Building2 size={16} aria-hidden="true" /> Atención de salud gratuita en 6 policlínicos
              </span>
              <h1>Reserva tu hora de atención</h1>
              <p style={{ fontSize: '1.125rem', lineHeight: '1.75rem', maxWidth: 520 }}>
                Pídela tú o un familiar, solo con el RUT y la fecha de nacimiento del paciente. Te confirmamos por WhatsApp y te recordamos tu hora el día antes.
              </p>
              <div className="fila">
                <Link to="/reservar" className="boton">Reservar hora</Link>
                <a href="#requisitos" className="boton boton--secundario">Ver requisitos</a>
              </div>
              <p className="texto-secundario fila" style={{ gap: 8 }}><MapPin size={18} aria-hidden="true" /> ¿Prefieres hacerlo en persona? Pide tu hora en tu policlínico o por teléfono.</p>
            </div>
            <div style={{ position: 'relative', display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 16 }}>
              <div className="tarjeta tarjeta--padding pila" style={{ maxWidth: 380, width: '100%' }}>
                <h2 style={{ color: '#0b4a55', fontWeight: 600 }}>Así de simple</h2>
                {[
                  [IdCard, '1. Identifica al paciente', 'RUT y fecha de nacimiento.'],
                  [CalendarCheck, '2. Elige día y hora', 'Policlínico, especialidad y profesional.'],
                  [MessageCircle, '3. Recibe la confirmación', 'Por WhatsApp, y el recordatorio el día antes.'],
                ].map(([Icono, t, d]) => {
                  const I = Icono as typeof IdCard;
                  return (
                    <div key={t as string} className="fila" style={{ alignItems: 'flex-start', flexWrap: 'nowrap' }}>
                      <span className="paso-icono"><I size={20} aria-hidden="true" /></span>
                      <div><div className="texto-fuerte">{t as string}</div><div className="texto-secundario texto-pequeno">{d as string}</div></div>
                    </div>
                  );
                })}
              </div>
              <div className="tarjeta fila" style={{ padding: '10px 14px', alignSelf: 'flex-end', flexWrap: 'nowrap' }} aria-hidden="true">
                <span className="iniciales"><Check size={18} /></span>
                <div><div className="texto-fuerte" style={{ color: 'var(--color-exito)' }}>Hora reservada</div><div className="texto-pequeno texto-secundario">Te llega por WhatsApp</div></div>
              </div>
            </div>
          </div>
        </section>

        <section className="seccion pila" id="requisitos" style={{ gap: 24 }}>
          <div className="pila--chica">
            <h2>Antes de reservar</h2>
            <p className="texto-secundario">Atendemos a personas con FONASA inscritas en su CESFAM. Ten a mano:</p>
          </div>
          <div className="grilla-3">
            {[
              [FileCheck2, 'Certificado de FONASA', 'Lo puedes descargar en la página de FONASA.'],
              [Stethoscope, 'Inscripción en tu CESFAM', 'El certificado del CESFAM que te corresponde según tu domicilio.'],
              [IdCard, 'RUT del paciente', 'Si es menor de edad, también los datos de su adulto responsable.'],
            ].map(([Icono, t, d]) => {
              const I = Icono as typeof IdCard;
              return (
                <div key={t as string} className="tarjeta tarjeta--padding pila--chica" style={{ background: 'var(--color-papel)' }}>
                  <span className="paso-icono"><I size={20} aria-hidden="true" /></span>
                  <h3 style={{ fontFamily: 'var(--fuente-cuerpo)', fontWeight: 700 }}>{t as string}</h3>
                  <p className="texto-secundario">{d as string}</p>
                </div>
              );
            })}
          </div>
          <div className="aviso aviso--info">
            <span>¿Es tu primera atención? Al reservar puedes subir una foto o PDF de tus certificados, o llevarlos ese día al policlínico.</span>
          </div>
        </section>

        <section className="seccion pila" id="policlinicos" style={{ gap: 24, paddingTop: 0 }}>
          <div className="pila--chica">
            <h2>Nuestros policlínicos</h2>
            <p className="texto-secundario">Atención gratuita en 6 comunas de la Región Metropolitana. Elige un policlínico para ver cómo llegar.</p>
          </div>
          <div className="grilla-3">
            {policlinicos.map((p, i) => (
              <Link key={p.id} to={`/policlinicos/${p.id}`} className="tarjeta tarjeta--padding fila" style={{ textDecoration: 'none', color: 'inherit', alignItems: 'flex-start', flexWrap: 'nowrap' }}>
                <span className="paso-icono" style={{ fontWeight: 700 }}>{i + 1}</span>
                <div style={{ flex: 1 }}>
                  <div className="texto-fuerte">Policlínico {p.nombre}</div>
                  <div className="texto-secundario texto-pequeno">{p.direccion}, {p.comuna}</div>
                </div>
                <span className="texto-fuerte" style={{ color: 'var(--color-tinta)', whiteSpace: 'nowrap' }}>Ver <ArrowRight size={16} aria-hidden="true" style={{ verticalAlign: 'middle' }} /></span>
              </Link>
            ))}
          </div>
        </section>
      </main>
      <footer className="pie fila fila--entre">
        <div className="pila--chica">
          <Logo compacto />
          <span className="texto-pequeno">Más de 200 años al servicio de Chile</span>
        </div>
        <Link to="/ingreso">Ingreso del personal</Link>
      </footer>
    </div>
  );
}
