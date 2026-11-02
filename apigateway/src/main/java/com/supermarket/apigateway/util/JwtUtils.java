package com.supermarket.apigateway.util;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Component
public class JwtUtils {

    // ... Claves RSA ...
    @Value("${rsa.private-key}")
    private String privateKeyStr;

    @Value("${rsa.public-key}")
    private String publicKeyStr;

    // 1. Leemos el Feature Flag
    @Value("${security.refresh-token.enabled:false}")
    private boolean refreshTokenEnabled;

    // 2. Leemos la duración LARGA (Original / Modo Simple)
    @Value("${security.jwt.expiration:3600000}")
    private long standardExpiration;

    // 3. Leemos la duración CORTA (Modo Refresh)
    @Value("${security.refresh-token.access-token-validity:300000}")
    private long shortExpiration;

    // 4. Leemos la duración del Refresh Token
    @Value("${security.refresh-token.refresh-token-validity:604800000}")
    private long refreshTokenValidity;

    // ---------------------------------------------------------

    // Generar Access Token (Lógica Inteligente)
    public String generateAccessToken(String username) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("role", "USER");
        claims.put("type", "access");
        
        // AQUÍ ESTÁ LA LÓGICA QUE PEDISTE:
        // Si hay refresh token -> Usamos tiempo corto (5 min)
        // Si NO hay refresh token -> Usamos tiempo estándar (1 hora)
        long validity = refreshTokenEnabled ? shortExpiration : standardExpiration;

        return createToken(claims, username, validity);
    }

    // Generar Refresh Token (Siempre usa su propia validez larga)
    public String generateRefreshToken(String username) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("type", "refresh");
        return createToken(claims, username, refreshTokenValidity);
    }

    // Validar Token (Usando clave pública)
    public void validateToken(String token) {
        try {
            Jwts.parserBuilder().setSigningKey(getPublicKey()).build().parseClaimsJws(token);
        } catch (Exception e) {
            throw new RuntimeException("Token inválido o expirado");
        }
    }

    // Extraer usuario del token
    public String getUserNameFromToken(String token) {
        try {
            return Jwts.parserBuilder()
                    .setSigningKey(getPublicKey()) // Aquí ocurría el error porque lanza Exception
                    .build()
                    .parseClaimsJws(token)
                    .getBody()
                    .getSubject();
        } catch (Exception e) {
            // Convertimos la excepción revisada en una RuntimeException para no ensuciar la firma
            throw new RuntimeException("Error extrayendo usuario del token", e);
        }
    }

    // Método privado común para crear JWTs
    private String createToken(Map<String, Object> claims, String username, long validity) {
        try {
            return Jwts.builder()
                    .setClaims(claims)
                    .setSubject(username)
                    .setIssuedAt(new Date(System.currentTimeMillis()))
                    .setExpiration(new Date(System.currentTimeMillis() + validity))
                    .signWith(getPrivateKey(), SignatureAlgorithm.RS256)
                    .compact();
        } catch (Exception e) {
            throw new RuntimeException("Error firmando token", e);
        }
    }

    private PrivateKey getPrivateKey() throws Exception {
        byte[] keyBytes = Base64.getDecoder().decode(privateKeyStr);
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        return kf.generatePrivate(spec);
    }

    private PublicKey getPublicKey() throws Exception {
        byte[] keyBytes = Base64.getDecoder().decode(publicKeyStr);
        X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        return kf.generatePublic(spec);
    }
}
