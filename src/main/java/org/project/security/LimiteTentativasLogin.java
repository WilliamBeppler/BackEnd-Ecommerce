package org.project.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class LimiteTentativasLogin {

    private final int maximoPorEmail;
    private final int maximoPorIp;
    private final Duration janela;
    private final Clock relogio;

    private final Map<String, Contador> porEmail = new ConcurrentHashMap<>();
    private final Map<String, Contador> porIp = new ConcurrentHashMap<>();
    private final AtomicInteger operacoesDesdeLimpeza = new AtomicInteger();

    @Autowired
    public LimiteTentativasLogin(@Value("${app.login.max-falhas-por-email:5}") int maximoPorEmail,
                                 @Value("${app.login.max-falhas-por-ip:20}") int maximoPorIp,
                                 @Value("${app.login.janela-minutos:15}") long janelaMinutos) {
        this(maximoPorEmail, maximoPorIp, Duration.ofMinutes(janelaMinutos), Clock.systemUTC());
    }

    public LimiteTentativasLogin(int maximoPorEmail, int maximoPorIp, Duration janela, Clock relogio) {
        this.maximoPorEmail = maximoPorEmail;
        this.maximoPorIp = maximoPorIp;
        this.janela = janela;
        this.relogio = relogio;
    }

    /** Quanto tempo falta de bloqueio para este e-mail ou IP; null se pode tentar. */
    public Duration tempoDeBloqueio(String email, String ip) {
        Duration esperaEmail = tempoRestante(porEmail, chaveEmail(email));
        Duration esperaIp = tempoRestante(porIp, ip);
        if (esperaEmail == null) return esperaIp;
        if (esperaIp == null) return esperaEmail;
        return esperaEmail.compareTo(esperaIp) >= 0 ? esperaEmail : esperaIp;
    }

    public void registrarFalha(String email, String ip) {
        somarFalha(porEmail, chaveEmail(email), maximoPorEmail);
        somarFalha(porIp, ip, maximoPorIp);
        limparDeTempoEmTempo();
    }

    /** Login certo zera o contador do e-mail (o do IP continua: ele mede o computador). */
    public void registrarSucesso(String email) {
        porEmail.remove(chaveEmail(email));
    }

    private String chaveEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private Duration tempoRestante(Map<String, Contador> mapa, String chave) {
        Contador contador = mapa.get(chave);
        if (contador == null) return null;
        Instant agora = relogio.instant();
        Instant bloqueadoAte = contador.bloqueadoAte;
        if (bloqueadoAte != null && agora.isBefore(bloqueadoAte)) {
            return Duration.between(agora, bloqueadoAte);
        }
        return null;
    }

    private void somarFalha(Map<String, Contador> mapa, String chave, int maximo) {
        Instant agora = relogio.instant();
        mapa.compute(chave, (k, contador) -> {
            if (contador == null || contador.venceu(agora, janela)) {
                contador = new Contador(agora);
            }
            contador.falhas++;
            if (contador.falhas >= maximo) {
                contador.bloqueadoAte = agora.plus(janela);
            }
            return contador;
        });
    }

    // Remove contadores vencidos para a memória não crescer sem limite
    private void limparDeTempoEmTempo() {
        if (operacoesDesdeLimpeza.incrementAndGet() < 200) return;
        operacoesDesdeLimpeza.set(0);
        Instant agora = relogio.instant();
        porEmail.values().removeIf(c -> c.venceu(agora, janela));
        porIp.values().removeIf(c -> c.venceu(agora, janela));
    }

    private static final class Contador {
        private final Instant inicioJanela;
        private volatile int falhas;
        private volatile Instant bloqueadoAte;

        private Contador(Instant inicioJanela) {
            this.inicioJanela = inicioJanela;
        }

        private boolean venceu(Instant agora, Duration janela) {
            Instant fimDoBloqueio = bloqueadoAte;
            if (fimDoBloqueio != null) return !agora.isBefore(fimDoBloqueio);
            return !agora.isBefore(inicioJanela.plus(janela));
        }
    }
}
