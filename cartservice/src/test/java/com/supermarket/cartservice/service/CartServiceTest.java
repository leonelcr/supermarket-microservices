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

// Prueba el servicio de carrito con repositorio y RabbitMQ simulados. Verifica
// la creación, actualización, cierre y publicación de compras.
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
        String username    = faker.internet().username();
        long productId     = faker.number().numberBetween(1L, 9999L);
        String productName = faker.commerce().productName();
        BigDecimal price   = BigDecimal.valueOf(faker.number().randomDouble(2, 1L, 50L));
        int quantity       = faker.number().numberBetween(1, 10);
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

        // ASSERT 3: mensaje de exito retornado
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
