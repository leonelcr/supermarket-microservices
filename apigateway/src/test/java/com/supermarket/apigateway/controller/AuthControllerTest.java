package com.supermarket.apigateway.controller;

import com.supermarket.apigateway.util.JwtUtils;
import net.datafaker.Faker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

// Prueba las respuestas de login y refresh del controlador de autenticación.
// Usa un JwtUtils simulado para aislar el comportamiento de los controladores.
@ExtendWith(MockitoExtension.class)
@DisplayName("AuthController - Tests Unitarios")
class AuthControllerTest {

    private static final Faker faker = new Faker(Locale.ENGLISH);

    @Mock
    private JwtUtils jwtUtils;

    @InjectMocks
    private AuthController authController;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(authController, "refreshTokenEnabled", false);
    }

    @Test
    @DisplayName("login con refreshToken DESACTIVADO: retorna solo accessToken, sin cookie")
    void login_withRefreshDisabled_returnsOnlyAccessToken() {
        String username    = faker.internet().username();
        String accessToken = faker.internet().uuid();
        when(jwtUtils.generateAccessToken(username)).thenReturn(accessToken);

        ResponseEntity<?> response = authController.login(username);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertThat(body).containsKey("accessToken");
        assertThat(body.get("accessToken")).isEqualTo(accessToken);
        assertThat(response.getHeaders().get(HttpHeaders.SET_COOKIE)).isNull();
        verify(jwtUtils, never()).generateRefreshToken(anyString());
    }

    @Test
    @DisplayName("login con refreshToken ACTIVADO: retorna accessToken + cookie Set-Cookie segura")
    void login_withRefreshEnabled_returnsTokenAndSecureCookie() {
        ReflectionTestUtils.setField(authController, "refreshTokenEnabled", true);
        String username      = faker.internet().username();
        String accessToken   = faker.internet().uuid();
        String refreshToken  = faker.internet().uuid();
        when(jwtUtils.generateAccessToken(username)).thenReturn(accessToken);
        when(jwtUtils.generateRefreshToken(username)).thenReturn(refreshToken);

        ResponseEntity<?> response = authController.login(username);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        Map<String, Object> body2 = (Map<String, Object>) response.getBody();
        assertThat(body2.get("accessToken")).isEqualTo(accessToken);

        String cookie = response.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertThat(cookie)
                .isNotNull()
                .contains("refreshToken=" + refreshToken)
                .contains("HttpOnly")
                .contains("SameSite=Strict");

        verify(jwtUtils, times(1)).generateRefreshToken(username);
    }

    @Test
    @DisplayName("refresh con feature DESACTIVADA retorna 404")
    void refresh_withFeatureDisabled_returns404() {
        ResponseEntity<?> response = authController.refresh(faker.internet().uuid());
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("refresh sin cookie retorna 401")
    void refresh_withNoCookie_returns401() {
        ReflectionTestUtils.setField(authController, "refreshTokenEnabled", true);
        ResponseEntity<?> response = authController.refresh(null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("refresh con token VALIDO retorna nuevo accessToken")
    void refresh_withValidToken_returnsNewAccessToken() {
        ReflectionTestUtils.setField(authController, "refreshTokenEnabled", true);
        String username      = faker.internet().username();
        String refreshToken  = faker.internet().uuid();
        String newAccess     = faker.internet().uuid();
        doNothing().when(jwtUtils).validateToken(refreshToken);
        when(jwtUtils.getUserNameFromToken(refreshToken)).thenReturn(username);
        when(jwtUtils.generateAccessToken(username)).thenReturn(newAccess);

        ResponseEntity<?> response = authController.refresh(refreshToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        Map<String, Object> body3 = (Map<String, Object>) response.getBody();
        assertThat(body3.get("accessToken")).isEqualTo(newAccess);
    }

    @Test
    @DisplayName("refresh con token INVALIDO retorna 401")
    void refresh_withInvalidToken_returns401() {
        ReflectionTestUtils.setField(authController, "refreshTokenEnabled", true);
        String badToken = faker.internet().uuid();
        doThrow(new RuntimeException("Token invalido")).when(jwtUtils).validateToken(badToken);

        ResponseEntity<?> response = authController.refresh(badToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
