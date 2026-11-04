package org.project.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Único lugar que monta o cookie do token.
 * Login, cadastro e logout usam esta classe, para os atributos nunca divergirem:
 * se o cookie de logout tiver atributos diferentes, o navegador não apaga o original.
 */
@Service
public class CookieTokenService {

    public static final String NOME_COOKIE = "token";

    private final JwtService jwtService;
    private final boolean secure;

    public CookieTokenService(JwtService jwtService,
                              @Value("${app.cookie.secure}") boolean secure) {
        this.jwtService = jwtService;
        this.secure = secure;
    }

    public ResponseCookie criarCookie(String token) {
        return montar(token, Duration.ofMillis(jwtService.getExpiration()));
    }

    public ResponseCookie criarCookieExpirado() {
        return montar("", Duration.ZERO);
    }

    private ResponseCookie montar(String valor, Duration validade) {
        return ResponseCookie.from(NOME_COOKIE, valor)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Strict")
                .path("/")
                .maxAge(validade)
                .build();
    }
}
