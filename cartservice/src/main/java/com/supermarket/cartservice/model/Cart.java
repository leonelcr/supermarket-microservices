package com.supermarket.cartservice.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
@Document(collection = "carts")
public class Cart {
    
    @Id
    private String id; // Mongo usa String (ObjectId) por defecto
    
    private String username; // Para identificar de quién es el carrito
    private List<CartItem> items = new ArrayList<>();
    private BigDecimal totalPrice = BigDecimal.ZERO;
    private String status; // OPEN, CLOSED
}