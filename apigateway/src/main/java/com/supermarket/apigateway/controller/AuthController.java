package com.supermarket.apigateway.controller;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {

    @Value("${rsa.private-key}")
    private String privateKeyStr;

    // 1. INYECTAMOS EL VALOR DEL YAM (TTL)
    @Value("${jwt.expiration}")
    private Long jwtExpiration;

    @PostMapping("/login")
    public String login(@RequestParam String username) {
        // Simulamos validación de usuario (En prod iría a una BD)
        // Generamos el token firmado con RSA256
        return generateToken(username);
    }

    private String generateToken(String username) {
        try {
            Map<String, Object> claims = new HashMap<>();
            claims.put("role", "USER");// Podemos agregar roles

            return Jwts.builder()
                    .setClaims(claims)
                    .setSubject(username)
                    .setIssuedAt(new Date(System.currentTimeMillis()))
                    
                    // 2. USAMOS LA VARIABLE INYECTADA EN LUGAR DEL HARDCODE
                    .setExpiration(new Date(System.currentTimeMillis() + jwtExpiration)) 
                    
                    .signWith(getPrivateKey(), SignatureAlgorithm.RS256) // FIRMA CON PRIVADA
                    .compact();
        } catch (Exception e) {
            throw new RuntimeException("Error generando token", e);
        }
    }

    private PrivateKey getPrivateKey() throws Exception {
        // 1. LIMPIEZA ROBUSTA
        String sanitizedKey = privateKeyStr
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");

        byte[] keyBytes = Base64.getDecoder().decode(sanitizedKey);
        
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        return kf.generatePrivate(spec);
    }
}