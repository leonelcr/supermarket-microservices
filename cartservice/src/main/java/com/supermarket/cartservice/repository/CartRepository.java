package com.supermarket.cartservice.repository;

import com.supermarket.cartservice.model.Cart;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CartRepository extends MongoRepository<Cart, String> {
    // Método mágico de Spring Data para buscar por usuario y estado
    Optional<Cart> findByUsernameAndStatus(String username, String status);
}