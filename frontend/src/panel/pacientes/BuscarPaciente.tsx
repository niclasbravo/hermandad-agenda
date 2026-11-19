import { useEffect, useState, type FormEvent } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { Search, UserPlus } from 'lucide-react';
import { api, consulta, ErrorDeApi } from '../../compartido/api';
import { MensajeError } from '../../compartido/componentes';
import { formatearRut, rutValido } from '../../compartido/rut';
import { useSesion } from '../../compartido/sesion';
import type { Paciente } from '../../compartido/tipos';
import { Cabecera } from '../DisenoPanel';

/** M-03 Buscar por RUT (222:5141) y RUT no registrado (222:6356). RF-09. */
export function BuscarPaciente() {
  const [params, setParams] = useSearchParams();
  const { usuario } = useSesion();
  const [valor, setValor] = useState(params.get('documento') ?? '');
  const [noEncontrado, setNoEncontrado] = useState<string | null>(null);
  const [error, setError] = useState<unknown>(null);
  const [buscando, setBuscando] = useState(false);
  const navegar = useNavigate();

  const buscar = async (doc: string) => {
    setNoEncontrado(null);
    setError(null);
    if (!doc.trim()) { setError(new ErrorDeApi({ estado: 400, codigo: 'V', mensaje: 'Escribe el RUT o el documento del paciente.', detalles: {} })); return; }
    setBuscando(true);
    try {
      const p = await api.get<Paciente>('/api/pacientes' + consulta({ documento: doc.trim() }));
      navegar(`/pacientes/${p.id}`);
    } catch (e) {
      if (e instanceof ErrorDeApi && e.estado === 404) setNoEncontrado(rutValido(doc) ? formatearRut(doc) : doc.trim());
      else setError(e);
    } finally {
      setBuscando(false);
    }
  };

  useEffect(() => {
    const d = params.get('documento');
    if (d) { setValor(d); buscar(d); }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [params]);

  const enviar = (e: FormEvent) => { e.preventDefault(); setParams({ documento: valor }); };

  return (
    <>
      <Cabecera titulo="Pacientes" subtitulo="Identifica al paciente antes de reservar o actualizar sus datos" buscador={false} />
      <div className="cuerpo">
        <form className="tarjeta tarjeta--padding pila" style={{ maxWidth: 640 }} onSubmit={enviar} role="search">
          <div className="campo">
            <label htmlFor="rut-buscar" style={{ fontSize: '1rem' }}>RUT del paciente</label>
            <div className="fila" style={{ flexWrap: 'nowrap' }}>
              <input id="rut-buscar" className="control" style={{ fontSize: '1.125rem' }} value={valor} onChange={(e) => setValor(e.target.value)} placeholder="12.345.678-5" autoFocus />
              <button className="boton" type="submit" disabled={buscando}><Search size={20} aria-hidden="true" /> Buscar</button>
            </div>
            <span className="campo-ayuda">Escribe el RUT con o sin puntos y guion. Si no está registrado, podrás registrarlo.</span>
          </div>
          <MensajeError error={error} />
        </form>
        {noEncontrado && (
          <div className="tarjeta tarjeta--padding pila" style={{ maxWidth: 640 }} role="status">
            <h2>No hay un paciente registrado con el RUT {noEncontrado}</h2>
            <p className="texto-secundario">Revisa que el RUT esté bien escrito. Si es correcto, regístralo para poder reservarle una hora.</p>
            {usuario?.perfil === 'TENS' && (
              <Link className="boton" style={{ alignSelf: 'flex-start' }} to={`/pacientes/nuevo?documento=${encodeURIComponent(noEncontrado)}`}>
                <UserPlus size={20} aria-hidden="true" /> Registrar paciente
              </Link>
            )}
          </div>
        )}
      </div>
    </>
  );
}
