package org.project.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class LimiteTentativasLoginTest {

    private static final String IP = "10.0.0.1";

    static class RelogioFalso extends Clock {
        private Instant agora = Instant.parse("2026-11-05T12:00:00Z");

        void avancar(Duration tempo) {
            agora = agora.plus(tempo);
        }

        @Override public Instant instant() { return agora; }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
    }

    private RelogioFalso relogio;
    private LimiteTentativasLogin limite;

    @BeforeEach
    void setUp() {
        relogio = new RelogioFalso();

        limite = new LimiteTentativasLogin(5, 20, Duration.ofMinutes(15), relogio);
    }

    private void errar(String email, int vezes) {
        for (int i = 0; i < vezes; i++) {
            limite.registrarFalha(email, IP);
        }
    }

    @Test
    void semFalhasNaoBloqueia() {
        assertThat(limite.tempoDeBloqueio("maria@email.com", IP)).isNull();
    }

    @Test
    void quatroFalhasAindaDeixaTentar() {
        errar("maria@email.com", 4);
        assertThat(limite.tempoDeBloqueio("maria@email.com", IP)).isNull();
    }

    @Test
    void quintaFalhaBloqueiaPor15Minutos() {
        errar("maria@email.com", 5);
        assertThat(limite.tempoDeBloqueio("maria@email.com", IP)).isEqualTo(Duration.ofMinutes(15));
    }

    @Test
    void comCatorzeMinutosAindaBloqueado_comQuinzeLibera() {
        errar("maria@email.com", 5);

        relogio.avancar(Duration.ofMinutes(14));
        assertThat(limite.tempoDeBloqueio("maria@email.com", IP)).isEqualTo(Duration.ofMinutes(1));

        relogio.avancar(Duration.ofMinutes(1));
        assertThat(limite.tempoDeBloqueio("maria@email.com", IP)).isNull();
    }

    @Test
    void maiusculasEEspacosNoEmailContamComoOMesmoEmail() {
        errar("Maria@Email.com", 3);
        errar("  maria@email.com ", 2);
        assertThat(limite.tempoDeBloqueio("MARIA@EMAIL.COM", IP)).isNotNull();
    }

    @Test
    void loginCertoZeraAsFalhasDoEmail() {
        errar("maria@email.com", 4);
        limite.registrarSucesso("maria@email.com");
        errar("maria@email.com", 1);
        assertThat(limite.tempoDeBloqueio("maria@email.com", IP)).isNull();
    }

    @Test
    void falhasAntigasVencemDepoisDaJanela() {
        errar("maria@email.com", 4);
        relogio.avancar(Duration.ofMinutes(15));
        errar("maria@email.com", 1);
        assertThat(limite.tempoDeBloqueio("maria@email.com", IP)).isNull();
    }

    @Test
    void bloqueioDeUmEmailNaoAfetaOutro() {
        errar("maria@email.com", 5);
        assertThat(limite.tempoDeBloqueio("joao@email.com", "10.0.0.2")).isNull();
    }

    @Test
    void vinteFalhasDoMesmoIpBloqueiamOIpParaQualquerEmail() {
        for (int i = 0; i < 20; i++) {
            limite.registrarFalha("vitima" + i + "@email.com", IP);
        }
        assertThat(limite.tempoDeBloqueio("outra@email.com", IP)).isEqualTo(Duration.ofMinutes(15));
        assertThat(limite.tempoDeBloqueio("outra@email.com", "10.0.0.99")).isNull();
    }

    @Test
    void loginCertoNaoZeraOContadorDoIp() {
        for (int i = 0; i < 19; i++) {
            limite.registrarFalha("vitima" + i + "@email.com", IP);
        }
        limite.registrarSucesso("vitima0@email.com");
        limite.registrarFalha("mais1@email.com", IP);
        assertThat(limite.tempoDeBloqueio("qualquer@email.com", IP)).isNotNull();
    }
}
