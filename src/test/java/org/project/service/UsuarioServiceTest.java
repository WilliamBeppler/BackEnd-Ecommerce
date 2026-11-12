package org.project.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.project.dto.CadastroRequest;
import org.project.model.Usuario;
import org.project.repository.UsuarioRepository;
import org.project.security.LimiteTentativasLogin;
import org.project.service.excecao.LoginBloqueadoException;
import org.project.service.excecao.NaoAutenticadoException;
import org.project.service.excecao.RegraDeNegocioException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Cadastro e login, com o banco, o BCrypt e o limite de tentativas SIMULADOS pelo Mockito.*/

@ExtendWith(MockitoExtension.class)
class UsuarioServiceTest {

    private static final String IP = "10.0.0.1";

    @Mock UsuarioRepository usuarioRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock LimiteTentativasLogin limiteTentativas;

    @InjectMocks UsuarioService usuarioService;

    private static CadastroRequest cadastroValido() {
        return new CadastroRequest("  Maria Silva  ", "  maria@email.com ", "senhaForte1",
                "52998224725", " (48) 99999-9999 ", LocalDate.of(1990, 5, 20));
    }

    private static Usuario usuarioNoBanco() {
        Usuario u = new Usuario("Maria", LocalDate.of(1990, 5, 20), "48999999999",
                "529.982.247-25", "maria@email.com", "HASH_DO_BANCO");
        u.setId(7);
        return u;
    }

    //cadastro

    @Test
    void cadastroSalvaComSenhaCriptografadaCargoClienteECpfFormatado() {
        when(usuarioRepository.findByEmail("maria@email.com")).thenReturn(null);
        when(usuarioRepository.existsByCpf("529.982.247-25")).thenReturn(false);
        when(passwordEncoder.encode("senhaForte1")).thenReturn("HASH_BCRYPT");
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(chamada -> chamada.getArgument(0));

        usuarioService.cadastrar(cadastroValido());

        ArgumentCaptor<Usuario> salvo = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).save(salvo.capture());

        Usuario u = salvo.getValue();
        assertThat(u.getSenha()).isEqualTo("HASH_BCRYPT");
        assertThat(u.getRole()).isEqualTo("CLIENTE");         // ninguém se cadastra como ADMIN
        assertThat(u.getCpf()).isEqualTo("529.982.247-25");
        assertThat(u.getEmail()).isEqualTo("maria@email.com");
        assertThat(u.getNome()).isEqualTo("Maria Silva");
        assertThat(u.getTelefone()).isEqualTo("(48) 99999-9999");
    }

    @Test
    void cadastroInvalidoNemConsultaOBanco() {
        CadastroRequest semCpf = new CadastroRequest("Maria", "maria@email.com", "senhaForte1",
                "111.111.111-11", "48999999999", LocalDate.of(1990, 5, 20));

        assertThatThrownBy(() -> usuarioService.cadastrar(semCpf))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessage("Erro: informe um CPF válido.");

        verifyNoInteractions(usuarioRepository, passwordEncoder);
    }

    @Test
    void emailJaCadastradoEhRecusado() {
        when(usuarioRepository.findByEmail("maria@email.com")).thenReturn(usuarioNoBanco());

        assertThatThrownBy(() -> usuarioService.cadastrar(cadastroValido()))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessage("Erro: Este e-mail já está cadastrado na loja.");

        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void cpfJaCadastradoEhRecusadoMesmoDigitadoSemPontos() {

        when(usuarioRepository.findByEmail("maria@email.com")).thenReturn(null);
        when(usuarioRepository.existsByCpf("529.982.247-25")).thenReturn(true);

        assertThatThrownBy(() -> usuarioService.cadastrar(cadastroValido()))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessage("Erro: Este CPF já está cadastrado na loja.");

        verify(usuarioRepository, never()).save(any());
    }

    //login

    private void semBloqueio(String email) {
        when(limiteTentativas.tempoDeBloqueio(email, IP)).thenReturn(null);
    }

    @Test
    void emailOuSenhaVaziosNemContamComoTentativa() {
        assertThatThrownBy(() -> usuarioService.autenticar("  ", "senha", IP))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessage("Erro: informe e-mail e senha.");
        assertThatThrownBy(() -> usuarioService.autenticar("maria@email.com", null, IP))
                .isInstanceOf(RegraDeNegocioException.class);

        verifyNoInteractions(limiteTentativas, usuarioRepository, passwordEncoder);
    }

    @Test
    void loginCerto() {
        semBloqueio("maria@email.com");
        Usuario maria = usuarioNoBanco();
        when(usuarioRepository.findByEmail("maria@email.com")).thenReturn(maria);
        when(passwordEncoder.matches("senhaForte1", "HASH_DO_BANCO")).thenReturn(true);

        Usuario logado = usuarioService.autenticar("  maria@email.com ", "senhaForte1", IP);

        assertThat(logado).isSameAs(maria);
        verify(limiteTentativas).registrarSucesso("maria@email.com");
        verify(limiteTentativas, never()).registrarFalha(anyString(), anyString());
    }

    @Test
    void senhaErradaRegistraFalhaEDaMensagemGenerica() {
        semBloqueio("maria@email.com");
        when(usuarioRepository.findByEmail("maria@email.com")).thenReturn(usuarioNoBanco());
        when(passwordEncoder.matches("errada", "HASH_DO_BANCO")).thenReturn(false);

        assertThatThrownBy(() -> usuarioService.autenticar("maria@email.com", "errada", IP))
                .isInstanceOf(NaoAutenticadoException.class)
                .hasMessage(UsuarioService.MENSAGEM_LOGIN_INVALIDO);

        verify(limiteTentativas).registrarFalha("maria@email.com", IP);
    }

    @Test
    void emailInexistenteDaAMesmaMensagemERodaOBcryptMesmoAssim() {
        semBloqueio("ninguem@email.com");
        when(usuarioRepository.findByEmail("ninguem@email.com")).thenReturn(null);
        when(passwordEncoder.encode(anyString())).thenReturn("HASH_FICTICIO");

        assertThatThrownBy(() -> usuarioService.autenticar("ninguem@email.com", "qualquer", IP))
                .isInstanceOf(NaoAutenticadoException.class)
                .hasMessage(UsuarioService.MENSAGEM_LOGIN_INVALIDO);

        // A comparação acontece mesmo sem usuário: o tempo de resposta não denuncia quem tem conta
        verify(passwordEncoder).matches("qualquer", "HASH_FICTICIO");
        verify(limiteTentativas).registrarFalha("ninguem@email.com", IP);
    }

    @Test
    void loginBloqueadoNemConsultaOBancoNemRodaOBcrypt() {
        when(limiteTentativas.tempoDeBloqueio("maria@email.com", IP)).thenReturn(Duration.ofMinutes(10));

        assertThatThrownBy(() -> usuarioService.autenticar("maria@email.com", "senhaForte1", IP))
                .isInstanceOf(LoginBloqueadoException.class)
                .hasMessage("Erro: muitas tentativas de login. Tente novamente em 10 minuto(s).");

        verifyNoInteractions(usuarioRepository, passwordEncoder);
    }

    //usuário do token

    @Test
    void usuarioDoTokenQueNaoExisteMaisDa401() {
        when(usuarioRepository.findByEmail("apagado@email.com")).thenReturn(null);

        assertThatThrownBy(() -> usuarioService.buscarLogado("apagado@email.com"))
                .isInstanceOf(NaoAutenticadoException.class)
                .hasMessage("Erro: usuário do token não existe mais.");
    }
}
