package org.project.controller;

import org.project.dto.ProdutoRequest;
import org.project.model.Produto;
import org.project.repository.ProdutoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/produtos")
// CORS configurado num lugar só: SecurityConfig (origens vêm de CORS_ORIGINS)
public class ProdutoController {

    // Limites iguais aos da tabela: nome VARCHAR(100), preco DECIMAL(10,2), caminho_imagem VARCHAR(255)
    private static final int NOME_MAXIMO = 100;
    private static final int IMAGEM_MAXIMO = 255;
    private static final BigDecimal PRECO_MAXIMO = new BigDecimal("99999999.99");

    // Imagens que já estão na pasta imgs da loja: "../imgs/foto.jpeg" ou "/imgs/foto.jpeg".
    // Só letras, números, _ e -, sem ".." no nome (não deixa sair da pasta imgs).
    private static final Pattern IMAGEM_LOCAL = Pattern.compile(
            "^(\\.\\./|/)imgs/[\\p{L}\\p{N}_-]+(\\.[\\p{L}\\p{N}_-]+)*\\.(jpe?g|png|webp)$",
            Pattern.CASE_INSENSITIVE);

    @Autowired
    private ProdutoRepository produtoRepository;

    /** Vitrine pública: só os produtos à venda. */
    @GetMapping
    public List<Produto> listarAtivos() {
        // Spring busca a lista no banco de dados e o Jackson converte tudo em JSON automaticamente.
        return produtoRepository.findByAtivoTrue();
    }

    /** Painel (só ADMIN, ver SecurityConfig): todos, inclusive os desativados. */
    @GetMapping("/admin")
    public List<Produto> listarTodos() {
        return produtoRepository.findAll();
    }

    @PostMapping
    public ResponseEntity<?> cadastrar(@RequestBody ProdutoRequest dados) {
        String erro = validar(dados);
        if (erro != null) {
            return ResponseEntity.badRequest().body(erro);
        }

        Produto novo = new Produto();
        aplicar(dados, novo);
        return ResponseEntity.status(HttpStatus.CREATED).body(produtoRepository.save(novo));
    }

    /** O id vem SÓ da URL; o corpo (ProdutoRequest) nem tem campo de id. */
    @PutMapping("/{id}")
    public ResponseEntity<?> editar(@PathVariable int id, @RequestBody ProdutoRequest dados) {
        Produto produto = produtoRepository.findById(id).orElse(null);
        if (produto == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Erro: produto " + id + " não encontrado.");
        }

        String erro = validar(dados);
        if (erro != null) {
            return ResponseEntity.badRequest().body(erro);
        }

        aplicar(dados, produto);
        return ResponseEntity.ok(produtoRepository.save(produto));
    }

    /** "Apagar" = desativar. Some da vitrine, mas os pedidos antigos continuam íntegros. */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> desativar(@PathVariable int id) {
        return mudarAtivo(id, false);
    }

    @PutMapping("/{id}/ativar")
    public ResponseEntity<?> reativar(@PathVariable int id) {
        return mudarAtivo(id, true);
    }

    private ResponseEntity<?> mudarAtivo(int id, boolean ativo) {
        Produto produto = produtoRepository.findById(id).orElse(null);
        if (produto == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Erro: produto " + id + " não encontrado.");
        }
        produto.setAtivo(ativo);
        return ResponseEntity.ok(produtoRepository.save(produto));
    }

    private void aplicar(ProdutoRequest dados, Produto produto) {
        produto.setNome(dados.nome().trim());
        produto.setPreco(dados.preco());
        produto.setCaminho_imagem(dados.caminhoImagem().trim());
    }

    /** Devolve a mensagem do primeiro problema encontrado, ou null se estiver tudo certo. */
    private String validar(ProdutoRequest dados) {
        if (dados == null) {
            return "Erro: dados do produto não enviados.";
        }

        String nome = dados.nome() == null ? "" : dados.nome().trim();
        if (nome.isEmpty()) {
            return "Erro: o nome do produto é obrigatório.";
        }
        if (nome.length() > NOME_MAXIMO) {
            return "Erro: o nome pode ter no máximo " + NOME_MAXIMO + " caracteres.";
        }

        BigDecimal preco = dados.preco();
        if (preco == null || preco.compareTo(BigDecimal.ZERO) <= 0) {
            return "Erro: o preço deve ser maior que zero.";
        }
        if (preco.stripTrailingZeros().scale() > 2) {
            return "Erro: o preço pode ter no máximo 2 casas decimais.";
        }
        if (preco.compareTo(PRECO_MAXIMO) > 0) {
            return "Erro: o preço máximo é 99999999.99.";
        }

        String imagem = dados.caminhoImagem() == null ? "" : dados.caminhoImagem().trim();
        if (imagem.isEmpty()) {
            return "Erro: a imagem do produto é obrigatória.";
        }
        if (imagem.length() > IMAGEM_MAXIMO) {
            return "Erro: o endereço da imagem pode ter no máximo " + IMAGEM_MAXIMO + " caracteres.";
        }
        if (!imagemValida(imagem)) {
            return "Erro: a imagem deve ser um endereço https:// ou um arquivo da pasta imgs (ex.: ../imgs/foto.jpeg).";
        }

        return null;
    }

    // Aceita só https:// com domínio, ou um arquivo da pasta imgs da loja.
    // Barra "javascript:", "data:", "http://" e caminhos que tentam sair da pasta.
    private boolean imagemValida(String imagem) {
        if (IMAGEM_LOCAL.matcher(imagem).matches()) {
            return true;
        }
        try {
            URI uri = new URI(imagem);
            return "https".equalsIgnoreCase(uri.getScheme())
                    && uri.getHost() != null
                    && !uri.getHost().isBlank();
        } catch (Exception e) {
            return false; // espaço, caractere inválido etc.
        }
    }
}
