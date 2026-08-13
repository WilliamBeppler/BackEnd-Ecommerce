package org.project.controller;

import org.project.model.Usuario;
import org.project.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/usuarios")
@CrossOrigin(origins = "*")
public class UsuarioController {

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired // Adicione isso!
    private PasswordEncoder passwordEncoder;

    @PostMapping("/registrar")
    public ResponseEntity<Object> registrarUsuario(@RequestBody Usuario novoUsuario) {

        // 1.Regra de Negócio: Verifica se o e-mail já existe no banco
        if (usuarioRepository.findByEmail(novoUsuario.getEmail()) != null) {
            return ResponseEntity.badRequest().body("Erro: Este e-mail já está cadastrado na loja.");
        }

        String senhaCriptografada = passwordEncoder.encode(novoUsuario.getSenha());
        novoUsuario.setSenha(senhaCriptografada);

        // 2.O comando .save() devolve o usuário com o ID preenchido pelo banco!
        Usuario usuarioSalvo = usuarioRepository.save(novoUsuario);

        // 3. Devolve o objeto inteiro (JSON) para o Front-End pegar o ID
        return ResponseEntity.ok(usuarioSalvo);
    }

    @PostMapping("/login")
    public ResponseEntity<Object> loginUsuario(@RequestBody Usuario dadosLogin) {

        // 1. O Repository vai no MySQL procurar alguém com o e-mail digitado
        Usuario usuarioNoBanco = usuarioRepository.findByEmail(dadosLogin.getEmail());

        // 2. Primeira barreira de defesa: O e-mail existe no cofre?
        if (usuarioNoBanco == null) {
            return ResponseEntity.status(401).body("Erro: E-mail não cadastrado.");
        }

        if (!passwordEncoder.matches(dadosLogin.getSenha(), usuarioNoBanco.getSenha())) {
            return ResponseEntity.status(401).body("Erro: Senha incorreta.");
        }

        return ResponseEntity.ok(usuarioNoBanco);

    }

}
