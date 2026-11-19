package cl.udd.hermandad.agenda.pacientes.infraestructura;

import cl.udd.hermandad.agenda.pacientes.dominio.IAlmacenArchivos;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/** Guarda los certificados en un directorio no público, con nombre aleatorio (UUID). */
@Component
public class AlmacenArchivosLocal implements IAlmacenArchivos {

    private final Path raiz;

    public AlmacenArchivosLocal(@Value("${agenda.almacen.directorio}") String directorio) {
        this.raiz = Path.of(directorio).toAbsolutePath().normalize();
    }

    @Override
    public String guardar(byte[] contenido, String extension) {
        try {
            Files.createDirectories(raiz);
            String nombre = UUID.randomUUID() + (extension == null ? "" : "." + extension);
            Files.write(raiz.resolve(nombre), contenido);
            return nombre;
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo guardar el archivo", e);
        }
    }

    @Override
    public InputStream abrir(String ruta) {
        Path archivo = raiz.resolve(ruta).normalize();
        if (!archivo.startsWith(raiz)) {
            throw new IllegalArgumentException("Ruta no permitida");
        }
        try {
            return Files.newInputStream(archivo);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo abrir el archivo", e);
        }
    }
}
