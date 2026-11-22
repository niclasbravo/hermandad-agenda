import { CheckCheck } from 'lucide-react';
import { fechaLargaConAno, hora } from '../../compartido/formato';
import type { Cita } from '../../compartido/tipos';

/**
 * Vista previa del mensaje de confirmación que recibe el paciente por WhatsApp. Reproduce el texto de la plantilla
 * aprobada en Meta `hermandad_confirmacion` (RedactorMensajes) con los datos de la cita recién reservada.
 * Es informativa: el envío real lo hace el backend.
 */
export function VistaWhatsApp({ cita, telefono }: { cita: Cita; telefono: string }) {
  const horaTexto = hora(cita.horaInicio).replace(/^0/, '');
  const ahora = new Date().toLocaleTimeString('es-CL', { hour: '2-digit', minute: '2-digit' });
  return (
    <aside className="vista-wa" aria-label={`Mensaje de confirmación que recibirá el paciente al ${telefono}`}>
      <div className="vista-wa__cabecera">
        <span className="vista-wa__avatar" aria-hidden="true">HD</span>
        <div>
          <div className="vista-wa__nombre">Hermandad de Dolores</div>
          <div className="vista-wa__estado">Confirmación automática · {telefono}</div>
        </div>
      </div>
      <div className="vista-wa__chat">
        <div className="vista-wa__burbuja">
          <p>Hola, {cita.nombrePila}. Su hora quedó reservada.</p>
          <p className="vista-wa__dato">{fechaLargaConAno(cita.fecha)}, {horaTexto}</p>
          <p>{cita.profesion} con {cita.profesional}</p>
          <p>Policlínico {cita.policlinico}, {cita.box}</p>
          <p>{cita.direccion}</p>
          <p>Si no puede asistir, avísenos para dar la hora a otra persona.</p>
          <span className="vista-wa__hora">{ahora} <CheckCheck size={14} aria-hidden="true" /></span>
        </div>
        <p className="vista-wa__nota">El día antes le llega el recordatorio con los botones «Asistiré» y «No podré asistir».</p>
      </div>
    </aside>
  );
}
