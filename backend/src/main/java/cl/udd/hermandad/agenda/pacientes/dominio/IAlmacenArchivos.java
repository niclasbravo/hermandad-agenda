package cl.udd.hermandad.agenda.pacientes.dominio;

import java.io.InputStream;

/** Certificados fuera de la base (RF-41). Implementación local en disco; S3 queda como opcional. */
public interface IAlmacenArchivos {
    /** Guarda el contenido con un nombre aleatorio y devuelve la ruta relativa. */
    String guardar(byte[] contenido, String extension);
    InputStream abrir(String ruta);
}
