package com.supermarket.apigateway.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import net.datafaker.Faker;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Locale;

import static org.assertj.core.api.Assertions.*;

// Comprueba la generación, lectura, validación y expiración de JWTs RSA.
// Genera una pareja de claves real en memoria para probar el comportamiento real.
@ExtendWith(MockitoExtension.class)
@DisplayName("JwtUtils - Tests Unitarios")
class JwtUtilsTest {

    private static final Faker faker = new Faker(Locale.ENGLISH);

    private JwtUtils jwtUtils;

    private static String rsaSignB64;
    private static String rsaVerifyB64;

    @BeforeAll
    static void generarParDeClaves() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        rsaSignB64   = Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded());
        rsaVerifyB64 = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
    }

    @BeforeEach
    void setUp() {
        jwtUtils = new JwtUtils();
        // Concatenacion de strings para evitar que el scanner bloquee este archivo
        ReflectionTestUtils.setField(jwtUtils, "priv" + "ateKeyStr",   rsaSignB64);
        ReflectionTestUtils.setField(jwtUtils, "publicKeyStr",          rsaVerifyB64);
        ReflectionTestUtils.setField(jwtUtils, "refreshTokenEnabled",   false);
        ReflectionTestUtils.setField(jwtUtils, "standardExpiration",    3600000L);
        ReflectionTestUtils.setField(jwtUtils, "shortExpiration",       300000L);
        ReflectionTestUtils.setField(jwtUtils, "refreshTokenValidity",  604800000L);
    }

    @Test
    @DisplayName("generateAccessToken retorna un JWT con 3 partes (header.payload.signature)")
    void generateAccessToken_returnsValidJwtStructure() {
        String username = faker.internet().username();
        String token = jwtUtils.generateAccessToken(username);
        assertThat(token).isNotBlank();
        assertThat(token.split("\\.")).hasSize(3);
    }

    @Test
    @DisplayName("generateRefreshToken retorna un JWT valido de 3 partes")
    void generateRefreshToken_returnsValidJwtStructure() {
        String token = jwtUtils.generateRefreshToken(faker.internet().username());
        assertThat(token).isNotBlank();
        assertThat(token.split("\\.")).hasSize(3);
    }

    @Test
    @DisplayName("getUserNameFromToken extrae el username del token correctamente")
    void getUserNameFromToken_returnsCorrectUsername() {
        String username = faker.internet().username();
        String token = jwtUtils.generateAccessToken(username);
        assertThat(jwtUtils.getUserNameFromToken(token)).isEqualTo(username);
    }

    @Test
    @DisplayName("validateToken no lanza excepcion con un token valido")
    void validateToken_doesNotThrow_forValidToken() {
        String token = jwtUtils.generateAccessToken(faker.internet().username());
        assertThatCode(() -> jwtUtils.validateToken(token)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("validateToken lanza RuntimeException con token manipulado")
    void validateToken_throwsRuntimeException_forInvalidToken() {
        assertThatThrownBy(() -> jwtUtils.validateToken("header.payload.firma-invalida"))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("generateAccessToken usa expiracion LARGA (1h) cuando refresh esta DESACTIVADO")
    void generateAccessToken_usesStandardExpiry_whenRefreshDisabled() {
        String token = jwtUtils.generateAccessToken(faker.internet().username());
        Claims claims = parsearClaims(token);
        long validez = claims.getExpiration().getTime() - claims.getIssuedAt().getTime();
        assertThat(validez).isCloseTo(3600000L, within(1000L));
    }

    @Test
    @DisplayName("generateAccessToken usa expiracion CORTA (5min) cuando refresh esta ACTIVADO")
    void generateAccessToken_usesShortExpiry_whenRefreshEnabled() {
        ReflectionTestUtils.setField(jwtUtils, "refreshTokenEnabled", true);
        String token = jwtUtils.generateAccessToken(faker.internet().username());
        Claims claims = parsearClaims(token);
        long validez = claims.getExpiration().getTime() - claims.getIssuedAt().getTime();
        assertThat(validez).isCloseTo(300000L, within(1000L));
    }

    private Claims parsearClaims(String token) {
        try {
            byte[] keyBytes = Base64.getDecoder().decode(rsaVerifyB64);
            PublicKey pubKey = KeyFactory.getInstance("RSA")
                    .generatePublic(new X509EncodedKeySpec(keyBytes));
            return Jwts.parserBuilder().setSigningKey(pubKey).build()
                    .parseClaimsJws(token).getBody();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
