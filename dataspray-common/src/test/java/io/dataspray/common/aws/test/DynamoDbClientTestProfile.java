package io.dataspray.common.aws.test;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBStreams;
import com.amazonaws.services.dynamodbv2.local.embedded.DynamoDBEmbedded;
import com.amazonaws.services.dynamodbv2.local.shared.access.AmazonDynamoDBLocal;
import com.google.common.collect.ImmutableSet;
import io.quarkus.test.junit.QuarkusTestProfile;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.streams.DynamoDbStreamsAsyncClient;
import software.amazon.awssdk.services.dynamodb.streams.DynamoDbStreamsClient;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Alternative;
import javax.inject.Singleton;
import java.util.Set;

@ApplicationScoped
public class DynamoDbClientTestProfile implements QuarkusTestProfile {

    @Override
    public Set<Class<?>> getEnabledAlternatives() {
        return ImmutableSet.of(DynamoDbClientTestProfile.class);
    }

    @Singleton
    @Alternative
    public DynamoDbClient getDynamoDbClient(AmazonDynamoDBLocal amazonDynamoDBLocal) {
        return amazonDynamoDBLocal.dynamoDbClient();
    }

    @Singleton
    @Alternative
    public DynamoDbAsyncClient getDynamoDbAsyncClient(AmazonDynamoDBLocal amazonDynamoDBLocal) {
        return amazonDynamoDBLocal.dynamoDbAsyncClient();
    }

    @Singleton
    @Alternative
    public DynamoDbStreamsClient getDynamoDbStreamsClient(AmazonDynamoDBLocal amazonDynamoDBLocal) {
        return amazonDynamoDBLocal.dynamoDbStreamsClient();
    }

    @Singleton
    @Alternative
    public DynamoDbStreamsAsyncClient getDynamoDbStreamsAsyncClient(AmazonDynamoDBLocal amazonDynamoDBLocal) {
        return amazonDynamoDBLocal.dynamoDbStreamsAsyncClient();
    }

    @Singleton
    @Alternative
    public AmazonDynamoDB getAmazonDynamoDB(AmazonDynamoDBLocal amazonDynamoDBLocal) {
        return amazonDynamoDBLocal.amazonDynamoDB();
    }

    @Singleton
    @Alternative
    public AmazonDynamoDBStreams getAmazonDynamoDBStreams(AmazonDynamoDBLocal amazonDynamoDBLocal) {
        return amazonDynamoDBLocal.amazonDynamoDBStreams();
    }

    @Singleton
    public AmazonDynamoDBLocal getAmazonDynamoDBLocal() {
        System.setProperty("sqlite4java.library.path", "target/native-lib");
        return DynamoDBEmbedded.create();
    }
}
