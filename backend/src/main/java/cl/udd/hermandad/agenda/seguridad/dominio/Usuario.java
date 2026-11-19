package cl.udd.hermandad.agenda.seguridad.dominio;

import cl.udd.hermandad.agenda.catalogo.dominio.Policlinico;
import cl.udd.hermandad.agenda.compartido.PerfilUsuario;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Set;

@Entity
@Table(name = "usuario")
public class Usuario {

    private static final Set<Operacion> PERMISOS_ADMINISTRACION = EnumSet.of(
            Operacion.MANTENER_CATALOGO, Operacion.MANTENER_BLOQUES, Operacion.BLOQUEAR_CUPOS,
            Operacion.CONFIGURAR_PARAMETROS, Operacion.VER_AGENDA_CUPOS, Operacion.VER_PACIENTES,
            Operacion.VER_AGENDA_DIA, Operacion.VER_REPORTES_GESTION, Operacion.VER_INASISTENCIA_REITERADA);

    private static final Set<Operacion> PERMISOS_TENS = EnumSet.of(
            Operacion.VER_AGENDA_CUPOS, Operacion.VER_PACIENTES, Operacion.EDITAR_PACIENTES, Operacion.RESERVAR,
            Operacion.VER_AGENDA_DIA, Operacion.REGISTRAR_ASISTENCIA, Operacion.VER_INASISTENCIA_REITERADA);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_usuario")
    private Integer id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "id_perfil", nullable = false, columnDefinition = "tinyint unsigned")
    private Perfil perfil;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "id_policlinico", columnDefinition = "smallint unsigned")
    private Policlinico policlinico;

    @Column(name = "nombre_completo", nullable = false, length = 120)
    private String nombreCompleto;

    @Column(name = "nombre_usuario", nullable = false, length = 40)
    private String nombreUsuario;

    @Column(name = "hash_contrasena", nullable = false, length = 255)
    private String hashContrasena;

    @Column(name = "activo", nullable = false)
    private boolean activo = true;

    @Column(name = "fecha_creacion", nullable = false, insertable = false, updatable = false)
    private LocalDateTime fechaCreacion;

    protected Usuario() {
    }

    /** Regla: la TENS/TONS tiene policlínico base y la Administración no. */
    public Usuario(Perfil perfil, Policlinico policlinico, String nombreCompleto, String nombreUsuario, String hash) {
        PerfilUsuario tipo = perfil.tipo();
        if (tipo == PerfilUsuario.TENS && policlinico == null) {
            throw new IllegalArgumentException("La TENS/TONS necesita un policlínico base");
        }
        if (tipo == PerfilUsuario.ADMINISTRACION && policlinico != null) {
            throw new IllegalArgumentException("La Administración no tiene policlínico base");
        }
        this.perfil = perfil;
        this.policlinico = policlinico;
        this.nombreCompleto = nombreCompleto;
        this.nombreUsuario = nombreUsuario;
        this.hashContrasena = hash;
    }

    public boolean tienePermiso(Operacion operacion) {
        return switch (perfil.tipo()) {
            case ADMINISTRACION -> PERMISOS_ADMINISTRACION.contains(operacion);
            case TENS -> PERMISOS_TENS.contains(operacion);
        };
    }

    public Integer getId() { return id; }
    public Perfil getPerfil() { return perfil; }
    public Policlinico getPoliclinico() { return policlinico; }
    public String getNombreCompleto() { return nombreCompleto; }
    public String getNombreUsuario() { return nombreUsuario; }
    public String getHashContrasena() { return hashContrasena; }
    public boolean isActivo() { return activo; }
}
