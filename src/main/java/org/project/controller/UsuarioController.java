package org.project.controller;

import org.project.dto.CadastroRequest;
import org.project.dto.LoginRequest;
import org.project.model.Usuario;
import org.project.repository.UsuarioRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.project.security.CookieTokenService;
import org.project.security.JwtService;
import org.project.security.LimiteTentativasLogin;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Map;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/usuarios")
public class UsuarioController {

    @Autowired
    private JwtService jwtService;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private CookieTokenService cookieTokenService;

    @Autowired
    private LimiteTentativasLogin limiteTentativas;

    @PostMapping("/registrar")
    public ResponseEntity<?> registrarUsuario(@RequestBody CadastroRequest dados) {

        String erro = validarCadastro(dados);
        if (erro != null) {
            return ResponseEntity.badRequest().body(erro);
        }

        String email = dados.email().trim();
        String cpf = formatarCpf(dados.cpf());

        if (usuarioRepository.findByEmail(email) != null) {
            return ResponseEntity.badRequest().body("Erro: Este e-mail já está cadastrado na loja.");
        }
        // Sem esta checagem, o UNIQUE do banco estouraria como erro 500
        if (usuarioRepository.existsByCpf(cpf)) {
            return ResponseEntity.badRequest().body("Erro: Este CPF já está cadastrado na loja.");
        }

        // O usuário é montado AQUI, do zero: o id nasce 0, então o save() sempre faz INSERT.
        // Antes, o JSON preenchia a entidade direto; um "id" de outra pessoa virava um
        // UPDATE na conta dela (troca de e-mail e senha = conta roubada).
        Usuario novoUsuario = new Usuario(
                dados.nome().trim(),
                dados.nascimento(),
                dados.telefone().trim(),
                cpf,
                email,
                passwordEncoder.encode(dados.senha())
        );
        novoUsuario.setRole("CLIENTE");

        Usuario usuarioSalvo = usuarioRepository.save(novoUsuario);

        // CORREÇÃO 1: já emite o token no cadastro.
        // Antes, quem se cadastrava ia direto pro checkout SEM token e tomava
        // 403 no POST /api/pedidos, porque essa rota exige .authenticated().
        // CORREÇÃO 2: devolve um Map, não a entidade inteira (a senha vazava no JSON).
        return respostaComSessao(usuarioSalvo);
    }

    @PostMapping("/login")
    public ResponseEntity<?> loginUsuario(@RequestBody LoginRequest dadosLogin, HttpServletRequest request) {

        if (dadosLogin == null || vazio(dadosLogin.email()) || vazio(dadosLogin.senha())) {
            return ResponseEntity.badRequest().body("Erro: informe e-mail e senha.");
        }

        String email = dadosLogin.email().trim();

        // IP de quem conectou. ATENÇÃO em produção: atrás de um proxy (Nginx, Cloudflare,
        // balanceador), todo mundo chegaria com o IP do proxy. Aí é preciso configurar
        // server.forward-headers-strategy confiando SÓ no seu proxy. Nunca ler o
        // X-Forwarded-For direto: qualquer um inventa esse cabeçalho e ganha um IP novo
        // a cada tentativa, o que anularia o limite por IP.
        String ip = request.getRemoteAddr();

        // Confere o bloqueio ANTES do BCrypt: quem está bloqueado nem gasta a comparação
        Duration espera = limiteTentativas.tempoDeBloqueio(email, ip);
        if (espera != null) {
            long minutos = Math.max(1, (espera.getSeconds() + 59) / 60);
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .header(HttpHeaders.RETRY_AFTER, String.valueOf(espera.getSeconds() + 1))
                    .body("Erro: muitas tentativas de login. Tente novamente em " + minutos + " minuto(s).");
        }

        Usuario usuarioNoBanco = usuarioRepository.findByEmail(email);

        // Sempre roda o BCrypt, mesmo sem usuário: se o "e-mail não existe" respondesse
        // mais rápido, o tempo de resposta denunciaria quais e-mails têm conta.
        String hashParaComparar = usuarioNoBanco != null ? usuarioNoBanco.getSenha() : hashFicticio();
        boolean senhaConfere = passwordEncoder.matches(dadosLogin.senha(), hashParaComparar);

        // Mesma mensagem nos dois casos: não revela se o e-mail é cliente da loja
        if (usuarioNoBanco == null || !senhaConfere) {
            limiteTentativas.registrarFalha(email, ip);
            return ResponseEntity.status(401).body(MENSAGEM_LOGIN_INVALIDO);
        }

        limiteTentativas.registrarSucesso(email);
        return respostaComSessao(usuarioNoBanco);
    }

    /** Quem está logado agora. O e-mail vem do token (cookie); o resto vem do banco. */
    @GetMapping("/me")
    public ResponseEntity<?> usuarioLogado(Authentication authentication) {
        Usuario usuario = usuarioRepository.findByEmail(authentication.getName());

        // Token válido, mas o usuário foi apagado do banco depois do login
        if (usuario == null) {
            return ResponseEntity.status(401).body("Erro: usuário do token não existe mais.");
        }

        // Cargo lido do banco: se alguém for rebaixado de ADMIN, o /me mostra na hora
        return ResponseEntity.ok(dadosPublicos(usuario));
    }

    /** Pública de propósito: precisa funcionar mesmo com o token já vencido. */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookieTokenService.criarCookieExpirado().toString())
                .build();
    }

    /**
     * Resposta única de autenticação — cadastro e login devolvem o mesmo formato.
     * O token vai SÓ no cookie HttpOnly; o corpo nunca carrega o token.
     */
    private ResponseEntity<Map<String, Object>> respostaComSessao(Usuario usuario) {
        String token = jwtService.gerarToken(usuario.getEmail(), usuario.getRole());

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookieTokenService.criarCookie(token).toString())
                .body(dadosPublicos(usuario));
    }

    // ---------- LOGIN ----------

    private static final String MENSAGEM_LOGIN_INVALIDO = "Erro: e-mail ou senha inválidos.";

    private volatile String hashFicticio;

    /** Hash BCrypt de uma senha qualquer, só para o login sem usuário levar o mesmo tempo. */
    private String hashFicticio() {
        if (hashFicticio == null) {
            hashFicticio = passwordEncoder.encode("senha-ficticia-so-para-igualar-o-tempo");
        }
        return hashFicticio;
    }

    // ---------- VALIDAÇÃO DO CADASTRO ----------
    // Limites iguais aos da tabela usuarios (nome 100, email 150, telefone 50).

    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    /** Devolve a mensagem do primeiro problema encontrado, ou null se estiver tudo certo. */
    private String validarCadastro(CadastroRequest dados) {
        if (dados == null) {
            return "Erro: dados do cadastro não enviados.";
        }

        if (vazio(dados.nome()) || dados.nome().trim().length() > 100) {
            return "Erro: informe seu nome (até 100 caracteres).";
        }

        if (vazio(dados.email()) || dados.email().trim().length() > 150
                || !EMAIL.matcher(dados.email().trim()).matches()) {
            return "Erro: informe um e-mail válido.";
        }

        // O BCrypt só considera os primeiros 72 bytes: acima disso, o resto seria ignorado em silêncio
        String senha = dados.senha() == null ? "" : dados.senha();
        if (senha.length() < 8) {
            return "Erro: a senha deve ter no mínimo 8 caracteres.";
        }
        if (senha.getBytes(StandardCharsets.UTF_8).length > 72) {
            return "Erro: a senha é longa demais (máximo de 72 caracteres).";
        }

        if (!cpfValido(dados.cpf())) {
            return "Erro: informe um CPF válido.";
        }

        String digitosTelefone = dados.telefone() == null ? "" : dados.telefone().replaceAll("\\D", "");
        if (digitosTelefone.length() < 10 || digitosTelefone.length() > 11 || dados.telefone().trim().length() > 50) {
            return "Erro: informe um telefone válido com DDD.";
        }

        LocalDate nascimento = dados.nascimento();
        if (nascimento == null || nascimento.isAfter(LocalDate.now())
                || nascimento.isBefore(LocalDate.now().minusYears(120))) {
            return "Erro: informe uma data de nascimento válida.";
        }

        return null;
    }

    /** Confere os dois dígitos verificadores do CPF. Aceita com ou sem pontuação. */
    private boolean cpfValido(String cpf) {
        if (cpf == null) return false;
        String d = cpf.replaceAll("\\D", "");
        if (d.length() != 11 || d.chars().distinct().count() == 1) {
            return false; // tamanho errado, ou 111.111.111-11 e parecidos
        }
        return d.charAt(9) == digitoVerificador(d, 9) && d.charAt(10) == digitoVerificador(d, 10);
    }

    private char digitoVerificador(String digitos, int quantidade) {
        int soma = 0;
        for (int i = 0; i < quantidade; i++) {
            soma += (digitos.charAt(i) - '0') * (quantidade + 1 - i);
        }
        int resto = (soma * 10) % 11;
        return (char) ('0' + (resto == 10 ? 0 : resto));
    }

    /** Guarda sempre no mesmo formato (000.000.000-00), para o UNIQUE do banco funcionar. */
    private String formatarCpf(String cpf) {
        String d = cpf.replaceAll("\\D", "");
        return d.substring(0, 3) + "." + d.substring(3, 6) + "." + d.substring(6, 9) + "-" + d.substring(9);
    }

    private boolean vazio(String texto) {
        return texto == null || texto.isBlank();
    }

    /** O que o front pode saber do usuário. Nunca senha, CPF ou telefone. */
    private Map<String, Object> dadosPublicos(Usuario usuario) {
        return Map.of(
                "id", usuario.getId(),
                "nome", usuario.getNome(),
                "email", usuario.getEmail(),
                "role", usuario.getRole()
        );
    }
}