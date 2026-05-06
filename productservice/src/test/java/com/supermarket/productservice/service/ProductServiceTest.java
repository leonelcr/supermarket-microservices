package com.supermarket.productservice.service;

import com.supermarket.productservice.model.Product;
import com.supermarket.productservice.repository.ProductRepository;
import net.datafaker.Faker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProductService - Tests Unitarios")
class ProductServiceTest {

    private static final Faker faker = new Faker(Locale.ENGLISH);

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private ProductService productService;

    @Test
    @DisplayName("saveProduct delega en el repositorio y retorna el producto guardado con ID")
    void saveProduct_delegatesToRepository_andReturnsResult() {
        // ARRANGE
        String name  = faker.commerce().productName();
        String desc  = faker.lorem().sentence(3);
        BigDecimal price = BigDecimal.valueOf(faker.number().randomDouble(2, 1L, 100L));
        int stock    = faker.number().numberBetween(1, 500);
        long savedId = faker.number().numberBetween(1L, 99999L);

        Product input   = Product.builder().name(name).description(desc).price(price).stock(stock).build();
        Product guardado = Product.builder().id(savedId).name(name).description(desc).price(price).stock(stock).build();
        when(productRepository.save(any(Product.class))).thenReturn(guardado);

        // ACT
        Product resultado = productService.saveProduct(input);

        // ASSERT
        assertThat(resultado.getId()).isEqualTo(savedId);
        assertThat(resultado.getName()).isEqualTo(name);
        verify(productRepository, times(1)).save(input);
    }

    @Test
    @DisplayName("saveProduct nunca llama a findAll (no hace operaciones innecesarias)")
    void saveProduct_neverCallsFindAll() {
        Product product = Product.builder()
                .name(faker.commerce().productName())
                .price(BigDecimal.valueOf(faker.number().randomDouble(2, 1L, 50L)))
                .stock(faker.number().numberBetween(1, 200))
                .build();
        when(productRepository.save(any())).thenReturn(product);
        productService.saveProduct(product);
        verify(productRepository, never()).findAll();
    }

    @Test
    @DisplayName("getAllProducts retorna todos los productos del repositorio")
    void getAllProducts_returnsAllFromRepository() {
        // ARRANGE: 3 productos con nombres generados por faker
        String name1 = faker.commerce().productName();
        String name2 = faker.commerce().productName();
        String name3 = faker.commerce().productName();
        List<Product> esperados = List.of(
                Product.builder().id(1L).name(name1).build(),
                Product.builder().id(2L).name(name2).build(),
                Product.builder().id(3L).name(name3).build()
        );
        when(productRepository.findAll()).thenReturn(esperados);

        // ACT
        List<Product> resultado = productService.getAllProducts();

        // ASSERT
        assertThat(resultado).hasSize(3);
        assertThat(resultado.get(0).getName()).isEqualTo(name1);
        verify(productRepository, times(1)).findAll();
    }

    @Test
    @DisplayName("getAllProducts retorna lista vacía cuando no hay productos")
    void getAllProducts_returnsEmptyList_whenNoProducts() {
        when(productRepository.findAll()).thenReturn(List.of());
        assertThat(productService.getAllProducts()).isEmpty();
    }

    @Test
    @DisplayName("getProductById retorna el producto cuando existe")
    void getProductById_whenFound_returnsProduct() {
        // ARRANGE: ID y nombre generados por faker; se reusan en ASSERT
        long id    = faker.number().numberBetween(1L, 99999L);
        String name = faker.commerce().productName();
        BigDecimal price = BigDecimal.valueOf(faker.number().randomDouble(2, 1L, 100L));
        int stock  = faker.number().numberBetween(1, 500);

        Product esperado = Product.builder().id(id).name(name).price(price).stock(stock).build();
        when(productRepository.findById(id)).thenReturn(Optional.of(esperado));

        // ACT
        Product resultado = productService.getProductById(id);

        // ASSERT
        assertThat(resultado.getId()).isEqualTo(id);
        assertThat(resultado.getName()).isEqualTo(name);
        verify(productRepository, times(1)).findById(id);
    }

    @Test
    @DisplayName("getProductById lanza RuntimeException cuando el producto no existe")
    void getProductById_whenNotFound_throwsRuntimeException() {
        long idInexistente = faker.number().numberBetween(1L, 99999L);
        when(productRepository.findById(idInexistente)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.getProductById(idInexistente))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Producto no encontrado");

        verify(productRepository, times(1)).findById(idInexistente);
    }
}
