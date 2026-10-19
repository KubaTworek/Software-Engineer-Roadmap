package com.example.newsfeed.infrastructure;

import com.example.newsfeed.feed.*;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.enhanced.dynamodb.*;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.testcontainers.containers.localstack.LocalStackContainer.Service.DYNAMODB;

@Testcontainers(disabledWithoutDocker = true)
class DynamoDbFeedStorageInfrastructureIT {
    @Container
    static final LocalStackContainer AWS = new LocalStackContainer(DockerImageName.parse("localstack/localstack:3.8"))
            .withServices(DYNAMODB);

    @Test
    void adapterKeepsIdempotencyOrderingAndCanRebuildOnePartition() {
        try (DynamoDbClient client = DynamoDbClient.builder()
                .endpointOverride(AWS.getEndpointOverride(DYNAMODB)).region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(AWS.getAccessKey(), AWS.getSecretKey()))).build()) {
            DynamoDbTable<DynamoDbFeedInboxRecord> table = DynamoDbEnhancedClient.builder()
                    .dynamoDbClient(client).build()
                    .table("feed_inbox_test", TableSchema.fromBean(DynamoDbFeedInboxRecord.class));
            table.createTable();
            DynamoDbFeedStorage storage = new DynamoDbFeedStorage(table);
            UUID user = UUID.randomUUID();
            UUID author = UUID.randomUUID();
            UUID older = UUID.randomUUID();
            UUID newer = UUID.randomUUID();
            var entries = List.of(
                    new FeedInboxItem(user, older, author, 0, "TEST", 0, Instant.parse("2026-01-01T00:00:00Z")),
                    new FeedInboxItem(user, newer, author, 0, "TEST", 0, Instant.parse("2026-01-02T00:00:00Z")));

            storage.appendIdempotent(entries);
            storage.appendIdempotent(entries);
            assertThat(storage.getPostIds(user, null, null, 10)).containsExactly(newer, older);

            storage.clearUserFeed(user);
            assertThat(storage.getPostIds(user, null, null, 10)).isEmpty();
        }
    }
}
