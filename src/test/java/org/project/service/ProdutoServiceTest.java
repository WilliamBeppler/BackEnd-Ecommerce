package org.project.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.project.dto.ProdutoRequest;
import org.project.model.Produto;
import org.project.repository.ProdutoRepository;
import org.project.service.excecao.NaoEncontradoException;
import org.project.service.excecao.RegraDeNegocioException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Catálogo: cadastro, edição e desativar/reativar, com o repositório simulado. */

@ExtendWith(MockitoExtension.class)
class ProdutoServiceTest {

    @Mock ProdutoRepository produtoRepository;
    @InjectMocks ProdutoService produtoService;

    private static Produto pastel() {
        return new Produto(3, "Pastel", new BigDecimal("10.00"), "../imgs/img_pastelCarne.jpeg");
    }

    @Test
    void vitrineMostraSoOsAtivos() {
        when(produtoRepository.findByAtivoTrue()).thenReturn(List.of(pastel()));

        assertThat(produtoService.listarAtivos()).hasSize(1);
        verify(produtoRepository, never()).findAll();
    }

    @Test
    void cadastroSalvaOsCamposSemEspacosENasceAtivo() {
        when(produtoRepository.save(any(Produto.class))).thenAnswer(c -> c.getArgument(0));

        Produto novo = produtoService.cadastrar(
                new ProdutoRequest("  Pastel de Queijo ", new BigDecimal("11.90"), " ../imgs/img_pastelQueijo.jpeg "));

        assertThat(novo.getNome()).isEqualTo("Pastel de Queijo");
        assertThat(novo.getPreco()).isEqualByComparingTo("11.90");
        assertThat(novo.getCaminho_imagem()).isEqualTo("../imgs/img_pastelQueijo.jpeg");
        assertThat(novo.isAtivo()).isTrue();
    }

    @Test
    void cadastroInvalidoNaoSalva() {
        ProdutoRequest precoNegativo = new ProdutoRequest("Pastel", new BigDecimal("-1"), "../imgs/a.jpeg");

        assertThatThrownBy(() -> produtoService.cadastrar(precoNegativo))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessage("Erro: o preço deve ser maior que zero.");

        verify(produtoRepository, never()).save(any());
    }

    @Test
    void editarAlteraOProdutoExistente() {
        Produto existente = pastel();
        when(produtoRepository.findById(3)).thenReturn(Optional.of(existente));
        when(produtoRepository.save(existente)).thenReturn(existente);

        Produto editado = produtoService.editar(3,
                new ProdutoRequest("Pastel Grande", new BigDecimal("15.00"), "https://cdn.exemplo.com/p.jpg"));

        assertThat(editado.getId()).isEqualTo(3); // o id vem da URL, o mesmo registro
        assertThat(editado.getNome()).isEqualTo("Pastel Grande");
        assertThat(editado.getPreco()).isEqualByComparingTo("15.00");
    }

    @Test
    void editarProdutoInexistenteDa404AntesDeValidar() {
        when(produtoRepository.findById(99)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> produtoService.editar(99, new ProdutoRequest("", null, null)))
                .isInstanceOf(NaoEncontradoException.class)
                .hasMessage("Erro: produto 99 não encontrado.");
    }

    @Test
    void desativarNaoApagaSoMarcaComoInativo() {
        Produto existente = pastel();
        when(produtoRepository.findById(3)).thenReturn(Optional.of(existente));
        when(produtoRepository.save(any(Produto.class))).thenAnswer(c -> c.getArgument(0));

        produtoService.desativar(3);

        ArgumentCaptor<Produto> salvo = ArgumentCaptor.forClass(Produto.class);
        verify(produtoRepository).save(salvo.capture());
        assertThat(salvo.getValue().isAtivo()).isFalse();
        verify(produtoRepository, never()).delete(any());
        verify(produtoRepository, never()).deleteById(any());
    }

    @Test
    void reativarVoltaParaAVitrine() {
        Produto existente = pastel();
        existente.setAtivo(false);
        when(produtoRepository.findById(3)).thenReturn(Optional.of(existente));
        when(produtoRepository.save(existente)).thenReturn(existente);

        assertThat(produtoService.reativar(3).isAtivo()).isTrue();
    }

    @Test
    void desativarProdutoInexistenteDa404() {
        when(produtoRepository.findById(42)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> produtoService.desativar(42)).isInstanceOf(NaoEncontradoException.class);
    }
}
