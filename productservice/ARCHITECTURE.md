```mermaid
graph TD
    %% --- ESTILOS ---
    classDef service fill:#e1f5fe,stroke:#01579b,stroke-width:2px;
    classDef db fill:#e8f5e9,stroke:#2e7d32,stroke-width:2px;
    classDef infra fill:#fff3e0,stroke:#ff6f00,stroke-width:2px,stroke-dasharray: 5 5;
    classDef obs fill:#f3e5f5,stroke:#7b1fa2,stroke-width:2px;
    classDef security fill:#ffebee,stroke:#c62828,stroke-width:2px;

    %% --- ACTORES EXTERNOS ---
    User(("Usuario / Postman"))
    
    %% --- DOCKER NETWORK ---
    %% LA CORRECCIÓN ESTÁ AQUÍ ABAJO (Comillas agregadas)
    subgraph Docker_Network ["☁️ Red Microservicios (Docker Bridge)"]
        
        %% API GATEWAY
        Gateway["🛡️ API Gateway <br/> Port: 8080 <br/> (Spring Security + OAuth2)"]:::security
        
        %% MICROSERVICIOS DE NEGOCIO
        ProductSvc["🛒 Product Service <br/> Port: 8081"]:::service
        CartSvc["🛍️ Cart Service <br/> Port: 8082"]:::service
        DeliverySvc["🚚 Delivery Service <br/> Port: 8083"]:::service

        %% BASES DE DATOS Y BROKERS
        Postgres[("🐘 PostgresSQL <br/> Data")]:::db
        Redis[("⚡ Redis <br/> Cache")]:::db
        Mongo[("🍃 MongoDB <br/> Carritos")]:::db
        RabbitMQ["🐰 RabbitMQ <br/> Event Bus"]:::infra

        %% FLUJO DE SOLICITUDES
        User -- "1. HTTP/JSON (Token JWT)" --> Gateway
        
        Gateway -- "2. Valida JWT (RSA) \n3. Inyecta 'X-Gateway-Secret'" --> ProductSvc
        Gateway -- "2. Valida JWT (RSA) \n3. Inyecta 'X-Gateway-Secret'" --> CartSvc

        %% INTERACCIONES INTERNAS
        ProductSvc -- "Cache Hit/Miss" --> Redis
        ProductSvc -- "Persistencia" --> Postgres
        
        CartSvc -- "Guardar Carrito" --> Mongo
        CartSvc -- "4. Publish 'Order Created'" --> RabbitMQ
        
        RabbitMQ -.->|"5. Consume Evento"| DeliverySvc

        %% --- CAPA DE OBSERVABILIDAD ---
        subgraph Observability_Stack ["📊 Observabilidad"]
            direction LR
            Prometheus["🔥 Prometheus"]:::obs
            Loki["📜 Loki"]:::obs
            Zipkin["🕵️ Zipkin"]:::obs
            Grafana["📈 Grafana"]:::obs
            Promtail["📝 Promtail"]:::obs
        end

        %% CONEXIONES DE OBSERVABILIDAD
        ProductSvc -.->|"Logs"| Promtail
        CartSvc -.->|"Logs"| Promtail
        DeliverySvc -.->|"Logs"| Promtail
        Gateway -.->|"Logs"| Promtail

        Promtail -.-> Loki
        Loki -.-> Grafana

        ProductSvc -.->|"Spans (Http+SQL)"| Zipkin
        CartSvc -.->|"Spans (Http+Mongo+Rabbit)"| Zipkin
        DeliverySvc -.->|"Spans (Rabbit Listener)"| Zipkin
        Gateway -.->|"Spans"| Zipkin

        Prometheus -.->|"Scrape /actuator"| ProductSvc
        Prometheus -.->|"Scrape /actuator"| CartSvc
        Prometheus -.->|"Scrape /actuator"| DeliverySvc
    end
    ```
    