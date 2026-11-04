package org.project.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.project.dto.CadastroRequest;
import org.project.dto.LoginRequest;
import org.project.model.Usuario;
import org.project.security.CookieTokenService;
import org.project.security.JwtService;
import org.project.service.UsuarioService;
import org.project.service.excecao.RegraDeNegocioException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Porta de entrada de cadastro, login e sessão.
 * Só lida com HTTP: lê a requisição, chama o UsuarioService (as regras) e monta a
 * resposta — incluindo o cookie do token, que é assunto de HTTP e fica aqui.
 * Erros de negócio viram status HTTP no TratadorDeErros.
 */
@RestController
@RequestMapping("/api/usuarios")
public class UsuarioController {

    private final UsuarioService usuarioService;
    private final JwtService jwtService;
    private final CookieTokenService cookieTokenService;

    public UsuarioController(UsuarioService usuarioService, JwtService jwtService,
                             CookieTokenService cookieTokenService) {
        this.usuarioService = usuarioService;
        this.jwtService = jwtService;
        this.cookieTokenService = cookieTokenService;
    }

    @PostMapping("/registrar")
    public ResponseEntity<Map<String, Object>> registrarUsuario(@RequestBody CadastroRequest dados) {
        return respostaComSessao(usuarioService.cadastrar(dados));
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> loginUsuario(@RequestBody LoginRequest dadosLogin,
                                                            HttpServletRequest request) {
        if (dadosLogin == null) {
            throw new RegraDeNegocioException("Erro: informe e-mail e senha.");
        }

        String ip = request.getRemoteAddr();

        return respostaComSessao(usuarioService.autenticar(dadosLogin.email(), dadosLogin.senha(), ip));
    }

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> usuarioLogado(Authentication authentication) {
        return ResponseEntity.ok(dadosPublicos(usuarioService.buscarLogado(authentication.getName())));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookieTokenService.criarCookieExpirado().toString())
                .build();
    }

    private ResponseEntity<Map<String, Object>> respostaComSessao(Usuario usuario) {
        String token = jwtService.gerarToken(usuario.getEmail(), usuario.getRole());

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookieTokenService.criarCookie(token).toString())
                .body(dadosPublicos(usuario));
    }

    private Map<String, Object> dadosPublicos(Usuario usuario) {
        return Map.of(
                "id", usuario.getId(),
                "nome", usuario.getNome(),
                "email", usuario.getEmail(),
                "role", usuario.getRole()
        );
    }
}
