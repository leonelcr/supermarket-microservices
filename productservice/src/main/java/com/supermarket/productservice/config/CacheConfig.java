package com.supermarket.productservice.config;

import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;

@Configuration
@EnableCaching // Movemos esta anotación aquí para tener todo ordenado
public class CacheConfig {

    /**
     * Aquí definimos el comportamiento por defecto y los TTL específicos
     */
    @Bean
    public RedisCacheManagerBuilderCustomizer redisCacheManagerBuilderCustomizer() {
        return (builder) -> builder
                .cacheDefaults(defaultConfig()) // Configuración global (si no especificas nombre)
                
                // Configuración Específica para "products" (La que usas en el Service)
                .withCacheConfiguration("products", 
                        defaultConfig().entryTtl(Duration.ofMinutes(10))) // 10 Minutos de vida
                
                // Ejemplo: Si tuvieras otro cache para "precios_oferta"
                .withCacheConfiguration("promotions", 
                        defaultConfig().entryTtl(Duration.ofHours(1)));
    }

    /**
     * Configuración base:
     * 1. TTL por defecto de 60 minutos.
     * 2. (Opcional pero recomendado) Usar JSON en lugar de binario Java.
     */
    private RedisCacheConfiguration defaultConfig() {
        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(60)) // TTL Global por defecto
                .disableCachingNullValues()   // No guardar nulls en Redis
                
                // SI QUIERES VER JSON EN REDISINSIGHT EN LUGAR DE SÍMBOLOS RAROS, DESCOMENTA ESTO:
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(new GenericJackson2JsonRedisSerializer()));
    }
}
