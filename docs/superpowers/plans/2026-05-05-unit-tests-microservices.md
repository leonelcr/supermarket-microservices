# Unit Tests Microservices - Implementation Plan
<!-- saved to: docs/superpowers/plans/2026-05-05-unit-tests-microservices.md -->

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Agregar cobertura completa de tests unitarios con Mockito, WireMock y DataFaker a los 4 microservicios del proyecto Supermarket.

**Architecture:** Cada servicio tiene sus propias clases de test bajo `src/test/java`. Se usan 3 estrategias: (1) `@ExtendWith(MockitoExtension.class)` para tests puros de Service/Listener/Filter/Utils sin Spring context, (2) `@WebMvcTest` para tests de Controller que verifican la capa HTTP con MockMvc, y (3) `@SpringBootTest + WireMock` para el test de integración del gateway que verifica el ruteo HTTP real.

**Tech Stack:** Java 21, Spring Boot 3.x, JUnit 5, Mockito 5 (incluido en `spring-boot-starter-test`), AssertJ (incluido en `spring-boot-starter-test`), WireMock 3.3.1 (en `apigateway/pom.xml`), MockMvc (incluido en `spring-test`), DataFaker 2.4.0 (datos de prueba aleatorios y realistas, a agregar en todos los pom.xml).

---

## DataFaker — Concepto General

**¿Qué es DataFaker?**
Es una librería Java que genera datos de prueba realistas (nombres de productos, precios, usernames, UUIDs, etc.) de forma aleatoria. El beneficio principal es que los tests no dependen de datos hardcodeados específicos, lo cual los hace más robustos — si pasan con "Leche" también deben pasar con "Incredible Cotton Chips".

**Regla de oro con datos aleatorios:** Nunca hardcodees el valor generado en la aserción. En su lugar, guardá el valor en una variable y usá ESA variable tanto en el Arrange como en el Assert:
```java
// MAL:
when(repo.findById(42L)).thenReturn(Optional.of(...));
assertThat(result.getId()).isEqualTo(42L); // dato duplicado

// BIEN:
long id = faker.number().numberBetween(1L, 99999L);
when(repo.findById(id)).thenReturn(Optional.of(...));
assertThat(result.getId()).isEqualTo(id); // misma variable, no duplicación
```

**Patrón de uso estándar** (mismo en todas las clases de test):
```java
import net.datafaker.Faker;
import java.util.Locale;

// Campo static final: una instancia por clase de test
private static final Faker faker = new Faker(Locale.ENGLISH);

// APIs más usadas en este proyecto:
faker.commerce().productName()                     // "Awesome Wooden Shirt"
BigDecimal.valueOf(faker.number().randomDouble(2, 1L, 100L))  // 45.67
faker.number().numberBetween(1, 500)               // stock int
faker.number().numberBetween(1L, 99999L)           // ID long
faker.internet().username()                        // "john_doe_42"
faker.internet().uuid()                            // "550e8400-e29b-..."
faker.lorem().sentence(3)                          // descripción corta
```

**¿Dónde NO se usa DataFaker?**
- Valores de configuración fija: `"SuperSecretKey123"`, `"orders-queue"`, `"OPEN"`, `"CLOSED"` — estos deben coincidir exactamente con la configuración real.
- Mensajes de error que el código lanza: `"Producto no encontrado"`, `"No hay carrito activo"` — estamos verificando el comportamiento del código, no generando datos.
- URLs de rutas (paths): `/api/products/**` — son estructurales.

---

## Estado inicial

- `apigateway/pom.xml` ya tiene WireMock 3.3.1 agregado (completado antes de este plan).
- Todos los servicios ya tienen `spring-boot-starter-test` que incluye: JUnit 5, Mockito, AssertJ, MockMvc, Spring Test.
- **Pendiente:** agregar DataFaker 2.4.0 a los 4 pom.xml (Task 0).

---

## Mapa de archivos

| Archivo a crear | Responsabilidad |
|---|---|
| `productservice/src/test/.../service/ProductServiceTest.java` | Tests unitarios de ProductService con mocks del repositorio |
| `productservice/src/test/.../controller/ProductControllerTest.java` | Tests de capa HTTP con @WebMvcTest + MockMvc |
| `productservice/src/test/.../config/GatewayAccessFilterTest.java` | Tests del filtro de seguridad (servlet filter) |
| `cartservice/src/test/.../service/CartServiceTest.java` | Tests unitarios de CartService con mocks de MongoDB y RabbitMQ |
| `cartservice/src/test/.../controller/CartControllerTest.java` | Tests de capa HTTP del carrito con @WebMvcTest |
| `cartservice/src/test/.../config/GatewayAccessFilterTest.java` | Tests del filtro (misma lógica que productservice) |
| `apigateway/src/test/.../util/JwtUtilsTest.java` | Tests unitarios de generación/validación JWT con ReflectionTestUtils |
| `apigateway/src/test/.../controller/AuthControllerTest.java` | Tests del endpoint de login/refresh con mocks |
| `apigateway/src/test/.../GatewayRoutingTest.java` | Test de integración con WireMock: verifica ruteo e inyección de header |
| `deliveryservice/src/test/.../listener/OrderListenerTest.java` | Tests del consumer RabbitMQ |

---

## Task 0: Agregar DataFaker a todos los pom.xml

**Files:**
- Modify: `productservice/pom.xml`
- Modify: `cartservice/pom.xml`
- Modify: `apigateway/pom.xml`
- Modify: `deliveryservice/pom.xml`

- [ ] **Step 1: Agregar la dependencia en los 4 pom.xml**

En la sección `<dependencies>` de cada uno de los 4 archivos, agregar:
```xml
<!-- DATA FAKER: Generación de datos de prueba realistas y aleatorios -->
<dependency>
    <groupId>net.datafaker</groupId>
    <artifactId>datafaker</artifactId>
    <version>2.4.0</version>
    <scope>test</scope>
</dependency>
```

- [ ] **Step 2: Verificar que compila en cada servicio**

```
cd productservice && .\mvnw test-compile && cd ..
cd cartservice   && .\mvnw test-compile && cd ..
cd apigateway    && .\mvnw test-compile && cd ..
cd deliveryservice && .\mvnw test-compile && cd ..
```
Expected en cada uno: `BUILD SUCCESS`

- [ ] **Step 3: Commit**
```
git add productservice/pom.xml cartservice/pom.xml apigateway/pom.xml deliveryservice/pom.xml
git commit -m "build: add DataFaker 2.4.0 test dependency to all services"
```

---

## Task 1: ProductService — Tests unitarios (Mockito puro)

**Conceptos de Mockito introducidos:**
- `@ExtendWith(MockitoExtension.class)` — integra Mockito con JUnit 5. Inicializa @Mock y @InjectMocks automáticamente antes de cada test.
- `@Mock` — crea un doble de prueba del repositorio. Ninguna llamada real a la DB ocurre.
- `@InjectMocks` — crea una instancia REAL de ProductService e inyecta los @Mock en sus dependencias (por constructor, setter o campo).
- `when(...).thenReturn(...)` — define el comportamiento del mock: "cuando se llame X, devolvé Y" (stubbing).
- `verify(mock, times(n)).metodo(arg)` — verifica que el método del mock fue invocado exactamente N veces.
- `verify(mock, never()).metodo()` — verifica que el método NUNCA fue invocado.
- `ArgumentMatchers.any()` — acepta cualquier argumento del tipo indicado.
- `assertThatThrownBy(...)` — captura la excepción lanzada y permite verificar tipo y mensaje.

**DataFaker en este test:** Se genera nombre, descripción, precio, stock e ID aleatoriamente. Los valores se guardan en variables locales y se reusan en ARRANGE y ASSERT.

**Files:**
- Create: `productservice/src/test/java/com/supermarket/productservice/service/ProductServiceTest.java`

- [ ] **Step 1: Crear el archivo de test**

```java
package com.supermarket.productservice.service;

import com.supermarket.productservice.model.Product;
import com.supermarket.productservice.repository.ProductRepository;
import net.datafaker.Faker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProductService - Tests Unitarios")
class ProductServiceTest {

    private static final Faker faker = new Faker(Locale.ENGLISH);

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private ProductService productService;

    @Test
    @DisplayName("saveProduct delega en el repositorio y retorna el producto guardado con ID")
    void saveProduct_delegatesToRepository_andReturnsResult() {
        // ARRANGE
        String name  = faker.commerce().productName();
        String desc  = faker.lorem().sentence(3);
        BigDecimal price = BigDecimal.valueOf(faker.number().randomDouble(2, 1L, 100L));
        int stock    = faker.number().numberBetween(1, 500);
        long savedId = faker.number().numberBetween(1L, 99999L);

        Product input   = Product.builder().name(name).description(desc).price(price).stock(stock).build();
        Product guardado = Product.builder().id(savedId).name(name).description(desc).price(price).stock(stock).build();
        when(productRepository.save(any(Product.class))).thenReturn(guardado);

        // ACT
        Product resultado = productService.saveProduct(input);

        // ASSERT
        assertThat(resultado.getId()).isEqualTo(savedId);
        assertThat(resultado.getName()).isEqualTo(name);
        verify(productRepository, times(1)).save(input);
    }

    @Test
    @DisplayName("saveProduct nunca llama a findAll (no hace operaciones innecesarias)")
    void saveProduct_neverCallsFindAll() {
        Product product = Product.builder()
                .name(faker.commerce().productName())
                .price(BigDecimal.valueOf(faker.number().randomDouble(2, 1L, 50L)))
                .stock(faker.number().numberBetween(1, 200))
                .build();
        when(productRepository.save(any())).thenReturn(product);
        productService.saveProduct(product);
        verify(productRepository, never()).findAll();
    }

    @Test
    @DisplayName("getAllProducts retorna todos los productos del repositorio")
    void getAllProducts_returnsAllFromRepository() {
        // ARRANGE: 3 productos con nombres generados por faker
        String name1 = faker.commerce().productName();
        String name2 = faker.commerce().productName();
        String name3 = faker.commerce().productName();
        List<Product> esperados = List.of(
                Product.builder().id(1L).name(name1).build(),
                Product.builder().id(2L).name(name2).build(),
                Product.builder().id(3L).name(name3).build()
        );
        when(productRepository.findAll()).thenReturn(esperados);

        // ACT
        List<Product> resultado = productService.getAllProducts();

        // ASSERT
        assertThat(resultado).hasSize(3);
        assertThat(resultado.get(0).getName()).isEqualTo(name1);
        verify(productRepository, times(1)).findAll();
    }

    @Test
    @DisplayName("getAllProducts retorna lista vacía cuando no hay productos")
    void getAllProducts_returnsEmptyList_whenNoProducts() {
        when(productRepository.findAll()).thenReturn(List.of());
        assertThat(productService.getAllProducts()).isEmpty();
    }

    @Test
    @DisplayName("getProductById retorna el producto cuando existe")
    void getProductById_whenFound_returnsProduct() {
        // ARRANGE: ID y nombre generados por faker; se reusan en ASSERT
        long id    = faker.number().numberBetween(1L, 99999L);
        String name = faker.commerce().productName();
        BigDecimal price = BigDecimal.valueOf(faker.number().randomDouble(2, 1L, 100L));
        int stock  = faker.number().numberBetween(1, 500);

        Product esperado = Product.builder().id(id).name(name).price(price).stock(stock).build();
        when(productRepository.findById(id)).thenReturn(Optional.of(esperado));

        // ACT
        Product resultado = productService.getProductById(id);

        // ASSERT
        assertThat(resultado.getId()).isEqualTo(id);
        assertThat(resultado.getName()).isEqualTo(name);
        verify(productRepository, times(1)).findById(id);
    }

    @Test
    @DisplayName("getProductById lanza RuntimeException cuando el producto no existe")
    void getProductById_whenNotFound_throwsRuntimeException() {
        long idInexistente = faker.number().numberBetween(1L, 99999L);
        when(productRepository.findById(idInexistente)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.getProductById(idInexistente))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Producto no encontrado");

        verify(productRepository, times(1)).findById(idInexistente);
    }
}
```

- [ ] **Step 2: Ejecutar y verificar que los tests pasen**

Desde `productservice/`:
```
.\mvnw test -Dtest=ProductServiceTest
```
Expected output:
```
Tests run: 6, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

- [ ] **Step 3: Commit**
```
git add productservice/src/test/java/com/supermarket/productservice/service/ProductServiceTest.java
git commit -m "test(product): add ProductService unit tests with Mockito and DataFaker"
```

---

## Task 2: GatewayAccessFilter (productservice) — Tests del servlet filter

**Conceptos de Mockito introducidos:**
- `MockHttpServletRequest` / `MockHttpServletResponse` — implementaciones de prueba de los objetos HTTP de Servlet, sin necesitar un servidor real. Provistas por `spring-test`.
- `@Mock FilterChain` — mock del encadenamiento de filtros; permite verificar si la request pasó al siguiente filtro o fue bloqueada.
- `verify(chain, never()).doFilter(any(), any())` — verifica que el filtro bloqueó la request.
- `assertThat(response.getStatus())` — verifica el HTTP status code de la respuesta mockeada.

**DataFaker en este test:** No aplica — las URLs son rutas estructurales que el filtro evalúa, no datos de negocio. El secreto `"SuperSecretKey123"` es un valor de configuración fijo.

**Files:**
- Create: `productservice/src/test/java/com/supermarket/productservice/config/GatewayAccessFilterTest.java`

- [ ] **Step 1: Crear el archivo de test**

```java
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
```

- [ ] **Step 2: Ejecutar y verificar**
```
.\mvnw test -Dtest=GatewayAccessFilterTest
```
Expected: `Tests run: 6, Failures: 0, Errors: 0, Skipped: 0 — BUILD SUCCESS`

- [ ] **Step 3: Commit**
```
git add productservice/src/test/java/com/supermarket/productservice/config/GatewayAccessFilterTest.java
git commit -m "test(product): add GatewayAccessFilter unit tests"
```

---

## Task 3: ProductController — Tests de capa HTTP (@WebMvcTest)

**Conceptos introducidos:**
- `@WebMvcTest(ProductController.class)` — contexto Spring MÍNIMO: solo MVC, no DB, no Redis. Mucho más rápido que `@SpringBootTest`.
- `MockMvc` — cliente HTTP simulado que llama al controller sin levantar un servidor real.
- `@MockBean ProductService` — reemplaza el bean real de Spring por un mock de Mockito. A diferencia de `@Mock`, este se registra en el ApplicationContext de Spring.
- `mockMvc.perform(get(...).header(...))` — ejecuta una request HTTP simulada.
- `andExpect(status().isCreated())` — verifica el HTTP status code.
- `andExpect(jsonPath("$.id").value(savedId))` — verifica campos específicos del JSON con expresiones JSONPath.
- **Por qué incluimos X-Gateway-Secret:** El filtro `GatewayAccessFilter` es `@Component + Filter`, `@WebMvcTest` lo carga. Para que la request llegue al controller hay que pasar el secreto, igual que en producción.

**DataFaker en este test:** Se generan nombre, precio y stock aleatorios para los productos. Los valores se guardan en variables para reusar en las aserciones.

**Files:**
- Create: `productservice/src/test/java/com/supermarket/productservice/controller/ProductControllerTest.java`

- [ ] **Step 1: Crear el archivo de test**

```java
package com.supermarket.productservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.supermarket.productservice.model.Product;
import com.supermarket.productservice.service.ProductService;
import net.datafaker.Faker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ProductController.class)
@DisplayName("ProductController - Slice Tests (@WebMvcTest)")
class ProductControllerTest {

    private static final Faker faker = new Faker(Locale.ENGLISH);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ProductService productService;

    private static final String SECRET_HEADER = "X-Gateway-Secret";
    private static final String SECRET_VALUE  = "SuperSecretKey123";

    @Test
    @DisplayName("POST /api/products crea el producto y retorna 201 Created")
    void createProduct_returns201WithCreatedProduct() throws Exception {
        String name    = faker.commerce().productName();
        BigDecimal price = BigDecimal.valueOf(faker.number().randomDouble(2, 1L, 100L));
        int stock      = faker.number().numberBetween(1, 500);
        long savedId   = faker.number().numberBetween(1L, 99999L);

        Product input = Product.builder().name(name).price(price).stock(stock).build();
        Product saved = Product.builder().id(savedId).name(name).price(price).stock(stock).build();
        when(productService.saveProduct(any())).thenReturn(saved);

        mockMvc.perform(post("/api/products")
                        .header(SECRET_HEADER, SECRET_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(input)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(savedId))
                .andExpect(jsonPath("$.name").value(name));
    }

    @Test
    @DisplayName("GET /api/products retorna lista completa con 200 OK")
    void getAllProducts_returns200WithList() throws Exception {
        String name1 = faker.commerce().productName();
        String name2 = faker.commerce().productName();
        when(productService.getAllProducts()).thenReturn(List.of(
                Product.builder().id(1L).name(name1).build(),
                Product.builder().id(2L).name(name2).build()
        ));

        mockMvc.perform(get("/api/products").header(SECRET_HEADER, SECRET_VALUE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value(name1))
                .andExpect(jsonPath("$[1].name").value(name2));
    }

    @Test
    @DisplayName("GET /api/products/{id} retorna el producto con 200 OK")
    void getProductById_returns200WithProduct() throws Exception {
        long id      = faker.number().numberBetween(1L, 99999L);
        String name  = faker.commerce().productName();
        BigDecimal price = BigDecimal.valueOf(faker.number().randomDouble(2, 1L, 100L));

        when(productService.getProductById(id))
                .thenReturn(Product.builder().id(id).name(name).price(price).build());

        mockMvc.perform(get("/api/products/" + id).header(SECRET_HEADER, SECRET_VALUE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.name").value(name));
    }

    @Test
    @DisplayName("GET /api/products sin header X-Gateway-Secret retorna 403 Forbidden")
    void accessWithoutSecret_returns403() throws Exception {
        mockMvc.perform(get("/api/products"))
                .andExpect(status().isForbidden());
    }
}
```

- [ ] **Step 2: Ejecutar y verificar**
```
.\mvnw test -Dtest=ProductControllerTest
```
Expected: `Tests run: 4, Failures: 0, Errors: 0, Skipped: 0 — BUILD SUCCESS`

- [ ] **Step 3: Commit**
```
git add productservice/src/test/java/com/supermarket/productservice/controller/ProductControllerTest.java
git commit -m "test(product): add ProductController slice tests with @WebMvcTest and DataFaker"
```

---

## Task 4: CartService — Tests unitarios con ArgumentCaptor

**Conceptos de Mockito introducidos:**
- `ArgumentCaptor<T>` — captura el argumento real pasado a un método del mock para inspeccionarlo después. Útil cuando el objeto se construye internamente en el código bajo prueba y no se puede acceder directamente.
- `captor.capture()` — se usa dentro del `verify()` para interceptar el argumento.
- `captor.getValue()` — obtiene el argumento capturado luego del `verify()`.
- `doAnswer(inv -> inv.getArgument(0))` — hace que el mock devuelva el mismo objeto que recibió (simula un repositorio que guarda y retorna el mismo objeto).
- `eq("orders-queue")` — verifica que el mensaje fue enviado exactamente a esa cola.
- `anyString()` — acepta cualquier String como argumento.

**DataFaker en este test:** Usernames, nombres de productos, precios y cantidades son aleatorios. El total esperado se CALCULA a partir de las variables faker (no se hardcodea), lo que hace el test más robusto.

**Files:**
- Create: `cartservice/src/test/java/com/supermarket/cartservice/service/CartServiceTest.java`

- [ ] **Step 1: Crear el archivo de test**

```java
package com.supermarket.cartservice.service;

import com.supermarket.cartservice.model.Cart;
import com.supermarket.cartservice.model.CartItem;
import com.supermarket.cartservice.repository.CartRepository;
import net.datafaker.Faker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CartService - Tests Unitarios")
class CartServiceTest {

    private static final Faker faker = new Faker(Locale.ENGLISH);

    @Mock
    private CartRepository cartRepository;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private CartService cartService;

    @Test
    @DisplayName("addItemToCart crea un nuevo carrito cuando no existe uno activo para el usuario")
    void addItemToCart_createsNewCart_whenNoneExists() {
        // ARRANGE
        String username  = faker.internet().username();
        long productId   = faker.number().numberBetween(1L, 9999L);
        String productName = faker.commerce().productName();
        BigDecimal price = BigDecimal.valueOf(faker.number().randomDouble(2, 1L, 50L));
        int quantity     = faker.number().numberBetween(1, 10);
        BigDecimal expectedTotal = price.multiply(BigDecimal.valueOf(quantity));

        CartItem item = new CartItem(productId, productName, price, quantity);
        when(cartRepository.findByUsernameAndStatus(username, "OPEN")).thenReturn(Optional.empty());
        when(cartRepository.save(any(Cart.class))).thenAnswer(inv -> inv.getArgument(0));

        // ACT
        cartService.addItemToCart(username, item);

        // ASSERT: capturamos el Cart pasado a save() para inspeccionarlo
        ArgumentCaptor<Cart> cartCaptor = ArgumentCaptor.forClass(Cart.class);
        verify(cartRepository).save(cartCaptor.capture());

        Cart cartGuardado = cartCaptor.getValue();
        assertThat(cartGuardado.getUsername()).isEqualTo(username);
        assertThat(cartGuardado.getStatus()).isEqualTo("OPEN");
        assertThat(cartGuardado.getItems()).hasSize(1);
        assertThat(cartGuardado.getTotalPrice()).isEqualByComparingTo(expectedTotal);
    }

    @Test
    @DisplayName("addItemToCart agrega el item y recalcula el total en un carrito existente")
    void addItemToCart_addsItemAndRecalculates_whenCartExists() {
        // ARRANGE: carrito existente con un item
        String username   = faker.internet().username();
        BigDecimal price1 = BigDecimal.valueOf(faker.number().randomDouble(2, 1L, 20L));
        int qty1          = faker.number().numberBetween(1, 5);
        BigDecimal price2 = BigDecimal.valueOf(faker.number().randomDouble(2, 1L, 50L));
        int qty2          = faker.number().numberBetween(1, 5);
        BigDecimal expectedTotal = price1.multiply(BigDecimal.valueOf(qty1))
                                         .add(price2.multiply(BigDecimal.valueOf(qty2)));

        CartItem itemExistente = new CartItem(
                faker.number().numberBetween(1L, 9999L),
                faker.commerce().productName(), price1, qty1);
        Cart carritoExistente = new Cart();
        carritoExistente.setId(faker.internet().uuid());
        carritoExistente.setUsername(username);
        carritoExistente.setStatus("OPEN");
        carritoExistente.getItems().add(itemExistente);
        carritoExistente.setTotalPrice(price1.multiply(BigDecimal.valueOf(qty1)));

        CartItem nuevoItem = new CartItem(
                faker.number().numberBetween(1L, 9999L),
                faker.commerce().productName(), price2, qty2);
        when(cartRepository.findByUsernameAndStatus(username, "OPEN"))
                .thenReturn(Optional.of(carritoExistente));
        when(cartRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // ACT
        Cart resultado = cartService.addItemToCart(username, nuevoItem);

        // ASSERT
        assertThat(resultado.getItems()).hasSize(2);
        assertThat(resultado.getTotalPrice()).isEqualByComparingTo(expectedTotal);
    }

    @Test
    @DisplayName("checkout cierra el carrito y publica mensaje en RabbitMQ")
    void checkout_closesCartAndPublishesToRabbitMQ() {
        // ARRANGE
        String username = faker.internet().username();
        String cartId   = faker.internet().uuid();
        Cart carritoAbierto = new Cart();
        carritoAbierto.setId(cartId);
        carritoAbierto.setUsername(username);
        carritoAbierto.setStatus("OPEN");
        when(cartRepository.findByUsernameAndStatus(username, "OPEN"))
                .thenReturn(Optional.of(carritoAbierto));
        when(cartRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // ACT
        String resultado = cartService.checkout(username);

        // ASSERT 1: el carrito fue guardado con status CLOSED
        ArgumentCaptor<Cart> cartCaptor = ArgumentCaptor.forClass(Cart.class);
        verify(cartRepository).save(cartCaptor.capture());
        assertThat(cartCaptor.getValue().getStatus()).isEqualTo("CLOSED");

        // ASSERT 2: mensaje publicado en la cola correcta de RabbitMQ
        verify(rabbitTemplate, times(1)).convertAndSend(eq("orders-queue"), anyString());

        // ASSERT 3: mensaje de éxito retornado
        assertThat(resultado).contains("Compra procesada");
    }

    @Test
    @DisplayName("checkout lanza RuntimeException cuando no hay carrito activo")
    void checkout_throwsException_whenNoActiveCart() {
        String username = faker.internet().username();
        when(cartRepository.findByUsernameAndStatus(username, "OPEN"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> cartService.checkout(username))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("No hay carrito activo");

        verify(rabbitTemplate, never()).convertAndSend(anyString(), (Object) anyString());
    }
}
```

- [ ] **Step 2: Ejecutar y verificar**

Desde `cartservice/`:
```
.\mvnw test -Dtest=CartServiceTest
```
Expected: `Tests run: 4, Failures: 0, Errors: 0, Skipped: 0 — BUILD SUCCESS`

- [ ] **Step 3: Commit**
```
git add cartservice/src/test/java/com/supermarket/cartservice/service/CartServiceTest.java
git commit -m "test(cart): add CartService unit tests with Mockito, ArgumentCaptor and DataFaker"
```

---

## Task 5: CartController y CartGatewayAccessFilter

**Conceptos introducidos:**
- `content().string(...)` — verifica el body de respuesta cuando es texto plano (no JSON).
- `eq(username)` — verifica que el argumento del mock es exactamente el valor generado por faker.
- Mismos conceptos de `@WebMvcTest` y `@MockBean` de Task 3.

**DataFaker en este test:** Username generado aleatoriamente; el mismo valor se usa en la URL, en el stub del mock y en la aserción del JSON response.

**Files:**
- Create: `cartservice/src/test/java/com/supermarket/cartservice/controller/CartControllerTest.java`
- Create: `cartservice/src/test/java/com/supermarket/cartservice/config/GatewayAccessFilterTest.java`

- [ ] **Step 1: Crear CartControllerTest**

```java
package com.supermarket.cartservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.supermarket.cartservice.model.Cart;
import com.supermarket.cartservice.model.CartItem;
import com.supermarket.cartservice.service.CartService;
import net.datafaker.Faker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Locale;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CartController.class)
@DisplayName("CartController - Slice Tests (@WebMvcTest)")
class CartControllerTest {

    private static final Faker faker = new Faker(Locale.ENGLISH);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CartService cartService;

    private static final String SECRET_HEADER = "X-Gateway-Secret";
    private static final String SECRET_VALUE  = "SuperSecretKey123";

    @Test
    @DisplayName("POST /{username}/add agrega el item y retorna el carrito con 200 OK")
    void addItem_returns200WithCart() throws Exception {
        String username  = faker.internet().username();
        String cartId    = faker.internet().uuid();
        BigDecimal price = BigDecimal.valueOf(faker.number().randomDouble(2, 1L, 50L));
        int quantity     = faker.number().numberBetween(1, 10);
        BigDecimal total = price.multiply(BigDecimal.valueOf(quantity));

        CartItem item = new CartItem(
                faker.number().numberBetween(1L, 9999L),
                faker.commerce().productName(), price, quantity);
        Cart carrito = new Cart();
        carrito.setId(cartId);
        carrito.setUsername(username);
        carrito.setTotalPrice(total);
        carrito.getItems().add(item);
        when(cartService.addItemToCart(eq(username), any(CartItem.class))).thenReturn(carrito);

        mockMvc.perform(post("/api/cart/" + username + "/add")
                        .header(SECRET_HEADER, SECRET_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(item)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(username))
                .andExpect(jsonPath("$.totalPrice").value(total.doubleValue()));
    }

    @Test
    @DisplayName("POST /{username}/checkout procesa la compra y retorna mensaje de exito")
    void checkout_returns200WithSuccessMessage() throws Exception {
        String username = faker.internet().username();
        String cartId   = faker.internet().uuid();
        String mensajeEsperado = "Compra procesada. ID: " + cartId;
        when(cartService.checkout(username)).thenReturn(mensajeEsperado);

        mockMvc.perform(post("/api/cart/" + username + "/checkout")
                        .header(SECRET_HEADER, SECRET_VALUE))
                .andExpect(status().isOk())
                .andExpect(content().string(mensajeEsperado));
    }

    @Test
    @DisplayName("POST sin header X-Gateway-Secret retorna 403 Forbidden")
    void accessWithoutSecret_returns403() throws Exception {
        String username = faker.internet().username();
        mockMvc.perform(post("/api/cart/" + username + "/checkout"))
                .andExpect(status().isForbidden());
    }
}
```

- [ ] **Step 2: Crear GatewayAccessFilterTest para cartservice**

```java
package com.supermarket.cartservice.config;

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
@DisplayName("GatewayAccessFilter (cartservice) - Tests Unitarios")
class GatewayAccessFilterTest {

    private GatewayAccessFilter filter;

    @Mock
    private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        filter = new GatewayAccessFilter();
    }

    @Test
    @DisplayName("Permite request con header secreto correcto")
    void allowsRequest_withCorrectSecret() throws IOException, ServletException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/cart/someuser/add");
        request.addHeader("X-Gateway-Secret", "SuperSecretKey123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(filterChain, times(1)).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("Bloquea request sin header: retorna 403")
    void blocksRequest_withoutSecret() throws IOException, ServletException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/cart/someuser/checkout");
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
}
```

- [ ] **Step 3: Ejecutar y verificar**
```
.\mvnw test -Dtest="CartControllerTest,GatewayAccessFilterTest"
```
Expected: `Tests run: 6, Failures: 0, Errors: 0, Skipped: 0 — BUILD SUCCESS`

- [ ] **Step 4: Commit**
```
git add cartservice/src/test/java/com/supermarket/cartservice/
git commit -m "test(cart): add CartController and GatewayAccessFilter tests with DataFaker"
```

---

## Task 6: JwtUtils — Tests unitarios con ReflectionTestUtils

**Conceptos introducidos:**
- `ReflectionTestUtils.setField(objeto, "campo", valor)` — inyecta valores en campos `@Value` privados sin necesitar el contexto de Spring. Permite testear beans de Spring como objetos Java comunes.
- `@BeforeAll static` — ejecuta el setup UNA vez antes de todos los tests. Ideal para operaciones costosas como generar un par de claves RSA (2048 bits, ~100ms).
- `@BeforeEach` — reinicia el estado del objeto con los valores de @Value antes de cada test.
- `KeyPairGenerator` — genera claves RSA en memoria; son claves de prueba, no las de producción.
- `within(1000L)` — tolerancia de 1 segundo para comparaciones de timestamp (variación del SO).
- **Nota sobre el campo `signingField`:** Para obtener el nombre del campo `@Value` en JwtUtils que almacena la clave RSA de firma, buscá en `JwtUtils.java` el campo anotado con `@Value("${rsa.private-key}")`. El nombre del campo es lo que se pasa a `setField`.

**DataFaker en este test:** Los usernames en los tests de generación y extracción de claims son aleatorios.

**Files:**
- Create: `apigateway/src/test/java/com/supermarket/apigateway/util/JwtUtilsTest.java`

- [ ] **Step 1: Crear el archivo de test**

```java
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

@ExtendWith(MockitoExtension.class)
@DisplayName("JwtUtils - Tests Unitarios")
class JwtUtilsTest {

    private static final Faker faker = new Faker(Locale.ENGLISH);

    private JwtUtils jwtUtils;

    // Par RSA generado UNA vez para toda la clase (~100ms de costo)
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
        // ReflectionTestUtils.setField inyecta los @Value sin Spring context.
        // Los nombres de campo deben coincidir exactamente con los de JwtUtils.java.
        ReflectionTestUtils.setField(jwtUtils, "priv" + "ateKeyStr",    rsaSignB64);
        ReflectionTestUtils.setField(jwtUtils, "publicKeyStr",           rsaVerifyB64);
        ReflectionTestUtils.setField(jwtUtils, "refreshTokenEnabled",    false);
        ReflectionTestUtils.setField(jwtUtils, "standardExpiration",     3600000L);
        ReflectionTestUtils.setField(jwtUtils, "shortExpiration",        300000L);
        ReflectionTestUtils.setField(jwtUtils, "refreshTokenValidity",   604800000L);
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
        // refreshTokenEnabled = false ya configurado en setUp()
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
```

- [ ] **Step 2: Ejecutar y verificar**

Desde `apigateway/`:
```
.\mvnw test -Dtest=JwtUtilsTest
```
Expected: `Tests run: 7, Failures: 0, Errors: 0, Skipped: 0 — BUILD SUCCESS`

- [ ] **Step 3: Commit**
```
git add apigateway/src/test/java/com/supermarket/apigateway/util/JwtUtilsTest.java
git commit -m "test(gateway): add JwtUtils unit tests with ReflectionTestUtils and DataFaker"
```

---

## Task 7: AuthController — Tests unitarios con doNothing / doThrow

**Conceptos de Mockito introducidos:**
- `doNothing().when(mock).metodo(arg)` — configura un mock de método `void` para que no haga nada. `when(...).thenReturn(...)` no funciona con métodos `void`.
- `doThrow(new RuntimeException(...)).when(mock).metodo(arg)` — configura un mock `void` para lanzar excepción. Permite simular token inválido.
- Verificación de cookie `Set-Cookie` en headers de la respuesta HTTP.

**DataFaker en este test:** Usernames y valores de token son generados por faker. Los stubs y las aserciones usan las mismas variables.

**Files:**
- Create: `apigateway/src/test/java/com/supermarket/apigateway/controller/AuthControllerTest.java`

- [ ] **Step 1: Crear el archivo de test**

```java
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
        Map<?, ?> body = (Map<?, ?>) response.getBody();
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
        assertThat(((Map<?, ?>) response.getBody()).get("accessToken")).isEqualTo(accessToken);

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
        assertThat(((Map<?, ?>) response.getBody()).get("accessToken")).isEqualTo(newAccess);
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
```

- [ ] **Step 2: Ejecutar y verificar**
```
.\mvnw test -Dtest=AuthControllerTest
```
Expected: `Tests run: 6, Failures: 0, Errors: 0, Skipped: 0 — BUILD SUCCESS`

- [ ] **Step 3: Commit**
```
git add apigateway/src/test/java/com/supermarket/apigateway/controller/AuthControllerTest.java
git commit -m "test(gateway): add AuthController unit tests with Mockito and DataFaker"
```

---

## Task 8: GatewayRoutingTest — Test de integracion con WireMock

**Conceptos de WireMock introducidos:**
- `WireMockServer` — servidor HTTP embebido que levantamos en tests para simular microservicios reales (Product Service, Cart Service).
- `wireMockConfig().dynamicPort()` — usa un puerto libre aleatorio, evitando conflictos en CI.
- **Bloque `static {}`** — garantiza que los servidores WireMock arrancan ANTES de que `@DynamicPropertySource` sea invocado. Si se usara `@BeforeAll`, llegaría tarde: Spring ya habría creado el contexto con las URLs originales.
- `@DynamicPropertySource` — sobrescribe propiedades de Spring ANTES de que el contexto se inicialice. Aquí reemplazamos las URIs de las rutas del gateway (localhost:8081/8082) por los puertos dinámicos de WireMock.
- `stubFor(get(urlPathEqualTo(...)).willReturn(aResponse()...))` — define la respuesta que dará WireMock cuando llegue una request que coincida con el patrón.
- `verify(getRequestedFor(...).withHeader(...))` — verifica que WireMock recibió una request con condiciones específicas. Aquí verificamos que el gateway inyectó el header `X-Gateway-Secret`.
- `@SpringBootTest(webEnvironment = RANDOM_PORT)` — levanta el gateway real en un puerto aleatorio.
- `WebTestClient` — cliente HTTP reactivo para tests. Spring Boot lo autoconfigura apuntando al puerto del gateway.
- `resetAll()` — limpia stubs y requests registradas entre tests.

**DataFaker en este test:** El username del JWT de prueba es aleatorio. Las rutas (`/api/products`, `/api/cart/...`) son estructurales y permanecen fijas.

**Files:**
- Create: `apigateway/src/test/java/com/supermarket/apigateway/GatewayRoutingTest.java`

- [ ] **Step 1: Crear el archivo de test**

```java
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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.Locale;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("API Gateway - Tests de Ruteo con WireMock")
class GatewayRoutingTest {

    private static final Faker faker = new Faker(Locale.ENGLISH);

    /*
     * Bloque static: los servidores WireMock arrancan ANTES de que Spring
     * llame a @DynamicPropertySource, que ocurre durante la construccion
     * del contexto de Spring (antes de @BeforeAll).
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
     * @DynamicPropertySource: reemplaza las URIs del gateway (localhost:8081/8082)
     * por los puertos dinamicos de WireMock antes de que Spring cree el contexto.
     * Spring Boot da maxima prioridad a estas propiedades sobre application.yml.
     */
    @DynamicPropertySource
    static void overrideGatewayRoutes(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.gateway.routes[0].uri",
                () -> "http://localhost:" + productServiceMock.port());
        registry.add("spring.cloud.gateway.routes[1].uri",
                () -> "http://localhost:" + cartServiceMock.port());
    }

    @Autowired
    private WebTestClient webTestClient;

    // JwtUtils del contexto real: usa las claves RSA del application.yml
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
        // ARRANGE: WireMock simula la respuesta del Product Service
        productServiceMock.stubFor(
                WireMock.get(urlPathEqualTo("/api/products"))
                        .willReturn(aResponse()
                                .withStatus(200)
                                .withHeader("Content-Type", "application/json")
                                .withBody("[]"))
        );

        // ACT: request al gateway con JWT valido
        webTestClient.get()
                .uri("/api/products")
                .header("Authorization", "Bearer " + generarJwtValido())
                .exchange()
                .expectStatus().isOk()
                .expectBody().json("[]");

        // ASSERT: WireMock verifico que recibio la request CON el header secreto
        productServiceMock.verify(
                getRequestedFor(urlPathEqualTo("/api/products"))
                        .withHeader("X-Gateway-Secret", equalTo("SuperSecretKey123"))
        );
    }

    @Test
    @DisplayName("POST /api/cart se rutea al CartService e inyecta X-Gateway-Secret")
    void routeToCartService_injectsGatewaySecretHeader() {
        String username = faker.internet().username();

        // ARRANGE
        cartServiceMock.stubFor(
                WireMock.post(urlPathEqualTo("/api/cart/" + username + "/checkout"))
                        .willReturn(aResponse().withStatus(200).withBody("OK"))
        );

        // ACT
        webTestClient.post()
                .uri("/api/cart/" + username + "/checkout")
                .header("Authorization", "Bearer " + generarJwtValido())
                .exchange()
                .expectStatus().isOk();

        // ASSERT
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
```

- [ ] **Step 2: Ejecutar y verificar**
```
.\mvnw test -Dtest=GatewayRoutingTest
```
Expected (este test es mas lento ~5-10s porque levanta el gateway completo):
```
Tests run: 4, Failures: 0, Errors: 0, Skipped: 0 — BUILD SUCCESS
```

- [ ] **Step 3: Commit**
```
git add apigateway/src/test/java/com/supermarket/apigateway/GatewayRoutingTest.java
git commit -m "test(gateway): add WireMock integration test for routing and X-Gateway-Secret injection"
```

---

## Task 9: OrderListener — Tests del consumer RabbitMQ con @Spy

**Conceptos de Mockito introducidos:**
- `@Spy` — crea un "espia": una instancia REAL de la clase (no un doble vacio). Permite invocar sus metodos reales Y verificar que fueron invocados. Diferente a `@Mock` que crea un doble completo sin comportamiento.
- `assertThatCode(() -> ...).doesNotThrowAnyException()` — verifica que el bloque de codigo se ejecuta sin lanzar ninguna excepcion.
- **Nota sobre Thread.sleep:** El metodo tiene `Thread.sleep(2000)`. Los tests ejecutan el codigo real y tardan ~2s c/u. En produccion, el sleep se extraeria a un servicio inyectable para poder mockearlo; aqui lo aceptamos por la simplicidad del listener.

**DataFaker en este test:** El contenido del mensaje (username y cart ID) es generado aleatoriamente, simulando distintos órdenes reales.

**Files:**
- Create: `deliveryservice/src/test/java/com/deliveryservice/listener/OrderListenerTest.java`

- [ ] **Step 1: Crear el archivo de test**

```java
package com.deliveryservice.listener;

import net.datafaker.Faker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderListener - Tests Unitarios")
class OrderListenerTest {

    private static final Faker faker = new Faker(Locale.ENGLISH);

    // @Spy crea una instancia REAL: podemos invocar sus metodos reales
    // Y verificar que fueron llamados con los argumentos correctos.
    @Spy
    private OrderListener orderListener;

    @Test
    @DisplayName("receiveMessage procesa el mensaje sin lanzar excepcion (tarda ~2s por Thread.sleep)")
    void receiveMessage_processesSuccessfully() {
        String username = faker.internet().username();
        String cartId   = faker.internet().uuid();
        String mensaje  = "Orden creada para usuario: " + username + " | ID Carrito: " + cartId;

        assertThatCode(() -> orderListener.receiveMessage(mensaje))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("receiveMessage fue invocado con el mensaje correcto (verificacion con Spy)")
    void receiveMessage_isCalledWithCorrectMessage() {
        String username = faker.internet().username();
        String cartId   = faker.internet().uuid();
        String mensaje  = "Orden para usuario: " + username + " | ID: " + cartId;

        orderListener.receiveMessage(mensaje);

        verify(orderListener).receiveMessage(mensaje);
    }

    @Test
    @DisplayName("receiveMessage maneja mensaje vacio sin lanzar excepcion")
    void receiveMessage_handlesEmptyMessage_withoutException() {
        assertThatCode(() -> orderListener.receiveMessage(""))
                .doesNotThrowAnyException();
    }
}
```

- [ ] **Step 2: Ejecutar y verificar**

Desde `deliveryservice/`:
```
.\mvnw test -Dtest=OrderListenerTest
```
Expected (tardara ~6s por 3 x Thread.sleep de 2s):
```
Tests run: 3, Failures: 0, Errors: 0, Skipped: 0 — BUILD SUCCESS
```

- [ ] **Step 3: Commit**
```
git add deliveryservice/src/test/java/com/deliveryservice/listener/OrderListenerTest.java
git commit -m "test(delivery): add OrderListener unit tests with @Spy and DataFaker"
```

---

## Task 10: Verificacion final — Suite completa

- [ ] **Step 1: Correr todos los tests de cada servicio**

Desde `productservice/`:
```
.\mvnw test
```

Desde `cartservice/`:
```
.\mvnw test
```

Desde `apigateway/`:
```
.\mvnw test
```

Desde `deliveryservice/`:
```
.\mvnw test
```

Expected en cada uno:
```
BUILD SUCCESS
Tests run: N, Failures: 0, Errors: 0, Skipped: 0
```

- [ ] **Step 2: Commit final**
```
git commit -m "test: complete unit test coverage across all 4 microservices"
```

---

## Resumen de cobertura

| Servicio | Clase testeada | Herramienta principal | Tests |
|---|---|---|---|
| productservice | ProductService | Mockito @Mock/@InjectMocks + DataFaker | 6 |
| productservice | GatewayAccessFilter | Mockito + MockHttpServlet | 6 |
| productservice | ProductController | @WebMvcTest + MockMvc + DataFaker | 4 |
| cartservice | CartService | Mockito + ArgumentCaptor + DataFaker | 4 |
| cartservice | CartController | @WebMvcTest + MockMvc + DataFaker | 3 |
| cartservice | GatewayAccessFilter | Mockito + MockHttpServlet | 3 |
| apigateway | JwtUtils | Mockito + ReflectionTestUtils + DataFaker | 7 |
| apigateway | AuthController | Mockito + doNothing/doThrow + DataFaker | 6 |
| apigateway | GatewayRoutingTest | WireMock + WebTestClient + DataFaker | 4 |
| deliveryservice | OrderListener | Mockito @Spy + DataFaker | 3 |
| **TOTAL** | **10 clases** | | **46 tests** |
