package org.project.controller;

import org.project.dto.admin.MudarStatusRequest;
import org.project.dto.admin.PaginaPedidos;
import org.project.dto.admin.PedidoDetalhe;
import org.project.service.AdminPedidoService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * Pedidos no painel de admin. Tudo aqui exige ROLE_ADMIN (SecurityConfig) e passa pela
 * trava por Origin (AdminOriginFilter) — inclusive as leituras, porque as respostas têm
 * dados pessoais dos compradores.
 * Só recebe, chama o AdminPedidoService e responde.
 */
@RestController
@RequestMapping("/api/admin/pedidos")
public class AdminPedidoController {

    private final AdminPedidoService adminPedidoService;

    public AdminPedidoController(AdminPedidoService adminPedidoService) {
        this.adminPedidoService = adminPedidoService;
    }

    @GetMapping
    public ResponseEntity<PaginaPedidos> listar(@RequestParam(required = false) String aba,
                                                @RequestParam(required = false) String busca,
                                                @RequestParam(defaultValue = "0") int pagina,
                                                @RequestParam(defaultValue = "" + AdminPedidoService.TAMANHO_PADRAO) int tamanho) {
        return ResponseEntity.ok(adminPedidoService.listar(aba, busca, pagina, tamanho));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PedidoDetalhe> detalhe(@PathVariable int id) {
        return ResponseEntity.ok(adminPedidoService.detalhe(id));
    }

    @PutMapping("/{id}/pagamento")
    public ResponseEntity<PedidoDetalhe> mudarPagamento(@PathVariable int id,
                                                        @RequestBody MudarStatusRequest dados,
                                                        Authentication authentication) {
        // Quem mudou vem do token, nunca do corpo
        return ResponseEntity.ok(adminPedidoService.mudarPagamento(id, dados, authentication.getName()));
    }

    @PutMapping("/{id}/processamento")
    public ResponseEntity<PedidoDetalhe> mudarProcessamento(@PathVariable int id,
                                                            @RequestBody MudarStatusRequest dados,
                                                            Authentication authentication) {
        return ResponseEntity.ok(adminPedidoService.mudarProcessamento(id, dados, authentication.getName()));
    }
}
