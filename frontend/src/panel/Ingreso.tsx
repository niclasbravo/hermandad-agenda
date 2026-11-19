import { useState, type FormEvent } from 'react';
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom';
import { useSesion } from '../compartido/sesion';
import { Logo, MensajeError } from '../compartido/componentes';
import type { Perfil } from '../compartido/tipos';
import { inicioDe } from '../App';

/** P-00 Ingreso (222:1622). El backend valida que el perfil del usuario coincida con el botón. */
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

  const enviar = async (perfil: Perfil, e?: FormEvent) => {
    e?.preventDefault();
    const faltan = {
      usuario: nombre.trim() ? undefined : 'Escribe tu usuario.',
      contrasena: contrasena ? undefined : 'Escribe tu contraseña.',
    };
    setErrores(faltan);
    if (faltan.usuario || faltan.contrasena) return;
    setEnviando(true);
    setError(null);
    try {
      const yo = await ingresar(nombre.trim(), contrasena, perfil);
      navegar(desde ?? inicioDe(yo.perfil), { replace: true });
    } catch (err) {
      setError(err);
    } finally {
      setEnviando(false);
    }
  };

  return (
    <main className="ingreso">
      <section className="ingreso__hoja" aria-hidden="true">
        <Logo />
        <p style={{ fontFamily: 'var(--fuente-titulo)', fontSize: '1.25rem', position: 'relative' }}>Agenda de atenciones de los policlínicos</p>
      </section>
      <section className="ingreso__lado">
        <form className="tarjeta ingreso__tarjeta pila" onSubmit={(e) => enviar('TENS', e)} noValidate>
          <div>
            <h1>Ingresar</h1>
            <p className="texto-secundario texto-pequeno" style={{ marginTop: 6 }}>Cada perfil ve solo las funciones que le corresponden.</p>
          </div>
          <div className="campo">
            <label htmlFor="usuario">Usuario</label>
            <input id="usuario" className="control" autoComplete="username" value={nombre} onChange={(e) => setNombre(e.target.value)}
              aria-invalid={!!errores.usuario} aria-describedby={errores.usuario ? 'usuario-error' : undefined} />
            {errores.usuario && <span id="usuario-error" className="campo-error">{errores.usuario}</span>}
          </div>
          <div className="campo">
            <label htmlFor="contrasena">Contraseña</label>
            <input id="contrasena" className="control" type="password" autoComplete="current-password" value={contrasena}
              onChange={(e) => setContrasena(e.target.value)} aria-invalid={!!errores.contrasena}
              aria-describedby={errores.contrasena ? 'contrasena-error' : undefined} />
            {errores.contrasena && <span id="contrasena-error" className="campo-error">{errores.contrasena}</span>}
          </div>
          <div className="campo">
            <label htmlFor="policlinico">Policlínico</label>
            <input id="policlinico" className="control" readOnly value="Se asigna según tu usuario" aria-describedby="policlinico-ayuda" />
            <span id="policlinico-ayuda" className="campo-ayuda">La TENS/TONS trabaja en su policlínico base.</span>
          </div>
          <MensajeError error={error} />
          <button className="boton boton--ancho" type="submit" disabled={enviando}>Ingresar como TENS/TONS</button>
          <button className="boton boton--secundario boton--ancho" type="button" disabled={enviando} onClick={() => enviar('ADMINISTRACION')}>
            Ingresar como Administración
          </button>
        </form>
        <Link to="/" className="texto-fuerte" style={{ textDecoration: 'none' }}>← Volver al sitio de reserva de horas</Link>
      </section>
    </main>
  );
}
