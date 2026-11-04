package org.project.pagamento;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/**
 * O que aproveitamos da resposta do Mercado Pago ao criar a preferência.
 * A resposta tem dezenas de campos; ignoreUnknown faz o Jackson ler só estes três.
 *
 * initPoint: link de pagamento. sandboxInitPoint: link do ambiente de testes.
 * Qual usar com credenciais de teste é decidido no teste ao vivo da Aula 4.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record PreferenciaResposta(String id, String initPoint, String sandboxInitPoint) {
}
