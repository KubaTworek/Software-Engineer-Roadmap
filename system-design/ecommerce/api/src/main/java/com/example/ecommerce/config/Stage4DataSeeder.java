package com.example.ecommerce.config;

import com.example.ecommerce.catalog.ProductRepository;
import com.example.ecommerce.recommendation.RecommendationService;
import com.example.ecommerce.warehouse.WarehouseRepository;
import com.example.ecommerce.warehouse.Warehouse;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class Stage4DataSeeder {
    @Bean
    CommandLineRunner seedStage4(
            WarehouseRepository warehouses,
            ProductRepository products,
            RecommendationService recommendations
    ) {
        return args -> {
            if (warehouses.count() == 0) {
                warehouses.save(new Warehouse("WAW-01", "Warsaw Main Warehouse", "PL", "Warsaw", "ul. Magazynowa 1"));
                warehouses.save(new Warehouse("KRK-01", "Krakow Secondary Warehouse", "PL", "Krakow", "ul. Logistyczna 2"));
            }

            var productList = products.findAll();
            if (productList.size() >= 2) {
                recommendations.addManualRecommendation(
                        productList.get(0).getId(),
                        productList.get(1).getId(),
                        0.85,
                        "MANUAL_STAGE4_SEED"
                );
            }
        };
    }
}
