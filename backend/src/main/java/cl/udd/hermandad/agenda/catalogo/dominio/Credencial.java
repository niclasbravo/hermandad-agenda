package cl.udd.hermandad.agenda.catalogo.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/** RF-03. Credencial opcional del profesional (objeto de valor). */
@Embeddable
public class Credencial {

    @Column(name = "titulo_profesional", length = 120)
    private String tituloProfesional;

    @Column(name = "casa_estudios", length = 120)
    private String casaEstudios;

    @Column(name = "registro_prestador", length = 40)
    private String registroPrestador;

    protected Credencial() {
    }

    public Credencial(String tituloProfesional, String casaEstudios, String registroPrestador) {
        this.tituloProfesional = vacioANulo(tituloProfesional);
        this.casaEstudios = vacioANulo(casaEstudios);
        this.registroPrestador = vacioANulo(registroPrestador);
    }

    private static String vacioANulo(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    public String getTituloProfesional() { return tituloProfesional; }
    public String getCasaEstudios() { return casaEstudios; }
    public String getRegistroPrestador() { return registroPrestador; }
}
