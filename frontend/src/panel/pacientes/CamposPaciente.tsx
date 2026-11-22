import type { FieldErrors, UseFormRegister, UseFormWatch } from 'react-hook-form';
import { MessageCircle } from 'lucide-react';
import { edad } from '../../compartido/formato';
import { GENEROS, RELACIONES, type DatosFormulario } from './esquemaPaciente';
import { Etiqueta, Obligatorio } from '../../compartido/componentes';

interface Props {
  register: UseFormRegister<DatosFormulario>;
  errors: FieldErrors<DatosFormulario>;
  watch: UseFormWatch<DatosFormulario>;
  documentoBloqueado?: boolean;
  enLinea?: boolean;
}

function Error({ m, id }: { m?: string; id: string }) {
  return m ? <span id={id} className="campo-error">{m}</span> : null;
}

/** Campos de datos de ingreso (RF-10, RF-12). Los obligatorios faltantes se marcan en rojo. */
export function CamposPaciente({ register, errors, watch, documentoBloqueado, enLinea }: Props) {
  const nacimiento = watch('fechaNacimiento');
  const menor = nacimiento ? edad(nacimiento) < 18 : false;
  const conAdulto = watch('conAdulto');
  const campo = (nombre: keyof DatosFormulario, etiqueta: string, props: Record<string, unknown> = {}) => {
    const e = errors[nombre]?.message as string | undefined;
    return (
      <div className="campo">
        <label htmlFor={'f-' + nombre}><Etiqueta texto={etiqueta} /></label>
        <input id={'f-' + nombre} className="control" {...register(nombre)} aria-invalid={!!e} aria-describedby={e ? 'e-' + nombre : undefined} {...props} />
        <Error m={e} id={'e-' + nombre} />
      </div>
    );
  };
  const ea = errors.adulto;
  const campoAdulto = (nombre: 'numeroDocumento' | 'nombreCompleto' | 'telefono', etiqueta: string, props: Record<string, unknown> = {}) => {
    const e = ea?.[nombre]?.message;
    return (
      <div className="campo">
        <label htmlFor={'fa-' + nombre}><Etiqueta texto={etiqueta} /></label>
        <input id={'fa-' + nombre} className="control" {...register(`adulto.${nombre}` as const)} aria-invalid={!!e} aria-describedby={e ? 'ea-' + nombre : undefined} {...props} />
        <Error m={e} id={'ea-' + nombre} />
      </div>
    );
  };
  return (
    <div className="pila">
      <section className="tarjeta tarjeta--padding pila">
        <h2>Datos del paciente</h2>
        {!enLinea && (
          <div className="grilla-3">
            <div className="campo">
              <label htmlFor="f-tipoDocumento">Tipo de documento <Obligatorio /></label>
              <select id="f-tipoDocumento" className="control" {...register('tipoDocumento')} disabled={documentoBloqueado}>
                <option value="RUT">RUT</option><option value="PASAPORTE">Pasaporte</option><option value="OTRO">Otro documento</option>
              </select>
            </div>
            {campo('numeroDocumento', 'Número de documento *', { readOnly: documentoBloqueado, placeholder: '12.345.678-5' })}
            {campo('fechaNacimiento', 'Fecha de nacimiento *', { type: 'date' })}
          </div>
        )}
        <div className="grilla-2">
          {campo('nombres', 'Nombres *', { autoComplete: 'given-name' })}
          {campo('apellidos', 'Apellidos *', { autoComplete: 'family-name' })}
        </div>
        <div className="grilla-2">
          {campo('telefonoCelular', 'Celular *', { placeholder: '+56 9 1234 5678', inputMode: 'tel', autoComplete: 'tel' })}
          {campo('correo', 'Correo (opcional)', { type: 'email', autoComplete: 'email' })}
        </div>
        {campo('direccion', 'Dirección *', { autoComplete: 'street-address' })}
        <div className="grilla-2">
          <div className="campo">
            <label htmlFor="f-genero">Género <Obligatorio /></label>
            <select id="f-genero" className="control" {...register('genero')} aria-invalid={!!errors.genero}>
              <option value="">Elige una opción</option>
              {GENEROS.map((g) => <option key={g}>{g}</option>)}
            </select>
            <Error m={errors.genero?.message} id="e-genero" />
          </div>
          {campo('nacionalidad', 'Nacionalidad *')}
        </div>
        <div className="aviso aviso--info">
          <MessageCircle size={20} aria-hidden="true" style={{ flexShrink: 0, marginTop: 2 }} />
          <span>Te enviaremos la confirmación y el recordatorio de tu hora por WhatsApp a este celular.</span>
        </div>
      </section>
      <section className="tarjeta tarjeta--padding pila">
        <h2>Adulto responsable</h2>
        {menor ? (
          <div className="aviso aviso--alerta">La persona es menor de edad: completa los datos de su adulto responsable.</div>
        ) : (
          <label className="casilla"><input type="checkbox" {...register('conAdulto')} /> Un familiar gestiona las horas de este paciente</label>
        )}
        {(menor || conAdulto) && (
          <>
            <div className="grilla-3">
              <div className="campo">
                <label htmlFor="fa-tipo">Tipo de documento</label>
                <select id="fa-tipo" className="control" {...register('adulto.tipoDocumento')}>
                  <option value="RUT">RUT</option><option value="PASAPORTE">Pasaporte</option><option value="OTRO">Otro documento</option>
                </select>
              </div>
              {campoAdulto('numeroDocumento', 'Documento *')}
              <div className="campo">
                <label htmlFor="fa-relacion">Relación con el paciente <Obligatorio /></label>
                <select id="fa-relacion" className="control" {...register('adulto.relacion')} aria-invalid={!!ea?.relacion}>
                  <option value="">Elige una opción</option>
                  {RELACIONES.map((r) => <option key={r} value={r}>{r.charAt(0).toUpperCase() + r.slice(1)}</option>)}
                </select>
                <Error m={ea?.relacion?.message} id="ea-relacion" />
              </div>
            </div>
            <div className="grilla-2">
              {campoAdulto('nombreCompleto', 'Nombre completo *')}
              {campoAdulto('telefono', 'Teléfono *', { inputMode: 'tel', placeholder: '+56 9 1234 5678' })}
            </div>
            <p className="texto-secundario texto-pequeno">Los mensajes por WhatsApp se envían al paciente y a su adulto responsable.</p>
          </>
        )}
      </section>
    </div>
  );
}
