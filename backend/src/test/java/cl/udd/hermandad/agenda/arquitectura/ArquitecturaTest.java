package cl.udd.hermandad.agenda.arquitectura;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Reglas de dependencia (sección 3): dentro de cada módulo api → aplicacion → dominio; la infraestructura implementa
 * las interfaces del dominio; un módulo no usa la infraestructura de otro; el dominio no conoce Spring MVC, HTTP ni WhatsApp.
 */
class ArquitecturaTest {

    private static final String BASE = "cl.udd.hermandad.agenda";
    private static JavaClasses clases;

    @BeforeAll
    static void importar() {
        clases = new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS).importPackages(BASE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"catalogo", "agenda", "pacientes", "citas", "listaespera", "confirmaciones", "asistencia", "reportes", "seguridad"})
    void elDominioNoDependeDeOtrasCapas(String modulo) {
        String pkg = BASE + "." + modulo;
        ArchRule regla = noClasses().that().resideInAPackage(pkg + ".dominio..")
                .should().dependOnClassesThat().resideInAnyPackage(pkg + ".api..", pkg + ".aplicacion..", pkg + ".infraestructura..")
                .allowEmptyShould(true);
        regla.check(clases);
    }

    @ParameterizedTest
    @ValueSource(strings = {"catalogo", "agenda", "pacientes", "citas", "listaespera", "confirmaciones", "asistencia", "reportes", "seguridad"})
    void laAplicacionNoDependeDeLaApi(String modulo) {
        String pkg = BASE + "." + modulo;
        noClasses().that().resideInAPackage(pkg + ".aplicacion..")
                .should().dependOnClassesThat().resideInAPackage(pkg + ".api..")
                .allowEmptyShould(true).check(clases);
    }

    @Test
    void ningunDominioConoceSpringMvcHttpNiWhatsApp() {
        noClasses().that().resideInAPackage("..dominio..")
                .should().dependOnClassesThat().resideInAnyPackage("org.springframework.web..", "org.springframework.http..",
                        "jakarta.servlet..", "java.net.http..")
                .orShould().dependOnClassesThat().haveSimpleNameContaining("WhatsApp")
                .check(clases);
    }

    @ParameterizedTest
    @ValueSource(strings = {"catalogo", "agenda", "pacientes", "citas", "listaespera", "confirmaciones", "asistencia", "reportes", "seguridad"})
    void ningunModuloUsaLaInfraestructuraDeOtro(String modulo) {
        noClasses().that().resideOutsideOfPackage(BASE + "." + modulo + "..")
                .should().dependOnClassesThat().resideInAPackage(BASE + "." + modulo + ".infraestructura..")
                .check(clases);
    }

    @Test
    void lasCapasInternasNoDependenDeControladores() {
        noClasses().that().resideInAnyPackage("..dominio..", "..aplicacion..", "..infraestructura..")
                .should().dependOnClassesThat().resideInAPackage("..api..")
                .check(clases);
    }
}
