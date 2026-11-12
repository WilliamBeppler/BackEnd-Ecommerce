package org.project.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private static final String SEGREDO = "chave-de-teste-com-mais-de-32-caracteres-123456";

    private JwtService jwt;

    private static JwtService criar(String segredo, long expiracao) {
        JwtService servico = new JwtService();

        ReflectionTestUtils.setField(servico, "secret", segredo);
        ReflectionTestUtils.setField(servico, "expiration", expiracao);
        return servico;
    }

    @BeforeEach
    void setUp() {
        jwt = criar(SEGREDO, 60_000);
    }

    @Test
    void tokenGeradoGuardaEmailECargo() {
        String token = jwt.gerarToken("maria@email.com", "CLIENTE");

        assertThat(jwt.tokenValido(token)).isTrue();
        assertThat(jwt.extrairEmail(token)).isEqualTo("maria@email.com");
        assertThat(jwt.extrairRole(token)).isEqualTo("CLIENTE");
    }

    @Test
    void trocarOCargoDentroDoTokenInvalidaAAssinatura() {

        String token = jwt.gerarToken("maria@email.com", "CLIENTE");
        String[] partes = token.split("\\.");

        String payloadFalso = Base64.getUrlEncoder().withoutPadding().encodeToString(
                "{\"sub\":\"maria@email.com\",\"role\":\"ADMIN\"}".getBytes(StandardCharsets.UTF_8));
        String tokenAdulterado = partes[0] + "." + payloadFalso + "." + partes[2];

        assertThat(jwt.tokenValido(tokenAdulterado)).isFalse();
    }

    @Test
    void tokenAssinadoComOutraChaveEhRecusado() {
        JwtService outroServidor = criar("outra-chave-secreta-tambem-com-32-caracteres!!", 60_000);
        String tokenDeFora = outroServidor.gerarToken("maria@email.com", "ADMIN");

        assertThat(jwt.tokenValido(tokenDeFora)).isFalse();
    }

    @Test
    void tokenSemAssinaturaEhRecusado() {

        String cabecalho = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"none\"}".getBytes(StandardCharsets.UTF_8));
        String corpo = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"sub\":\"maria@email.com\",\"role\":\"ADMIN\"}".getBytes(StandardCharsets.UTF_8));

        assertThat(jwt.tokenValido(cabecalho + "." + corpo + ".")).isFalse();
    }

    @Test
    void tokenVencidoEhRecusado() {
        JwtService jaVencido = criar(SEGREDO, -1_000); // validade no passado
        String token = jaVencido.gerarToken("maria@email.com", "CLIENTE");

        assertThat(jwt.tokenValido(token)).isFalse();
    }

    @Test
    void textoQualquerNaoEhToken() {
        assertThat(jwt.tokenValido("lixo")).isFalse();
        assertThat(jwt.tokenValido("")).isFalse();
    }
}
