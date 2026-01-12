package com.supermarket.cartservice.controller;

import com.supermarket.cartservice.model.Cart;
import com.supermarket.cartservice.model.CartItem;
import com.supermarket.cartservice.service.CartService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    @PostMapping("/{username}/add")
    public ResponseEntity<Cart> addItem(@PathVariable String username, @RequestBody CartItem item) {
        return ResponseEntity.ok(cartService.addItemToCart(username, item));
    }

    @PostMapping("/{username}/checkout")
    public ResponseEntity<String> checkout(@PathVariable String username) {
        return ResponseEntity.ok(cartService.checkout(username));
    }
}