# Supermarket Microservices

Arquitectura de referencia implementada con Spring Boot 3, Java 21 y Docker.

## Servicios
- **API Gateway (8080):** Spring Cloud Gateway + Security JWT RSA.
- **Product Service (8081):** Postgres + Redis Cache.
- **Cart Service (8082):** MongoDB + RabbitMQ Producer.
- **Delivery Service (8083):** RabbitMQ Consumer.

## Infraestructura
Ejecutar: `docker-compose -f docker-compose.infra.yml up -d`

## Contacto
Maintainer: Engineering Manager
Email: leonelr@gmail.com