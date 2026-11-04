package org.project.pagamento;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Confere se um aviso (webhook) veio MESMO do Mercado Pago.
 *
 * Como funciona: o Mercado Pago e o servidor conhecem o mesmo SEGREDO.
 * Para cada aviso, o Mercado Pago monta um texto ("manifesto") com
 * dados do aviso, calcula um HMAC-SHA256 desse texto com o segredo e manda o resultado no
 * cabeçalho x-signature. Refaz a mesma conta: se der igual, quem mandou conhece o
 * segredo, ou seja, é o Mercado Pago. Quem não conhece o segredo não consegue forjar.
 */

public final class AssinaturaWebhook {

    private AssinaturaWebhook() {
    }

    public static boolean valida(String xSignature, String xRequestId, String dataId, String segredo) {
        if (xSignature == null || xSignature.isBlank() || segredo == null || segredo.isBlank()) {
            return false;
        }

        // 1. Separa "ts=...,v1=..." nas duas partes
        String ts = null;
        String v1 = null;
        for (String parte : xSignature.split(",")) {
            String[] chaveValor = parte.split("=", 2);
            if (chaveValor.length != 2) continue;
            String chave = chaveValor[0].trim();
            String valor = chaveValor[1].trim();
            if (chave.equals("ts")) ts = valor;
            if (chave.equals("v1")) v1 = valor;
        }
        if (ts == null || v1 == null) {
            return false;
        }

        // 2. Monta o manifesto exatamente como o Mercado Pago montou
        StringBuilder manifesto = new StringBuilder();
        if (dataId != null && !dataId.isBlank()) {
            manifesto.append("id:").append(dataId.toLowerCase(Locale.ROOT)).append(';');
        }
        if (xRequestId != null && !xRequestId.isBlank()) {
            manifesto.append("request-id:").append(xRequestId).append(';');
        }
        manifesto.append("ts:").append(ts).append(';');

        String esperado = hmacSha256Hex(segredo, manifesto.toString());

        // 4. Compara em TEMPO CONSTANTE: um equals comum para no primeiro caractere diferente,
        return MessageDigest.isEqual(
                esperado.getBytes(StandardCharsets.UTF_8),
                v1.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
    }

    static String hmacSha256Hex(String segredo, String texto) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(segredo.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(texto.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Não foi possível calcular o HMAC.", e);
        }
    }
}
