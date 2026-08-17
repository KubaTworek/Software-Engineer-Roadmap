package com.example.newsfeed.config;

import com.example.newsfeed.feed.DynamoDbFeedInboxRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.enhanced.dynamodb.*;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClientBuilder;

import java.net.URI;

/**
 * Konfiguracja DynamoDB dla feed storage.
 *
 * Aktywuje się tylko wtedy, gdy:
 *
 * newsfeed.feed.storage=dynamodb
 *
 * Dzięki temu lokalnie można dalej używać PostgresFeedStorage,
 * a produkcyjnie przełączyć aplikację na DynamoDB.
 */
@Configuration
@ConditionalOnProperty(
        name = "newsfeed.feed.storage",
        havingValue = "dynamodb"
)
public class DynamoDbConfig {

    @Bean
    public DynamoDbClient dynamoDbClient(
            @Value("${newsfeed.dynamodb.region:eu-central-1}") String region,
            @Value("${newsfeed.dynamodb.endpoint:}") String endpoint
    ) {
        DynamoDbClientBuilder builder = DynamoDbClient.builder()
                .region(Region.of(region))
                .credentialsProvider(DefaultCredentialsProvider.create());

        /*
         * Endpoint override jest przydatny lokalnie, np. dla LocalStack.
         *
         * W AWS produkcyjnym zostawiamy endpoint pusty.
         */
        if (endpoint != null && !endpoint.isBlank()) {
            builder.endpointOverride(URI.create(endpoint));
        }

        return builder.build();
    }

    @Bean
    public DynamoDbEnhancedClient dynamoDbEnhancedClient(DynamoDbClient dynamoDbClient) {
        return DynamoDbEnhancedClient.builder()
                .dynamoDbClient(dynamoDbClient)
                .build();
    }

    @Bean
    public DynamoDbTable<DynamoDbFeedInboxRecord> feedInboxTable(
            DynamoDbEnhancedClient enhancedClient,
            @Value("${newsfeed.dynamodb.feed-inbox-table:feed_inbox}") String tableName
    ) {
        return enhancedClient.table(
                tableName,
                TableSchema.fromBean(DynamoDbFeedInboxRecord.class)
        );
    }
}