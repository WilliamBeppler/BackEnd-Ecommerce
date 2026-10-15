package org.project.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Set;

/**
 * Trava por Origin nas rotas que ALTERAM produtos (POST, PUT, DELETE em /api/produtos/**).
 *
 * O cookie do token é o mesmo na loja e no painel. Sem esta trava, um script malicioso
 * rodando na loja (XSS) conseguiria usar o cookie de um admin logado no mesmo navegador.
 * O cabeçalho Origin é escrito pelo NAVEGADOR (o JavaScript da página não consegue
 * alterá-lo), então só o painel (ADMIN_ORIGINS) passa.
 *
 * Isto NÃO substitui o hasRole("ADMIN"): o hasRole confere QUEM pede, esta trava confere
 * DE ONDE veio o pedido. Fora do navegador (curl, Postman) o Origin pode ser forjado, mas aí
 * não há cookie de admin para roubar.
 *
 * Não é @Component de propósito: o SecurityConfig cria e posiciona este filtro, para ele
 * não ser registrado duas vezes.
 */
public class AdminOriginFilter extends OncePerRequestFilter {

    private static final Set<String> METODOS_QUE_ALTERAM = Set.of("POST", "PUT", "PATCH", "DELETE");

    private final List<String> origensDoPainel;

    public AdminOriginFilter(List<String> origensDoPainel) {
        this.origensDoPainel = origensDoPainel;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String caminho = request.getServletPath();
        boolean rotaDeProdutos = caminho.equals("/api/produtos") || caminho.startsWith("/api/produtos/");
        return !rotaDeProdutos || !METODOS_QUE_ALTERAM.contains(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String origem = request.getHeader("Origin");

        // Sem Origin, ou de qualquer lugar que não seja o painel: recusa antes de qualquer coisa
        if (origem == null || !origensDoPainel.contains(origem)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("text/plain;charset=UTF-8");
            response.getWriter().write("Erro: alterações de produto só são aceitas pelo painel de administração.");
            return;
        }

        filterChain.doFilter(request, response);
    }
}
