package org.project.pagamento;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;

/**
 * Tudo que a integração com o Mercado Pago precisa saber, lido UMA vez quando o
 * Spring sobe e conferido na hora. Se algo estiver errado, a aplicação não sobe e
 * diz o que falta — melhor do que descobrir só quando um cliente tentar pagar.
 *
 * O token é um SEGREDO: nunca aparece em log, em toString() nem em mensagem de erro.
 */
@Component
public class MercadoPagoConfiguracao {

    private static final Logger log = LoggerFactory.getLogger(MercadoPagoConfiguracao.class);

    private final String accessToken;
    private final String webhookSecret;
    private final String apiUrl;
    private final String lojaUrl;

    public MercadoPagoConfiguracao(@Value("${app.mercadopago.access-token}") String accessToken,
                                   @Value("${app.mercadopago.webhook-secret}") String webhookSecret,
                                   @Value("${app.mercadopago.api-url}") String apiUrl,
                                   @Value("${app.loja.url}") String lojaUrl) {

        if (webhookSecret == null || webhookSecret.isBlank()) {
            throw new IllegalStateException("MP_WEBHOOK_SECRET está vazio: copie a assinatura secreta do painel (Webhooks) para o .env.");
        }
        this.webhookSecret = webhookSecret.trim();

        if (accessToken == null || accessToken.isBlank()) {
            throw new IllegalStateException("MP_ACCESS_TOKEN está vazio: copie o Access Token de TESTE para o .env.");
        }
        if (!accessToken.startsWith("APP_USR-") && !accessToken.startsWith("TEST-")) {
            throw new IllegalStateException("MP_ACCESS_TOKEN não parece um Access Token do Mercado Pago (deveria começar com APP_USR-).");
        }

        this.apiUrl = urlSemBarraFinal(apiUrl, "app.mercadopago.api-url");
        this.lojaUrl = urlSemBarraFinal(lojaUrl, "LOJA_URL");
        this.accessToken = accessToken;

        log.info("Mercado Pago configurado. API: {} | Loja (para as páginas de volta): {}", this.apiUrl, this.lojaUrl);
    }

    String getAccessToken() {
        return accessToken;
    }

    String getWebhookSecret() {
        return webhookSecret;
    }

    public String getApiUrl() {
        return apiUrl;
    }

    public String getLojaUrl() {
        return lojaUrl;
    }

    public boolean lojaEmEnderecoLocal() {
        String host = URI.create(lojaUrl).getHost();
        return "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host) || "[::1]".equals(host);
    }

    @Override
    public String toString() {
        return "MercadoPagoConfiguracao[apiUrl=" + apiUrl + ", lojaUrl=" + lojaUrl + ", accessToken=***, webhookSecret=***]";
    }

    private static String urlSemBarraFinal(String valor, String nome) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalStateException(nome + " está vazio.");
        }
        String url = valor.trim();
        try {
            URI uri = new URI(url);
            boolean httpOuHttps = "http".equals(uri.getScheme()) || "https".equals(uri.getScheme());
            if (!httpOuHttps || uri.getHost() == null) {
                throw new IllegalStateException(nome + " precisa ser um endereço http(s) válido.");
            }
        } catch (java.net.URISyntaxException e) {
            throw new IllegalStateException(nome + " não é um endereço válido.");
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
