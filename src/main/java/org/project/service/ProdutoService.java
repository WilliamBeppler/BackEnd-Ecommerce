package org.project.service;

import org.project.dto.ProdutoRequest;
import org.project.model.Produto;
import org.project.repository.ProdutoRepository;
import org.project.service.excecao.NaoEncontradoException;
import org.project.service.excecao.RegraDeNegocioException;
import org.springframework.stereotype.Service;

import java.util.List;

/*** Regras do catálogo: vitrine, cadastro, edição e ativar/desativar.
 * As validações dos campos ficam no ValidadorProduto (testável sem banco).*/

@Service
public class ProdutoService {

    private final ProdutoRepository produtoRepository;

    public ProdutoService(ProdutoRepository produtoRepository) {
        this.produtoRepository = produtoRepository;
    }

    public List<Produto> listarAtivos() {
        return produtoRepository.findByAtivoTrue();
    }

    public List<Produto> listarTodos() {
        return produtoRepository.findAll();
    }

    public Produto cadastrar(ProdutoRequest dados) {
        validar(dados);
        Produto novo = new Produto();
        aplicar(dados, novo);
        return produtoRepository.save(novo);
    }

    public Produto editar(int id, ProdutoRequest dados) {
        // Primeiro o 404, depois a validação: mesma ordem de antes
        Produto produto = buscar(id);
        validar(dados);
        aplicar(dados, produto);
        return produtoRepository.save(produto);
    }

    public Produto desativar(int id) {
        return mudarAtivo(id, false);
    }

    public Produto reativar(int id) {
        return mudarAtivo(id, true);
    }

    private Produto mudarAtivo(int id, boolean ativo) {
        Produto produto = buscar(id);
        produto.setAtivo(ativo);
        return produtoRepository.save(produto);
    }

    private Produto buscar(int id) {
        return produtoRepository.findById(id)
                .orElseThrow(() -> new NaoEncontradoException("Erro: produto " + id + " não encontrado."));
    }

    private void validar(ProdutoRequest dados) {
        String erro = ValidadorProduto.validar(dados);
        if (erro != null) {
            throw new RegraDeNegocioException(erro);
        }
    }

    private void aplicar(ProdutoRequest dados, Produto produto) {
        produto.setNome(dados.nome().trim());
        produto.setPreco(dados.preco());
        produto.setCaminho_imagem(dados.caminhoImagem().trim());
    }
}
