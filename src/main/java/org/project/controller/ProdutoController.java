package org.project.controller;

import org.project.dto.ProdutoRequest;
import org.project.model.Produto;
import org.project.service.ProdutoService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Porta de entrada do catálogo. Só recebe, chama o ProdutoService e responde.
 * Quem pode o quê (público x ADMIN) está no SecurityConfig.
 */
@RestController
@RequestMapping("/api/produtos")
// CORS configurado num lugar só: SecurityConfig (origens vêm de CORS_ORIGINS)
public class ProdutoController {

    private final ProdutoService produtoService;

    public ProdutoController(ProdutoService produtoService) {
        this.produtoService = produtoService;
    }

    @GetMapping
    public List<Produto> listarAtivos() {
        return produtoService.listarAtivos();
    }

    @GetMapping("/admin")
    public List<Produto> listarTodos() {
        return produtoService.listarTodos();
    }

    @PostMapping
    public ResponseEntity<Produto> cadastrar(@RequestBody ProdutoRequest dados) {
        return ResponseEntity.status(HttpStatus.CREATED).body(produtoService.cadastrar(dados));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Produto> editar(@PathVariable int id, @RequestBody ProdutoRequest dados) {
        return ResponseEntity.ok(produtoService.editar(id, dados));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Produto> desativar(@PathVariable int id) {
        return ResponseEntity.ok(produtoService.desativar(id));
    }

    @PutMapping("/{id}/ativar")
    public ResponseEntity<Produto> reativar(@PathVariable int id) {
        return ResponseEntity.ok(produtoService.reativar(id));
    }
}
