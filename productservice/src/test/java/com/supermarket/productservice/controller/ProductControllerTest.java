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
        String name      = faker.commerce().productName();
        BigDecimal price = BigDecimal.valueOf(faker.number().randomDouble(2, 1L, 100L));
        int stock        = faker.number().numberBetween(1, 500);
        long savedId     = faker.number().numberBetween(1L, 99999L);

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
