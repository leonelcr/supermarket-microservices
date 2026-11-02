package com.supermarket.apigateway.controller;

import com.supermarket.apigateway.util.JwtUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final JwtUtils jwtUtils;

    // Leemos el flag de configuración
    @Value("${security.refresh-token.enabled:false}")
    private boolean refreshTokenEnabled;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestParam String username) {
        
        // 1. Generar siempre el Access Token (Token de acceso principal)
        String accessToken = jwtUtils.generateAccessToken(username);

        // CASO A: Refresh Token DESACTIVADO (Comportamiento Clásico)
        if (!refreshTokenEnabled) {
            // Devolvemos solo el token como String o JSON simple
            return ResponseEntity.ok(Map.of("accessToken", accessToken));
        }

        // CASO B: Refresh Token ACTIVADO (Arquitectura Avanzada)
        String refreshToken = jwtUtils.generateRefreshToken(username);

        // Crear Cookie Segura
        ResponseCookie cookie = ResponseCookie.from("refreshToken", refreshToken)
                .httpOnly(true)
                .secure(false) // True en producción (HTTPS)
                .path("/")
                .maxAge(7 * 24 * 60 * 60)
                .sameSite("Strict")
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(Map.of("accessToken", accessToken));
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@CookieValue(name = "refreshToken", required = false) String refreshToken) {
        
        // Si la feature está apagada, este endpoint no debe funcionar
        if (!refreshTokenEnabled) {
            return ResponseEntity.status(404).body("Refresh Token feature is disabled");
        }

        if (refreshToken == null) {
            return ResponseEntity.status(401).body("No Refresh Token provided");
        }

        try {
            jwtUtils.validateToken(refreshToken);
            String username = jwtUtils.getUserNameFromToken(refreshToken);
            String newAccessToken = jwtUtils.generateAccessToken(username);
            
            return ResponseEntity.ok(Map.of("accessToken", newAccessToken));
        } catch (Exception e) {
            return ResponseEntity.status(401).body("Invalid Refresh Token");
        }
    }
}