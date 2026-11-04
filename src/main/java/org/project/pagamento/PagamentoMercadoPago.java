package org.project.pagamento;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.math.BigDecimal;
import java.util.Set;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record PagamentoMercadoPago(Long id,
                                   String status,
                                   String statusDetail,
                                   String externalReference,
                                   BigDecimal transactionAmount,
                                   String currencyId,
                                   String paymentMethodId,
                                   String paymentTypeId,
                                   String dateApproved) {

    private static final Set<String> DINHEIRO_DEVOLVIDO = Set.of("refunded", "charged_back");

    public boolean aprovado() {
        return "approved".equals(status);
    }

    public boolean dinheiroDevolvido() {
        return DINHEIRO_DEVOLVIDO.contains(status);
    }

    /**
     * Como o cliente pagou, no formato que o pedido guarda.
     * No Mercado Pago, o PIX aparece como payment_method_id "pix" (tipo "bank_transfer").
     */
    public String formaDePagamento() {
        if ("pix".equals(paymentMethodId)) return "PIX";
        if ("credit_card".equals(paymentTypeId)) return "CARTAO_CREDITO";
        if ("debit_card".equals(paymentTypeId)) return "CARTAO_DEBITO";
        if ("ticket".equals(paymentTypeId)) return "BOLETO";
        return "MERCADO_PAGO";
    }
}
