package org.project.dto.admin;

/**
 * O que o painel manda para mudar um status. "status" é o nome do novo valor
 * (ex.: "PAGO", "PROCESSADO"); o código de rastreio só é usado no processamento.
 * Sem id (vem da URL) e sem e-mail do admin (vem do token).
 */
public record MudarStatusRequest(String status, String codigoRastreio) {
}
