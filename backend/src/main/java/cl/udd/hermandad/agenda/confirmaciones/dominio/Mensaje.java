package cl.udd.hermandad.agenda.confirmaciones.dominio;

import cl.udd.hermandad.agenda.citas.dominio.Cita;
import cl.udd.hermandad.agenda.pacientes.dominio.Paciente;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/** RF-19, RF-21, RF-38. Un registro por destinatario (D-07). Nunca se borra (D-17). */
@Entity
@Table(name = "mensaje")
public class Mensaje {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_mensaje")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_cita", nullable = false)
    private Cita cita;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "id_plantilla", nullable = false, columnDefinition = "smallint unsigned")
    private PlantillaMensaje plantilla;

    @Enumerated(EnumType.STRING)
    @Column(name = "destinatario", nullable = false)
    private Paciente.Destinatario destinatario;

    @Column(name = "telefono_destino", nullable = false, length = 20)
    private String telefonoDestino;

    @Enumerated(EnumType.STRING)
    @Column(name = "canal", nullable = false)
    private CanalMensaje canal = CanalMensaje.WHATSAPP;

    @Enumerated(EnumType.STRING)
    @Column(name = "enviado_por", nullable = false)
    private EnviadoPor enviadoPor;

    @Column(name = "id_usuario")
    private Integer idUsuario;

    @Column(name = "fecha_programada", nullable = false)
    private LocalDateTime fechaProgramada;

    @Column(name = "fecha_envio")
    private LocalDateTime fechaEnvio;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_entrega", nullable = false)
    private EstadoEntrega estadoEntrega = EstadoEntrega.PROGRAMADO;

    @Column(name = "id_externo", length = 100)
    private String idExterno;

    protected Mensaje() {
    }

    public Mensaje(Cita cita, PlantillaMensaje plantilla, Paciente.Destinatario destinatario, String telefonoDestino,
                   EnviadoPor enviadoPor, Integer idUsuario, LocalDateTime fechaProgramada) {
        if (enviadoPor == EnviadoPor.TENS && idUsuario == null) {
            throw new IllegalArgumentException("El reenvío de la TENS/TONS requiere el usuario");
        }
        this.cita = cita;
        this.plantilla = plantilla;
        this.destinatario = destinatario;
        this.telefonoDestino = telefonoDestino;
        this.enviadoPor = enviadoPor;
        this.idUsuario = idUsuario;
        this.fechaProgramada = fechaProgramada;
    }

    public void marcarEnviado(String idExterno, LocalDateTime ahora) {
        this.idExterno = idExterno;
        this.fechaEnvio = ahora;
        this.estadoEntrega = EstadoEntrega.ENVIADO;
    }

    /** Avisos de estado: no retrocede (LEIDO no vuelve a ENTREGADO). Devuelve true si cambió. */
    public boolean actualizarEstado(EstadoEntrega nuevo, LocalDateTime ahora) {
        if (nuevo == EstadoEntrega.FALLIDO) {
            if (estadoEntrega == EstadoEntrega.FALLIDO) {
                return false;
            }
            marcarFallido(ahora);
            return true;
        }
        if (estadoEntrega == EstadoEntrega.FALLIDO || nuevo.ordinal() <= estadoEntrega.ordinal()) {
            return false;
        }
        if (fechaEnvio == null) {
            fechaEnvio = ahora;
        }
        estadoEntrega = nuevo;
        return true;
    }

    public void marcarEntregado(LocalDateTime ahora) {
        actualizarEstado(EstadoEntrega.ENTREGADO, ahora);
    }

    /** El CHECK ck_mensaje_envio exige fecha_envio fuera de PROGRAMADO: se usa el momento del último intento. */
    public void marcarFallido(LocalDateTime ahora) {
        if (fechaEnvio == null) {
            fechaEnvio = ahora;
        }
        estadoEntrega = EstadoEntrega.FALLIDO;
    }

    /** Marca de id_externo para un mensaje que no salió porque se canceló (DI-06): así no se confunde con un envío fallido. */
    public static final String ID_CANCELADO = "CANCELADO";

    /**
     * Mensaje programado que ya no debe salir (cita anulada o recordatorio reemplazado): queda FALLIDO, que exige el
     * esquema, con id_externo = CANCELADO para mostrarlo como «cancelado» y no como «no enviado» (DI-06).
     */
    public void cancelar(LocalDateTime ahora) {
        marcarFallido(ahora);
        if (idExterno == null) {
            idExterno = ID_CANCELADO;
        }
    }

    public boolean isCancelado() {
        return estadoEntrega == EstadoEntrega.FALLIDO && ID_CANCELADO.equals(idExterno);
    }

    /** Estado para mostrar: CANCELADO no existe en el esquema, se deriva de la marca. */
    public String estadoVisible() {
        return isCancelado() ? "CANCELADO" : estadoEntrega.name();
    }

    public boolean estaProgramado() {
        return estadoEntrega == EstadoEntrega.PROGRAMADO;
    }

    public Long getId() { return id; }
    public Cita getCita() { return cita; }
    public PlantillaMensaje getPlantilla() { return plantilla; }
    public Paciente.Destinatario getDestinatario() { return destinatario; }
    public String getTelefonoDestino() { return telefonoDestino; }
    public CanalMensaje getCanal() { return canal; }
    public EnviadoPor getEnviadoPor() { return enviadoPor; }
    public Integer getIdUsuario() { return idUsuario; }
    public LocalDateTime getFechaProgramada() { return fechaProgramada; }
    public LocalDateTime getFechaEnvio() { return fechaEnvio; }
    public EstadoEntrega getEstadoEntrega() { return estadoEntrega; }
    public String getIdExterno() { return idExterno; }
}
