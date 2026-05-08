package com.supermarket.apigateway;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.supermarket.apigateway.util.JwtUtils;
import net.datafaker.Faker;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.Locale;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@DisplayName("API Gateway - Tests de Ruteo con WireMock")
class GatewayRoutingTest {

    private static final Faker faker = new Faker(Locale.ENGLISH);

    /*
     * Los servidores WireMock arrancan en bloque static, ANTES de que Spring
     * llame a @DynamicPropertySource (que ocurre durante la construccion del
     * contexto, antes de @BeforeAll).
     */
    private static final WireMockServer productServiceMock;
    private static final WireMockServer cartServiceMock;

    static {
        productServiceMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        productServiceMock.start();
        cartServiceMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        cartServiceMock.start();
    }

    /*
     * Sobreescribe las rutas completas antes de que Spring construya el contexto.
     * Se proveen TODAS las propiedades de cada ruta porque Spring Boot reemplaza
     * la lista entera cuando DynamicPropertySource toca algún índice de la lista,
     * descartando los predicados del YAML (que son @NotEmpty en RouteDefinition).
     */
    @DynamicPropertySource
    static void overrideGatewayRoutes(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.gateway.routes[0].id",            () -> "product-service");
        registry.add("spring.cloud.gateway.routes[0].uri",           () -> "http://localhost:" + productServiceMock.port());
        registry.add("spring.cloud.gateway.routes[0].predicates[0]", () -> "Path=/api/products/**");
        registry.add("spring.cloud.gateway.routes[0].filters[0]",    () -> "AddRequestHeader=X-Gateway-Secret, SuperSecretKey123");

        registry.add("spring.cloud.gateway.routes[1].id",            () -> "cart-service");
        registry.add("spring.cloud.gateway.routes[1].uri",           () -> "http://localhost:" + cartServiceMock.port());
        registry.add("spring.cloud.gateway.routes[1].predicates[0]", () -> "Path=/api/cart/**");
        registry.add("spring.cloud.gateway.routes[1].filters[0]",    () -> "AddRequestHeader=X-Gateway-Secret, SuperSecretKey123");
    }

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private JwtUtils jwtUtils;

    @AfterAll
    static void detenerMockServers() {
        productServiceMock.stop();
        cartServiceMock.stop();
    }

    @BeforeEach
    void resetearMocks() {
        productServiceMock.resetAll();
        cartServiceMock.resetAll();
    }

    private String generarJwtValido() {
        return jwtUtils.generateAccessToken(faker.internet().username());
    }

    @Test
    @DisplayName("GET /api/products se rutea al ProductService e inyecta X-Gateway-Secret")
    void routeToProductService_injectsGatewaySecretHeader() {
        productServiceMock.stubFor(
                WireMock.get(urlPathEqualTo("/api/products"))
                        .willReturn(aResponse()
                                .withStatus(200)
                                .withHeader("Content-Type", "application/json")
                                .withBody("[]"))
        );

        webTestClient.get()
                .uri("/api/products")
                .header("Authorization", "Bearer " + generarJwtValido())
                .exchange()
                .expectStatus().isOk()
                .expectBody().json("[]");

        productServiceMock.verify(
                getRequestedFor(urlPathEqualTo("/api/products"))
                        .withHeader("X-Gateway-Secret", equalTo("SuperSecretKey123"))
        );
    }

    @Test
    @DisplayName("POST /api/cart se rutea al CartService e inyecta X-Gateway-Secret")
    void routeToCartService_injectsGatewaySecretHeader() {
        String username = faker.internet().username();

        cartServiceMock.stubFor(
                WireMock.post(urlPathEqualTo("/api/cart/" + username + "/checkout"))
                        .willReturn(aResponse().withStatus(200).withBody("OK"))
        );

        webTestClient.post()
                .uri("/api/cart/" + username + "/checkout")
                .header("Authorization", "Bearer " + generarJwtValido())
                .exchange()
                .expectStatus().isOk();

        cartServiceMock.verify(
                postRequestedFor(urlPathEqualTo("/api/cart/" + username + "/checkout"))
                        .withHeader("X-Gateway-Secret", equalTo("SuperSecretKey123"))
        );
    }

    @Test
    @DisplayName("Request sin JWT retorna 401 Unauthorized")
    void requestWithoutJwt_returns401() {
        webTestClient.get().uri("/api/products")
                .exchange().expectStatus().isUnauthorized();
    }

    @Test
    @DisplayName("Request con JWT manipulado retorna 401 Unauthorized")
    void requestWithTamperedJwt_returns401() {
        webTestClient.get().uri("/api/products")
                .header("Authorization", "Bearer header.payload.firma-invalida")
                .exchange().expectStatus().isUnauthorized();
    }
}
