package org.project.controller;

import org.project.dto.MeuPedido;
import org.project.dto.PagamentoIniciado;
import org.project.dto.PedidoRequest;
import org.project.dto.SituacaoPedido;
import org.project.model.Pedido;
import org.project.service.PedidoService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Porta de entrada dos pedidos do cliente. Só recebe, chama o PedidoService e responde.
 * O e-mail do dono SEMPRE vem do token (authentication.getName()), nunca do corpo.
 */
@RestController
@RequestMapping("/api/pedidos")
public class PedidoController {

    private final PedidoService pedidoService;

    public PedidoController(PedidoService pedidoService) {
        this.pedidoService = pedidoService;
    }

    @PostMapping
    public ResponseEntity<PagamentoIniciado> criarPedido(@RequestBody PedidoRequest dados,
                                                         Authentication authentication) {
        // 1º salva o pedido (AGUARDANDO). 2º pede o link ao Mercado Pago, já fora do banco.
        Pedido pedidoSalvo = pedidoService.criar(authentication.getName(), dados);
        return responderPagamento(pedidoService.iniciarPagamento(pedidoSalvo));
    }

    @GetMapping("/meus")
    public ResponseEntity<List<MeuPedido>> meusPedidos(Authentication authentication) {
        return ResponseEntity.ok(pedidoService.meusPedidos(authentication.getName()));
    }


    @GetMapping("/{id}/situacao")
    public ResponseEntity<SituacaoPedido> situacao(@PathVariable int id, Authentication authentication) {
        return ResponseEntity.ok(pedidoService.situacao(authentication.getName(), id));
    }


    @PostMapping("/{id}/conferir-pagamento")
    public ResponseEntity<SituacaoPedido> conferirPagamento(@PathVariable int id,
                                                            @RequestParam("payment_id") String paymentId,
                                                            Authentication authentication) {
        return ResponseEntity.ok(pedidoService.conferirPagamento(authentication.getName(), id, paymentId));
    }


    @PostMapping("/{id}/pagamento")
    public ResponseEntity<PagamentoIniciado> pagarNovamente(@PathVariable int id, Authentication authentication) {
        return responderPagamento(pedidoService.pagarNovamente(authentication.getName(), id));
    }


    private ResponseEntity<PagamentoIniciado> responderPagamento(PagamentoIniciado resultado) {
        HttpStatus status = resultado.erro() == null ? HttpStatus.OK : HttpStatus.BAD_GATEWAY;
        return ResponseEntity.status(status).body(resultado);
    }
}
