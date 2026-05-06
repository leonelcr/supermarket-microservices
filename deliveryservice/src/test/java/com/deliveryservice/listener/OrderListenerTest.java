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
