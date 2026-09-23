package org.project.security;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Service
public class JwtService {
    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private long expiration; // em milissegundos

    // Transforma o texto da chave secreta num formato que o algoritmo entende
    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    // Cria o crachá (token) com o e-mail e o cargo do usuário
    public String gerarToken(String email, String role) {
        Date agora = new Date();
        Date validade = new Date(agora.getTime() + expiration);

        return Jwts.builder()
                .subject(email)          // "dono" do crachá
                .claim("role", role)     // informação extra: o cargo
                .issuedAt(agora)         // quando foi emitido
                .expiration(validade)    // quando expira
                .signWith(getSigningKey()) // assina com a chave secreta
                .compact();              // fecha e devolve como texto
    }

    // Abre o crachá e lê o que está escrito dentro. Se for falso ou expirado, dá erro.
    private Claims extrairClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String extrairEmail(String token) {
        return extrairClaims(token).getSubject();
    }

    public String extrairRole(String token) {
        return extrairClaims(token).get("role", String.class);
    }

    // Devolve true se o token for válido e não estiver expirado
    public boolean tokenValido(String token) {
        try {
            extrairClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
