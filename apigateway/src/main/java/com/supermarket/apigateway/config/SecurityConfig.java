package com.supermarket.apigateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.web.server.SecurityWebFilterChain;

import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Value("${rsa.public-key}")
    private String publicKeyStr;

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        http
            .csrf(csrf -> csrf.disable()) // Desactivar CSRF para APIs
            .authorizeExchange(exchanges -> exchanges
                // 1. Permitir acceso libre al Login y Actuator
                .pathMatchers("/auth/**").permitAll()
                .pathMatchers("/actuator/**").permitAll()
                
                // 2. Proteger TODOS los demás endpoints (Productos, Carrito)
                .anyExchange().authenticated()
            )
            // 3. Habilitar validación de JWT (Resource Server)
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtDecoder(jwtDecoder()))
            );

        return http.build();
    }

    // Decodificador de JWT que usa nuestra llave PÚBLICA para verificar la firma
     @Bean
    public ReactiveJwtDecoder jwtDecoder() {
        try {
            // 1. LIMPIEZA BÁSICA (Quitamos encabezados si el usuario los copió)
            String sanitizedKey = publicKeyStr
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .trim(); // Quita espacios al inicio y final

            // 2. CAMBIO CLAVE: Usamos getMimeDecoder() en vez de getDecoder()
            // Este decodificador ignora saltos de línea (\n, \r) automáticamente.
            byte[] keyBytes = Base64.getMimeDecoder().decode(sanitizedKey);

            X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            RSAPublicKey publicKey = (RSAPublicKey) keyFactory.generatePublic(spec);

            return NimbusReactiveJwtDecoder.withPublicKey(publicKey).build();

        } catch (Exception e) {
            // Log de emergencia para ver qué está leyendo
            System.err.println("============== ERROR CRÍTICO DE LLAVE PÚBLICA ==============");
            System.err.println("La llave que estoy leyendo mide: " + publicKeyStr.length() + " caracteres.");
            System.err.println("Primeros 20 caracteres: " + publicKeyStr.substring(0, Math.min(publicKeyStr.length(), 20)));
            System.err.println("============================================================");
            throw new RuntimeException("Error cargando llave pública. Revisa el log de arriba.", e);
        }
    }

}