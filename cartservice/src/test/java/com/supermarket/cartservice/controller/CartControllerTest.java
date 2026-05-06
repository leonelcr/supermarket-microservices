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
