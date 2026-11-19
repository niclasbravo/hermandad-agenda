package cl.udd.hermandad.agenda.seguridad.aplicacion;

import cl.udd.hermandad.agenda.compartido.ErrorNegocio;
import cl.udd.hermandad.agenda.compartido.Reloj;
import cl.udd.hermandad.agenda.compartido.SesionActual;
import cl.udd.hermandad.agenda.seguridad.dominio.AccionAuditoria;
import cl.udd.hermandad.agenda.seguridad.dominio.IParametroRepository;
import cl.udd.hermandad.agenda.seguridad.dominio.ParametroSistema;
import cl.udd.hermandad.agenda.seguridad.dominio.TipoDato;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.*;

@Service
public class ParametroService {

    public static final String PORCENTAJE_CUPOS_WEB = "PORCENTAJE_CUPOS_WEB";
    public static final String TOPE_RESERVAS_WEB = "TOPE_RESERVAS_WEB";
    public static final String REFUERZOS_CANTIDAD = "REFUERZOS_CANTIDAD";
    public static final String REFUERZOS_HORAS_ANTES = "REFUERZOS_HORAS_ANTES";
    public static final String UMBRAL_INASISTENCIA = "UMBRAL_INASISTENCIA";
    public static final String RECORDATORIO_HORA = "RECORDATORIO_HORA";
    public static final String MENSAJE_REINTENTOS = "MENSAJE_REINTENTOS";

    /** Valores iniciales de producción (sección 9.1). */
    public static final List<ParametroSistema> INICIALES = List.of(
            new ParametroSistema(PORCENTAJE_CUPOS_WEB, null, TipoDato.PORCENTAJE,
                    "Porcentaje de cupos de cada bloque que se ofrecen en la reserva en línea (D-01). Vacío = 0 %."),
            new ParametroSistema(TOPE_RESERVAS_WEB, null, TipoDato.ENTERO,
                    "Máximo de horas en línea activas por paciente (D-02). Vacío = sin tope."),
            new ParametroSistema(REFUERZOS_CANTIDAD, null, TipoDato.ENTERO,
                    "Cantidad de refuerzos del recordatorio (D-04). Vacío = sin refuerzos."),
            new ParametroSistema(REFUERZOS_HORAS_ANTES, null, TipoDato.TEXTO,
                    "Horas antes de la cita en que sale cada refuerzo, separadas por coma (D-04). Ej.: 48,4"),
            new ParametroSistema(UMBRAL_INASISTENCIA, "3", TipoDato.ENTERO,
                    "Inasistencias en el período desde las que se considera inasistencia reiterada (RN-10, D-13)."),
            new ParametroSistema(RECORDATORIO_HORA, "10:00", TipoDato.TEXTO,
                    "Hora del día anterior a la cita en que sale el recordatorio."),
            new ParametroSistema(MENSAJE_REINTENTOS, "3", TipoDato.ENTERO,
                    "Intentos de envío de un mensaje antes de marcarlo como no enviado."));

    private final IParametroRepository repositorio;
    private final AuditoriaService auditoria;
    private final ApplicationEventPublisher eventos;
    private final Reloj reloj;

    public ParametroService(IParametroRepository repositorio, AuditoriaService auditoria,
                            ApplicationEventPublisher eventos, Reloj reloj) {
        this.repositorio = repositorio;
        this.auditoria = auditoria;
        this.eventos = eventos;
        this.reloj = reloj;
    }

    @Transactional
    public void asegurarIniciales() {
        for (ParametroSistema p : INICIALES) {
            if (repositorio.findById(p.getClave()).isEmpty()) {
                repositorio.save(new ParametroSistema(p.getClave(), p.getValor(), p.getTipoDato(), p.getDescripcion()));
            }
        }
    }

    @Transactional(readOnly = true)
    public List<ParametroSistema> listar() {
        return repositorio.findAllByOrderByClaveAsc();
    }

    @Transactional
    public List<ParametroSistema> actualizar(Map<String, String> valores) {
        Integer idUsuario = SesionActual.usuario().idUsuario();
        for (Map.Entry<String, String> e : valores.entrySet()) {
            ParametroSistema p = repositorio.findById(e.getKey())
                    .orElseThrow(() -> ErrorNegocio.validacion(e.getKey(), "Este parámetro no existe."));
            String anterior = p.getValor();
            p.cambiar(e.getValue(), idUsuario, reloj.ahora());
            if (!Objects.equals(anterior, p.getValor())) {
                repositorio.save(p);
                auditoria.registrar(AccionAuditoria.MODIFICAR, "parametro_sistema", 0,
                        p.getClave() + ": " + anterior + " → " + p.getValor());
                eventos.publishEvent(new ParametroCambiado(p.getClave(), anterior, p.getValor()));
            }
        }
        validarRefuerzos();
        return listar();
    }

    private void validarRefuerzos() {
        Optional<Integer> cantidad = entero(REFUERZOS_CANTIDAD);
        List<Integer> horas = refuerzosHorasAntes();
        if (cantidad.isPresent() && cantidad.get() > 0 && horas.size() < cantidad.get()) {
            throw ErrorNegocio.validacion(REFUERZOS_HORAS_ANTES,
                    "Indica las horas de cada refuerzo. Por ejemplo, para 2 refuerzos: 48,4");
        }
    }

    public Optional<String> valor(String clave) {
        return repositorio.findById(clave).map(ParametroSistema::getValor);
    }

    public Optional<Integer> entero(String clave) {
        return valor(clave).map(String::trim).map(Integer::parseInt);
    }

    public Optional<Integer> porcentajeCuposWeb() {
        return entero(PORCENTAJE_CUPOS_WEB);
    }

    public Optional<Integer> topeReservasWeb() {
        return entero(TOPE_RESERVAS_WEB);
    }

    public Optional<Integer> umbralInasistencia() {
        return entero(UMBRAL_INASISTENCIA);
    }

    public int mensajeReintentos() {
        return entero(MENSAJE_REINTENTOS).orElse(3);
    }

    public LocalTime recordatorioHora() {
        return valor(RECORDATORIO_HORA).map(v -> LocalTime.parse(v.length() == 4 ? "0" + v : v))
                .orElse(LocalTime.of(10, 0));
    }

    /** Horas antes de la cita para cada refuerzo, limitado a REFUERZOS_CANTIDAD. */
    public List<Integer> refuerzosHorasAntes() {
        int cantidad = entero(REFUERZOS_CANTIDAD).orElse(0);
        if (cantidad <= 0) {
            return List.of();
        }
        List<Integer> horas = new ArrayList<>();
        valor(REFUERZOS_HORAS_ANTES).ifPresent(v -> {
            for (String parte : v.split(",")) {
                if (!parte.isBlank()) {
                    horas.add(Integer.parseInt(parte.trim()));
                }
            }
        });
        return horas.subList(0, Math.min(cantidad, horas.size()));
    }
}
