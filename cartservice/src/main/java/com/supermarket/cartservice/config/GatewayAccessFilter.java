package com.supermarket.cartservice.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@Slf4j
public class GatewayAccessFilter implements Filter {

    // En PROD esto va en application.yml, no hardcodeado
    private static final String SECRET = "SuperSecretKey123"; 

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        // 1. OBTENER LA RUTA QUE SE ESTÁ CONSULTANDO
        String requestURI = httpRequest.getRequestURI();

        // 2. LISTA BLANCA (WHITELIST): DEJAR PASAR ACTUATOR/SWAGGER
        // Si es una ruta de monitoreo, NO pedimos el secreto.
        if (requestURI.startsWith("/actuator") || 
            requestURI.startsWith("/swagger-ui") || 
            requestURI.startsWith("/v3/api-docs")) {
            
            chain.doFilter(request, response);
            return; // Salimos del método, dejando pasar la petición
        }

        // 3. VALIDACIÓN DE SEGURIDAD (Para el resto de rutas /api/...)
        String gatewayHeader = httpRequest.getHeader("X-Gateway-Secret");

        if (gatewayHeader == null || !gatewayHeader.equals(SECRET)) {
            log.warn("🚨 Intento de acceso directo bloqueado desde IP: {}", httpRequest.getRemoteAddr());
            httpResponse.sendError(HttpServletResponse.SC_FORBIDDEN, "Acceso directo no permitido. Use el Gateway.");
            return;
        }

        chain.doFilter(request, response);
    }
}