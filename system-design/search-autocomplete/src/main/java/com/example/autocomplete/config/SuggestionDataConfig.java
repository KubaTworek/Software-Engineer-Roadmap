package com.example.autocomplete.config;

import com.example.autocomplete.model.Suggestion;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class SuggestionDataConfig {

    @Bean
    public List<Suggestion> suggestions() {
        return List.of(
                new Suggestion("iphone 15", "query", 1000),
                new Suggestion("iphone 15 pro", "query", 950),
                new Suggestion("iphone charger", "query", 820),
                new Suggestion("iphone case", "query", 780),
                new Suggestion("iphone 14", "query", 700),
                new Suggestion("ipad pro", "query", 650),
                new Suggestion("ipad air", "query", 620),
                new Suggestion("imac", "query", 560),
                new Suggestion("macbook pro", "query", 920),
                new Suggestion("macbook air", "query", 890),
                new Suggestion("magic keyboard", "query", 500),
                new Suggestion("airpods pro", "query", 870),
                new Suggestion("airpods max", "query", 610),
                new Suggestion("apple watch", "query", 760),
                new Suggestion("apple watch ultra", "query", 690),
                new Suggestion("samsung galaxy s24", "query", 940),
                new Suggestion("samsung galaxy watch", "query", 640),
                new Suggestion("samsung charger", "query", 430),
                new Suggestion("sony headphones", "query", 710),
                new Suggestion("sony playstation 5", "query", 830),
                new Suggestion("nintendo switch", "query", 790),
                new Suggestion("java", "query", 880),
                new Suggestion("javascript", "query", 860),
                new Suggestion("java spring boot", "query", 720),
                new Suggestion("java streams", "query", 540),
                new Suggestion("docker", "query", 840),
                new Suggestion("docker compose", "query", 730),
                new Suggestion("kubernetes", "query", 810),
                new Suggestion("postgresql", "query", 760),
                new Suggestion("redis", "query", 700),
                new Suggestion("elasticsearch", "query", 680),
                new Suggestion("opensearch", "query", 520)
        );
    }
}
