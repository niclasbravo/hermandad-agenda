package cl.udd.hermandad.agenda.reportes.aplicacion;

import cl.udd.hermandad.agenda.reportes.dominio.CategoriaEtaria;
import cl.udd.hermandad.agenda.reportes.dominio.IConsultaReportes;
import cl.udd.hermandad.agenda.seguridad.aplicacion.AuditoriaService;
import cl.udd.hermandad.agenda.seguridad.dominio.AccionAuditoria;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@Transactional
public class CategoriaEtariaService {

    public record Datos(Integer id, String nombre, Integer edadDesde, Integer edadHasta) {
    }

    private final IConsultaReportes consultas;
    private final AuditoriaService auditoria;

    public CategoriaEtariaService(IConsultaReportes consultas, AuditoriaService auditoria) {
        this.consultas = consultas;
        this.auditoria = auditoria;
    }

    /** Reemplaza los tramos (D-13). */
    public List<CategoriaEtaria> guardar(List<Datos> datos) {
        Map<Integer, CategoriaEtaria> actuales = new HashMap<>();
        consultas.categorias().forEach(c -> actuales.put(c.getId(), c));
        Set<Integer> conservar = new HashSet<>();
        for (Datos d : datos) {
            if (d.id() != null && actuales.containsKey(d.id())) {
                actuales.get(d.id()).actualizar(d.nombre(), d.edadDesde(), d.edadHasta());
                consultas.guardarCategoria(actuales.get(d.id()));
                conservar.add(d.id());
            }
        }
        for (CategoriaEtaria c : actuales.values()) {
            if (!conservar.contains(c.getId())) {
                consultas.eliminarCategoria(c);
            }
        }
        for (Datos d : datos) {
            if (d.id() == null || !actuales.containsKey(d.id())) {
                consultas.guardarCategoria(new CategoriaEtaria(d.nombre(), d.edadDesde(), d.edadHasta()));
            }
        }
        auditoria.registrar(AccionAuditoria.MODIFICAR, "categoria_etaria", 0, datos.size() + " tramos");
        return consultas.categorias();
    }
}
