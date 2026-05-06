package com.supermarket.productservice.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("GatewayAccessFilter - Tests Unitarios")
class GatewayAccessFilterTest {

    private GatewayAccessFilter filter;

    @Mock
    private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        filter = new GatewayAccessFilter();
    }

    @Test
    @DisplayName("Permite request con header secreto correcto: chain.doFilter() es invocado")
    void allowsRequest_withCorrectSecret() throws IOException, ServletException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/products");
        request.addHeader("X-Gateway-Secret", "SuperSecretKey123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(filterChain, times(1)).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("Bloquea request sin el header X-Gateway-Secret: retorna 403")
    void blocksRequest_withoutSecret() throws IOException, ServletException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/products");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(filterChain, never()).doFilter(any(), any());
        assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test
    @DisplayName("Bloquea request con header incorrecto: retorna 403")
    void blocksRequest_withWrongSecret() throws IOException, ServletException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/products");
        request.addHeader("X-Gateway-Secret", "clave-incorrecta");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(filterChain, never()).doFilter(any(), any());
        assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test
    @DisplayName("Permite rutas de Actuator sin el header (whitelist)")
    void allowsActuatorRoutes_withoutSecret() throws IOException, ServletException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(filterChain, times(1)).doFilter(request, response);
    }

    @Test
    @DisplayName("Permite rutas de Swagger sin el header (whitelist)")
    void allowsSwaggerRoutes_withoutSecret() throws IOException, ServletException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/swagger-ui/index.html");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(filterChain, times(1)).doFilter(request, response);
    }

    @Test
    @DisplayName("Permite rutas de OpenAPI sin el header (whitelist)")
    void allowsOpenApiRoutes_withoutSecret() throws IOException, ServletException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v3/api-docs");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(filterChain, times(1)).doFilter(request, response);
    }
}
