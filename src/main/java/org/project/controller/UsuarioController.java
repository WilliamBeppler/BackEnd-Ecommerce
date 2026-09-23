package org.project.controller;

import org.project.model.Usuario;
import org.project.repository.UsuarioRepository;
import org.project.security.JwtService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/usuarios")
public class UsuarioController {

    @Autowired
    private JwtService jwtService;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @PostMapping("/registrar")
    public ResponseEntity<Object> registrarUsuario(@RequestBody Usuario novoUsuario) {

        if (usuarioRepository.findByEmail(novoUsuario.getEmail()) != null) {
            return ResponseEntity.badRequest().body("Erro: Este e-mail já está cadastrado na loja.");
        }

        novoUsuario.setRole("CLIENTE");

        String senhaCriptografada = passwordEncoder.encode(novoUsuario.getSenha());
        novoUsuario.setSenha(senhaCriptografada);

        Usuario usuarioSalvo = usuarioRepository.save(novoUsuario);

        // CORREÇÃO 1: já emite o token no cadastro.
        // Antes, quem se cadastrava ia direto pro checkout SEM token e tomava
        // 403 no POST /api/pedidos, porque essa rota exige .authenticated().
        String token = jwtService.gerarToken(usuarioSalvo.getEmail(), usuarioSalvo.getRole());

        // CORREÇÃO 2: devolve um Map, não a entidade inteira.
        // Antes o JSON de resposta incluía o campo 'senha' — o hash BCrypt do
        // usuário ia parar no navegador dele.
        return ResponseEntity.ok(montarRespostaAuth(usuarioSalvo, token));
    }

    @PostMapping("/login")
    public ResponseEntity<?> loginUsuario(@RequestBody Usuario dadosLogin) {

        Usuario usuarioNoBanco = usuarioRepository.findByEmail(dadosLogin.getEmail());

        if (usuarioNoBanco == null) {
            return ResponseEntity.status(401).body("Erro: E-mail não cadastrado.");
        }

        if (!passwordEncoder.matches(dadosLogin.getSenha(), usuarioNoBanco.getSenha())) {
            return ResponseEntity.status(401).body("Erro: Senha incorreta.");
        }

        String token = jwtService.gerarToken(usuarioNoBanco.getEmail(), usuarioNoBanco.getRole());

        // CORREÇÃO 3 (o bug principal): inclui o 'id' na resposta.
        // Sem ele o front não tinha como gravar idUsuarioLogado, e o checkout
        // achava que ninguém estava logado na hora de pagar.
        return ResponseEntity.ok(montarRespostaAuth(usuarioNoBanco, token));
    }

    /** Resposta única de autenticação — cadastro e login devolvem o mesmo formato. */
    private Map<String, Object> montarRespostaAuth(Usuario usuario, String token) {
        return Map.of(
                "id", usuario.getId(),
                "nome", usuario.getNome(),
                "email", usuario.getEmail(),
                "role", usuario.getRole(),
                "token", token
        );
    }
}