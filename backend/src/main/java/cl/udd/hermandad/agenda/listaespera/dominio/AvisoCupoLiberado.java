package cl.udd.hermandad.agenda.listaespera.dominio;

import cl.udd.hermandad.agenda.agenda.dominio.Cupo;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/** RF-26. Aviso a la TENS/TONS del policlínico; el sistema no asigna el cupo solo. */
@Entity
@Table(name = "aviso_cupo_liberado")
public class AvisoCupoLiberado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_aviso")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_cupo", nullable = false)
    private Cupo cupo;

    @Column(name = "id_cita_anulada", nullable = false)
    private Long idCitaAnulada;

    @Column(name = "fecha_hora", nullable = false)
    private LocalDateTime fechaHora;

    @Column(name = "atendido", nullable = false)
    private boolean atendido;

    @Column(name = "id_usuario")
    private Integer idUsuario;

    protected AvisoCupoLiberado() {
    }

    public AvisoCupoLiberado(Cupo cupo, Long idCitaAnulada, LocalDateTime ahora) {
        this.cupo = cupo;
        this.idCitaAnulada = idCitaAnulada;
        this.fechaHora = ahora;
    }

    public void atender(Integer idUsuario) {
        if (idUsuario == null) {
            throw new IllegalArgumentException("Atender un aviso requiere el usuario");
        }
        this.atendido = true;
        this.idUsuario = idUsuario;
    }

    public Integer getId() { return id; }
    public Cupo getCupo() { return cupo; }
    public Long getIdCitaAnulada() { return idCitaAnulada; }
    public LocalDateTime getFechaHora() { return fechaHora; }
    public boolean isAtendido() { return atendido; }
    public Integer getIdUsuario() { return idUsuario; }
}
