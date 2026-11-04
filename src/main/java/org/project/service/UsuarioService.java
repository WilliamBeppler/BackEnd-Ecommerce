package org.project.service;

import org.project.dto.CadastroRequest;
import org.project.model.Usuario;
import org.project.repository.UsuarioRepository;
import org.project.security.LimiteTentativasLogin;
import org.project.service.excecao.LoginBloqueadoException;
import org.project.service.excecao.NaoAutenticadoException;
import org.project.service.excecao.RegraDeNegocioException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;

/*** Regras de clientes: cadastro, login e "quem está logado".* Não sabe o que é HTTP, cookie ou token: recebe dados
 * simples (e-mail, senha, IP)
 * e devolve o Usuario, ou lança uma exceção de negócio.*/

@Service
public class UsuarioService {

    static final String MENSAGEM_LOGIN_INVALIDO = "Erro: e-mail ou senha inválidos.";

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final LimiteTentativasLogin limiteTentativas;

    private volatile String hashFicticio;

    public UsuarioService(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder,
                          LimiteTentativasLogin limiteTentativas) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.limiteTentativas = limiteTentativas;
    }

    public Usuario cadastrar(CadastroRequest dados) {
        String erro = ValidadorCadastro.validar(dados);
        if (erro != null) {
            throw new RegraDeNegocioException(erro);
        }

        String email = dados.email().trim();
        String cpf = ValidadorCadastro.formatarCpf(dados.cpf());

        if (usuarioRepository.findByEmail(email) != null) {
            throw new RegraDeNegocioException("Erro: Este e-mail já está cadastrado na loja.");
        }
        // Sem esta checagem, o UNIQUE do banco estouraria como erro 500
        if (usuarioRepository.existsByCpf(cpf)) {
            throw new RegraDeNegocioException("Erro: Este CPF já está cadastrado na loja.");
        }

        Usuario novoUsuario = new Usuario(
                dados.nome().trim(),
                dados.nascimento(),
                dados.telefone().trim(),
                cpf,
                email,
                passwordEncoder.encode(dados.senha())
        );
        novoUsuario.setRole("CLIENTE");

        return usuarioRepository.save(novoUsuario);
    }

    public Usuario autenticar(String emailDigitado, String senha, String ip) {
        if (vazio(emailDigitado) || vazio(senha)) {
            throw new RegraDeNegocioException("Erro: informe e-mail e senha.");
        }
        String email = emailDigitado.trim();

        // Confere o bloqueio ANTES do BCrypt: quem está bloqueado nem gasta a comparação

        Duration espera = limiteTentativas.tempoDeBloqueio(email, ip);
        if (espera != null) {
            throw new LoginBloqueadoException(espera);
        }

        Usuario usuarioNoBanco = usuarioRepository.findByEmail(email);

        // Sempre roda o BCrypt, mesmo sem usuário: se o "e-mail não existe" respondesse
        // mais rápido, o tempo de resposta denunciaria quais e-mails têm conta.

        String hashParaComparar = usuarioNoBanco != null ? usuarioNoBanco.getSenha() : hashFicticio();
        boolean senhaConfere = passwordEncoder.matches(senha, hashParaComparar);

        if (usuarioNoBanco == null || !senhaConfere) {
            limiteTentativas.registrarFalha(email, ip);
            throw new NaoAutenticadoException(MENSAGEM_LOGIN_INVALIDO);
        }

        limiteTentativas.registrarSucesso(email);
        return usuarioNoBanco;
    }


    public Usuario buscarLogado(String emailDoToken) {
        Usuario usuario = usuarioRepository.findByEmail(emailDoToken);
        if (usuario == null) {
            throw new NaoAutenticadoException("Erro: usuário do token não existe mais.");
        }
        return usuario;
    }

    private String hashFicticio() {
        if (hashFicticio == null) {
            hashFicticio = passwordEncoder.encode("senha-ficticia-so-para-igualar-o-tempo");
        }
        return hashFicticio;
    }

    private static boolean vazio(String texto) {
        return texto == null || texto.isBlank();
    }
}
