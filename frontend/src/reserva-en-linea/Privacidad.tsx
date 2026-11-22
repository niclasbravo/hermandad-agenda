import { Link } from 'react-router-dom';
import { CabeceraPublica } from './CabeceraPublica';

/**
 * Política de privacidad del sistema de agenda. La exige Meta para publicar la app de WhatsApp Business y responde a la
 * Ley 19.628 y a la Ley 21.719 (vigente desde el 01-12-2026), identificada como requisito legal en la factibilidad.
 * Es la política del prototipo académico: no reemplaza la política institucional de la Hermandad.
 */
export function Privacidad() {
  return (
    <div className="publico">
      <CabeceraPublica />
      <main className="seccion privacidad">
        <h1>Política de privacidad</h1>
        <p className="texto-secundario">Sistema de agendamiento y confirmación de atenciones · Última actualización: noviembre de 2026</p>

        <div className="aviso aviso--info">
          <span>
            Este sitio es un <strong>prototipo académico</strong> del curso Ingeniería de Software (Universidad del Desarrollo, 2026),
            desarrollado para los policlínicos del Instituto de Caridad Hermandad de Dolores. Funciona con <strong>datos ficticios</strong> de
            demostración y no reemplaza la política de privacidad oficial de la institución.
          </span>
        </div>

        <h2>1. Qué datos tratamos</h2>
        <ul>
          <li><strong>Identificación:</strong> tipo y número de documento (RUT o pasaporte), nombres, apellidos, fecha de nacimiento, género y nacionalidad.</li>
          <li><strong>Contacto:</strong> teléfono celular, dirección y, si se entrega, correo electrónico. Si el paciente es menor de edad, los datos del adulto responsable.</li>
          <li><strong>Atención:</strong> horas reservadas, asistencia, respuestas a los mensajes de confirmación y registro de llamadas de la TENS/TONS.</li>
          <li><strong>Requisitos de atención:</strong> certificados de FONASA y de inscripción en el CESFAM que el paciente sube al reservar en línea.</li>
        </ul>
        <p>No registramos diagnósticos ni información clínica.</p>

        <h2>2. Para qué los usamos</h2>
        <ul>
          <li>Reservar, modificar y anular horas en los policlínicos.</li>
          <li>Enviar por WhatsApp la confirmación de la hora, el recordatorio del día anterior y los avisos de anulación, y recibir la respuesta del paciente («Asistiré» o «No podré asistir»).</li>
          <li>Ofrecer los cupos liberados a las personas en lista de espera.</li>
          <li>Generar reportes de gestión agregados (ocupación, asistencia y demanda) sin identificar a las personas.</li>
        </ul>

        <h2>3. Mensajes de WhatsApp</h2>
        <p>
          Los mensajes se envían mediante <strong>WhatsApp Business Platform</strong> (Meta Platforms), que actúa como encargado del envío.
          Los mensajes contienen solo lo necesario para identificar la hora: nombre de pila, fecha, hora, policlínico, box, dirección y profesional.
          Al reservar, se informa al paciente que recibirá estos mensajes. Las respuestas a los botones vuelven al sistema para actualizar la cita.
        </p>

        <h2>4. Quién accede</h2>
        <p>
          Solo el personal autorizado de los policlínicos (TENS/TONS y Administración), con usuario y contraseña, y cada perfil ve únicamente
          las funciones que le corresponden. Cada creación, modificación, verificación y anulación queda registrada en una auditoría.
          No vendemos ni cedemos los datos a terceros.
        </p>

        <h2>5. Dónde se guardan y por cuánto tiempo</h2>
        <p>
          Los datos se guardan en un servidor en la nube con conexión cifrada (HTTPS). En este prototipo, el servidor está en Estados Unidos;
          para la operación real se evaluará un proveedor y una región conforme a la Ley 21.719 (transferencia internacional de datos).
          Los certificados se guardan fuera de la base de datos, en almacenamiento privado, y nunca en una dirección pública.
          Los datos se conservan mientras sean necesarios para la atención y según las normas aplicables.
        </p>

        <h2 id="eliminacion">6. Tus derechos y cómo pedir la eliminación de tus datos</h2>
        <p>
          Puedes solicitar el acceso, la rectificación, la supresión, la oposición o la portabilidad de tus datos personales.
          Para pedirlo, acércate o llama a tu policlínico e indica tu documento: la TENS/TONS registra la solicitud y se responde en
          los plazos legales. Si dejas de querer recibir mensajes de WhatsApp, también puedes indicarlo en tu policlínico.
        </p>

        <h2>7. Cambios a esta política</h2>
        <p>Si esta política cambia, se publicará la nueva versión en esta misma página con su fecha de actualización.</p>

        <p><Link to="/">← Volver al inicio</Link></p>
      </main>
    </div>
  );
}
