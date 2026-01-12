package com.deliveryservice.listener;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class OrderListener {

    @RabbitListener(queues = "orders-queue")
    public void receiveMessage(String message) {
        // Aquí empieza la lógica del Delivery
        log.info("🚚 RECIBIDO: Orden para delivery detectada.");
        log.info("📦 Contenido del mensaje: {}", message);

        try {
            // Simulamos que el proceso de logística tarda un poco (2 segundos)
            // Esto se verá genial en Zipkin
            log.info("⚙️ Procesando logística...");
            Thread.sleep(2000); 
            log.info("✅ Orden despachada a logística.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Error en procesamiento", e);
        }
    }
}