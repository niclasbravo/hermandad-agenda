package cl.udd.hermandad.agenda.dominio;

import cl.udd.hermandad.agenda.compartido.ErrorNegocio;
import cl.udd.hermandad.agenda.pacientes.dominio.DocumentoIdentidad;
import cl.udd.hermandad.agenda.pacientes.dominio.TipoDocumento;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class DocumentoIdentidadTest {

    @ParameterizedTest
    @ValueSource(strings = {"12.345.678-5", "12345678-5", "123456785", " 12345678 - 5 "})
    void rutValidoConOSinPuntosNiGuion(String rut) {
        DocumentoIdentidad d = DocumentoIdentidad.de(TipoDocumento.RUT, rut);
        assertThat(d.getNumero()).isEqualTo("12345678-5");
        assertThat(d.formatear()).isEqualTo("12.345.678-5");
    }

    @ParameterizedTest
    @ValueSource(strings = {"11.223.344-K", "11223344-k", "8765432K"})
    void rutConK(String rut) {
        DocumentoIdentidad d = DocumentoIdentidad.de(TipoDocumento.RUT, rut);
        assertThat(d.getNumero()).endsWith("-K");
    }

    @ParameterizedTest
    @ValueSource(strings = {"12.345.678-4", "11223344-1", "abc", "", "5"})
    void rutInvalidoSeRechaza(String rut) {
        assertThatThrownBy(() -> DocumentoIdentidad.de(TipoDocumento.RUT, rut)).isInstanceOf(ErrorNegocio.class);
    }

    @Test
    void mensajeDeRutInvalido() {
        assertThatThrownBy(() -> DocumentoIdentidad.de(TipoDocumento.RUT, "12.345.678-4"))
                .hasMessage("El RUT no es válido. Revisa el número y el dígito verificador.");
    }

    @Test
    void pasaporteNoValidaDigitoVerificador() {
        DocumentoIdentidad d = DocumentoIdentidad.de(TipoDocumento.PASAPORTE, "ab 123456");
        assertThat(d.getNumero()).isEqualTo("AB123456");
        assertThat(d.formatear()).isEqualTo("AB123456");
        assertThat(DocumentoIdentidad.de(TipoDocumento.OTRO, "12345678-4").getNumero()).isEqualTo("12345678-4");
    }

    @Test
    void calculaDigitoVerificador() {
        assertThat(DocumentoIdentidad.calcularDv("12345678")).isEqualTo('5');
        assertThat(DocumentoIdentidad.calcularDv("11223344")).isEqualTo('K');
        assertThat(DocumentoIdentidad.calcularDv("10203040")).isEqualTo('0');
    }

    @Test
    void enmascaraSinRevelarElDocumentoCompleto() {
        assertThat(DocumentoIdentidad.de(TipoDocumento.RUT, "12345678-5").enmascarar()).isEqualTo("12.345.***-5");
        assertThat(DocumentoIdentidad.de(TipoDocumento.RUT, "9876543-3").enmascarar()).isEqualTo("9.876.***-3");
    }
}
