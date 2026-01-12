package com.supermarket.cartservice.service;

import com.supermarket.cartservice.model.Cart;
import com.supermarket.cartservice.model.CartItem;
import com.supermarket.cartservice.repository.CartRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
@Slf4j
public class CartService {

    private final CartRepository cartRepository;
    private final RabbitTemplate rabbitTemplate; // <--- Cliente RabbitMQ

    // Agregar item al carrito
    public Cart addItemToCart(String username, CartItem item) {
        Cart cart = cartRepository.findByUsernameAndStatus(username, "OPEN")
                .orElseGet(() -> {
                    Cart newCart = new Cart();
                    newCart.setUsername(username);
                    newCart.setStatus("OPEN");
                    return newCart;
                });

        cart.getItems().add(item);
        
        // Recalcular total (simple)
        BigDecimal total = cart.getItems().stream()
                .map(i -> i.getPrice().multiply(new BigDecimal(i.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        cart.setTotalPrice(total);

        return cartRepository.save(cart);
    }

    // Checkout: Cierra carrito y avisa a Delivery (Asíncrono)
    public String checkout(String username) {
        Cart cart = cartRepository.findByUsernameAndStatus(username, "OPEN")
                .orElseThrow(() -> new RuntimeException("No hay carrito activo"));

        cart.setStatus("CLOSED");
        cartRepository.save(cart);

        // Enviar mensaje a RabbitMQ
        // Usamos el exchange por defecto (directo) mandando a la cola "orders-queue"
        String message = "Orden creada para usuario: " + username + " | ID Carrito: " + cart.getId();
        
        log.info("Enviando mensaje a RabbitMQ: {}", message);
        rabbitTemplate.convertAndSend("orders-queue", message);

        return "Compra procesada con éxito. ID: " + cart.getId();
    }
}