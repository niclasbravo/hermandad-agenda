package cl.udd.hermandad.agenda.integracion;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** El contexto arranca con ddl-auto=validate contra el esquema exacto de db/01 + db/02 (26 tablas, 5 disparadores). */
class ContextoTest extends PruebaIntegracion {

    @Test
    void elEsquemaEsElDelInformeYHibernateLoValida() {
        assertThat(contar("select count(*) from information_schema.tables where table_schema = 'hermandad_agenda'")).isEqualTo(26);
        assertThat(contar("select count(*) from perfil")).isEqualTo(2);
        assertThat(contar("select count(*) from plantilla_mensaje")).isEqualTo(6);
        assertThat(contar("select count(*) from parametro_sistema")).isEqualTo(7);
    }
}
