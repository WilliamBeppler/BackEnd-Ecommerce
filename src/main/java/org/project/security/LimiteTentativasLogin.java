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

/**
 * Limite de tentativas de login que FALHARAM, em dois contadores:
 *  - por e-mail: protege uma conta de alguém testando senhas, mesmo trocando de IP;
 *  - por IP: protege contra um computador testando muitas contas (listas de senhas vazadas).
 *
 * Ao atingir o limite dentro da janela, aquele e-mail/IP fica bloqueado pelo mesmo tempo.
 * O bloqueio vale para o e-mail DIGITADO, exista ele ou não: assim não revela quem é cliente.
 *
 * Fica em memória: reiniciar o backend zera tudo, e com mais de um servidor cada um conta
 * separado (nesse dia, trocar o mapa por Redis; o resto do código não muda).
 */
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

    /** Recebe o relógio de fora para os testes poderem "avançar o tempo" sem esperar. */
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

    // "Ana@X.com" e "ana@x.com" contam juntos; senão bastaria trocar letras para burlar o limite
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
        // compute() é atômico por chave: duas requisições ao mesmo tempo não perdem contagem
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

    // Só é alterado dentro do compute() (uma requisição por vez, por chave);
    // volatile garante que as leituras de fora vejam o valor mais recente.
    private static final class Contador {
        private final Instant inicioJanela;
        private volatile int falhas;
        private volatile Instant bloqueadoAte;

        private Contador(Instant inicioJanela) {
            this.inicioJanela = inicioJanela;
        }

        /** Acabou a janela sem bloqueio, ou acabou o bloqueio: pode recomeçar do zero. */
        private boolean venceu(Instant agora, Duration janela) {
            Instant fimDoBloqueio = bloqueadoAte;
            if (fimDoBloqueio != null) return !agora.isBefore(fimDoBloqueio);
            return !agora.isBefore(inicioJanela.plus(janela));
        }
    }
}
