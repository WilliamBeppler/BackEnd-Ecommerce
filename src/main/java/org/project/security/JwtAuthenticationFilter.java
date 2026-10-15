package org.project.security;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        // O token chega no cookie HttpOnly que o login criou (o JS da página não o enxerga)
        String token = lerTokenDoCookie(request);

        // Se não veio crachá nenhum, segue em frente (a rota pública decide se libera)
        if (token == null) {
            filterChain.doFilter(request, response);
            return;
        }

        if (jwtService.tokenValido(token)) {
            String email = jwtService.extrairEmail(token);
            String role = jwtService.extrairRole(token);

            // Cria a "autoridade" no formato que o Spring espera: ROLE_ADMIN, ROLE_CLIENTE...
            var autoridade = new SimpleGrantedAuthority("ROLE_" + role);

            var auth = new UsernamePasswordAuthenticationToken(
                    email, null, List.of(autoridade));

            // "Carimba" essa requisição como autenticada
            SecurityContextHolder.getContext().setAuthentication(auth);
        }

        filterChain.doFilter(request, response);
    }

    private String lerTokenDoCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;

        return Arrays.stream(cookies)
                .filter(cookie -> CookieTokenService.NOME_COOKIE.equals(cookie.getName()))
                .map(Cookie::getValue)
                .filter(valor -> !valor.isBlank())
                .findFirst()
                .orElse(null);
    }
}
