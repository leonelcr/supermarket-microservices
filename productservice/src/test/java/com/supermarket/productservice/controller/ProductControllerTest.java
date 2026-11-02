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

// Prueba de corte del controlador real. Spring MVC procesa las peticiones HTTP,
// pero ProductService se sustituye por un mock para evitar acceder a PostgreSQL.
@WebMvcTest(ProductController.class)
@DisplayName("ProductController - Slice Tests (@WebMvcTest)")
class ProductControllerTest {

    private static final Faker faker = new Faker(Locale.ENGLISH);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean // Reemplaza ProductService por un mock. Esto permite controlar las respuestas del servicio sin acceder a PostgreSQL.
    private ProductService productService;

    private static final String SECRET_HEADER = "X-Gateway-Secret";
    private static final String SECRET_VALUE  = "SuperSecretKey123";

    @Test
    @DisplayName("POST /api/products crea el producto y retorna 201 Created")
    void createProduct_returns201WithCreatedProduct() throws Exception {
        // Genera un nombre aleatorio y realista para el producto enviado.
        String name      = faker.commerce().productName();

        // Genera un precio monetario con dos decimales, entre 1 y 100.
        BigDecimal price = BigDecimal.valueOf(faker.number().randomDouble(2, 1L, 100L));

        // Genera una cantidad de stock válida, entre 1 y 500 unidades.
        int stock        = faker.number().numberBetween(1, 500);

        // Genera el identificador que devolverá el servicio al producto creado.
        long savedId     = faker.number().numberBetween(1L, 99999L);

        // Crea el producto de entrada sin identificador, porque el servicio debe asignarlo.
        Product input = Product.builder().name(name).price(price).stock(stock).build();

        // Crea la respuesta esperada con el identificador generado.
        Product saved = Product.builder().id(savedId).name(name).price(price).stock(stock).build();

        // Configura el servicio simulado para devolver el producto creado.
        when(productService.saveProduct(any())).thenReturn(saved);

        // Envía una petición POST al endpoint de creación con el cuerpo JSON y el header de gateway.
        mockMvc.perform(post("/api/products")
                        // Añade el header requerido para que el filtro de acceso deje pasar la petición.
                        .header(SECRET_HEADER, SECRET_VALUE)

                        // Indica que el cuerpo de la petición se serializa como JSON.
                        .contentType(MediaType.APPLICATION_JSON)

                        // Convierte el producto de entrada a JSON y lo envía en el cuerpo de la petición.
                        .content(objectMapper.writeValueAsString(input)))

                // Comprueba que la respuesta HTTP tenga el estado 201 Created.
                .andExpect(status().isCreated())

                // Comprueba que el JSON devuelto contiene el identificador esperado.
                .andExpect(jsonPath("$.id").value(savedId))

                // Comprueba que el JSON devuelto contiene el nombre esperado.
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
