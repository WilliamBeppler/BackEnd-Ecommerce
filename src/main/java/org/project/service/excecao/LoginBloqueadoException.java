package org.project.service.excecao;

import java.time.Duration;

/**
 * Muitas tentativas de login erradas: o e-mail ou o IP está bloqueado por um tempo.
 * Leva o tempo que falta, para o TratadorDeErros montar a resposta 429 com Retry-After.
 */
public class LoginBloqueadoException extends RuntimeException {

    private final Duration espera;

    public LoginBloqueadoException(Duration espera) {
        super("Erro: muitas tentativas de login. Tente novamente em " + minutos(espera) + " minuto(s).");
        this.espera = espera;
    }

    public Duration getEspera() {
        return espera;
    }

    private static long minutos(Duration espera) {
        return Math.max(1, (espera.getSeconds() + 59) / 60);
    }
}
