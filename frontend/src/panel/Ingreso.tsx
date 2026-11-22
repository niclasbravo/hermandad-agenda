import { useState, type FormEvent } from 'react';
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom';
import { useSesion } from '../compartido/sesion';
import { Logo, MensajeError, Obligatorio } from '../compartido/componentes';
import { inicioDe } from '../App';

/**
 * P-00 Ingreso (222:1622). El perfil sale de la cuenta (corrección de la evaluación de P-00): un solo botón
 * «Ingresar» y el sistema lleva a cada usuario a su inicio (TENS/TONS → Agenda del día; Administración → Agenda de cupos).
 * El policlínico base tampoco se elige: lo asigna el backend según el usuario.
 */
export function Ingreso() {
  const { usuario, ingresar } = useSesion();
  const [nombre, setNombre] = useState('');
  const [contrasena, setContrasena] = useState('');
  const [error, setError] = useState<unknown>(null);
  const [errores, setErrores] = useState<{ usuario?: string; contrasena?: string }>({});
  const [enviando, setEnviando] = useState(false);
  const navegar = useNavigate();
  const desde = (useLocation().state as { desde?: string } | null)?.desde;

  if (usuario) return <Navigate to={desde ?? inicioDe(usuario.perfil)} replace />;

  const enviar = async (e: FormEvent) => {
    e.preventDefault();
    const faltan = {
      usuario: nombre.trim() ? undefined : 'Escribe tu usuario.',
      contrasena: contrasena ? undefined : 'Escribe tu contraseña.',
    };
    setErrores(faltan);
    if (faltan.usuario || faltan.contrasena) return;
    setEnviando(true);
    setError(null);
    try {
      const yo = await ingresar(nombre.trim(), contrasena);
      navegar(desde ?? inicioDe(yo.perfil), { replace: true });
    } catch (err) {
      setError(err);
    } finally {
      setEnviando(false);
    }
  };

  return (
    <main className="ingreso">
      <section className="ingreso__marca" aria-hidden="true">
        <span className="ingreso__circulo ingreso__circulo--1" />
        <span className="ingreso__circulo ingreso__circulo--2" />
        <div className="ingreso__marca-interior">
          <Logo tamano="grande" />
          <p className="ingreso__lema">Agenda de atenciones de los policlínicos</p>
        </div>
      </section>
      <section className="ingreso__lado">
        <div className="solo-movil ingreso__logo-movil"><Logo /></div>
        <form className="tarjeta ingreso__tarjeta pila aparecer" onSubmit={enviar} noValidate>
          <div>
            <h1>Ingresar</h1>
            <p className="texto-secundario texto-pequeno" style={{ marginTop: 6 }}>
              Entra con tu usuario: el sistema te muestra solo las funciones de tu perfil.
            </p>
          </div>
          <div className="campo">
            <label htmlFor="usuario">Usuario <Obligatorio /></label>
            <input id="usuario" className="control" autoComplete="username" value={nombre} onChange={(e) => setNombre(e.target.value)}
              aria-required="true" aria-invalid={!!errores.usuario} aria-describedby={errores.usuario ? 'usuario-error' : undefined} />
            {errores.usuario && <span id="usuario-error" className="campo-error">{errores.usuario}</span>}
          </div>
          <div className="campo">
            <label htmlFor="contrasena">Contraseña <Obligatorio /></label>
            <input id="contrasena" className="control" type="password" autoComplete="current-password" value={contrasena}
              onChange={(e) => setContrasena(e.target.value)} aria-required="true" aria-invalid={!!errores.contrasena}
              aria-describedby={errores.contrasena ? 'contrasena-error' : undefined} />
            {errores.contrasena && <span id="contrasena-error" className="campo-error">{errores.contrasena}</span>}
          </div>
          <MensajeError error={error} />
          <button className="boton boton--ancho" type="submit" disabled={enviando}>
            {enviando ? 'Ingresando…' : 'Ingresar'}
          </button>
          <p className="texto-secundario texto-pequeno">
            <Obligatorio /> Campo obligatorio. Prototipo con datos ficticios.
          </p>
        </form>
        <Link to="/" className="texto-fuerte enlace-volver">← Volver al sitio de reserva de horas</Link>
      </section>
    </main>
  );
}
