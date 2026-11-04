package org.project.pagamento;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.math.BigDecimal;
import java.util.List;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
record PreferenciaRequest(List<Item> items,
                          Payer payer,
                          BackUrls backUrls,
                          String autoReturn,
                          String notificationUrl,
                          String externalReference,
                          String statementDescriptor) {

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record Item(String id, String title, int quantity, String currencyId, BigDecimal unitPrice) {
    }

    /** Quem está pagando (só o e-mail: o Mercado Pago pede o resto na página dele). */
    record Payer(String email) {
    }

    /** Para onde o Mercado Pago devolve o cliente depois de pagar (Aula 5). */
    record BackUrls(String success, String pending, String failure) {
    }
}
