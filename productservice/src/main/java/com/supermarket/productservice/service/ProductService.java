package com.supermarket.productservice.service;

import com.supermarket.productservice.model.Product;
import com.supermarket.productservice.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductService {

    private final ProductRepository productRepository;

    // Al guardar o actualizar, borramos la entrada vieja de Redis para forzar una recarga
    @CacheEvict(value = "products", key = "#product.id")
    public Product saveProduct(Product product) {
        log.info("Creando producto: {}", product);
        return productRepository.save(product);
    }

    public List<Product> getAllProducts() {
        return productRepository.findAll();
    }

    @Cacheable(value = "products", key = "#id")
    public Product getProductById(Long id) {
        log.info("Buscando en DB producto id: {}", id);
        return productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Producto no encontrado"));
    }
}