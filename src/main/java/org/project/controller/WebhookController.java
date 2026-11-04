package org.project.controller;

import org.project.pagamento.ConfirmacaoPagamento;
import org.project.pagamento.ConfirmacaoPagamento.ResultadoAviso;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * A porta por onde o Mercado Pago avisa que um pagamento mudou (webhook).
 *
 * Esta rota é PÚBLICA (SecurityConfig): quem chama é o servidor do Mercado Pago, que não
 * tem o nosso cookie. Por isso a segurança dela não vem do login, e sim de duas coisas:
 *  1. a ASSINATURA do aviso, conferida antes de tudo;
 *  2. a consulta à API do Mercado Pago: o corpo do aviso nunca é usado para decidir nada.
 *
 * Formato do aviso: POST /api/pagamentos/webhook?data.id=123456&type=payment
 *   cabeçalhos x-signature (ts=...,v1=...) e x-request-id; corpo JSON com action, data.id, type...
 */
@RestController
@RequestMapping("/api/pagamentos")
public class WebhookController {

    private final ConfirmacaoPagamento confirmacao;

    public WebhookController(ConfirmacaoPagamento confirmacao) {
        this.confirmacao = confirmacao;
    }

    @PostMapping("/webhook")
    public ResponseEntity<Void> receber(@RequestHeader(value = "x-signature", required = false) String xSignature,
                                        @RequestHeader(value = "x-request-id", required = false) String xRequestId,
                                        @RequestParam(value = "data.id", required = false) String dataIdDaUrl,
                                        @RequestParam(value = "type", required = false) String tipoDaUrl,
                                        @RequestBody(required = false) Map<String, Object> corpo) {


        String dataId = dataIdDaUrl != null ? dataIdDaUrl : idDoCorpo(corpo);
        String tipo = tipoDaUrl != null ? tipoDaUrl : (corpo != null ? String.valueOf(corpo.get("type")) : null);

        ResultadoAviso resultado = confirmacao.receberAviso(xSignature, xRequestId, dataId, tipo);

        return switch (resultado) {
            case ACEITO -> ResponseEntity.ok().build();
            case ASSINATURA_INVALIDA -> ResponseEntity.status(401).build();
            case ERRO_TEMPORARIO -> ResponseEntity.status(503).build();
        };
    }

    private String idDoCorpo(Map<String, Object> corpo) {
        if (corpo != null && corpo.get("data") instanceof Map<?, ?> data && data.get("id") != null) {
            return String.valueOf(data.get("id"));
        }
        return null;
    }
}
