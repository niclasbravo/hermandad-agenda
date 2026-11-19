package cl.udd.hermandad.agenda.compartido;

/** Perfiles de acceso (RNF-04). El nombre en la tabla perfil se traduce aquí. */
public enum PerfilUsuario {
    ADMINISTRACION("Administración", "ROLE_ADMINISTRACION"),
    TENS("TENS/TONS", "ROLE_TENS");

    private final String nombre;
    private final String rol;

    PerfilUsuario(String nombre, String rol) {
        this.nombre = nombre;
        this.rol = rol;
    }

    public String nombre() { return nombre; }
    public String rol() { return rol; }

    public static PerfilUsuario desdeNombre(String nombre) {
        for (PerfilUsuario p : values()) {
            if (p.nombre.equalsIgnoreCase(nombre) || p.name().equalsIgnoreCase(nombre)) {
                return p;
            }
        }
        throw new IllegalArgumentException("Perfil desconocido: " + nombre);
    }
}
