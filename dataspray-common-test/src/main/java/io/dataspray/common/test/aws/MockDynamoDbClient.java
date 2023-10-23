/*
 * Copyright 2023 Matus Faro
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package io.dataspray.common.test.aws;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBStreams;
import com.amazonaws.services.dynamodbv2.local.embedded.DynamoDBEmbedded;
import com.amazonaws.services.dynamodbv2.local.shared.access.AmazonDynamoDBLocal;
import com.google.common.collect.ImmutableMap;
import io.quarkus.arc.properties.IfBuildProperty;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import io.quarkus.test.junit.QuarkusTestProfile;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.streams.DynamoDbStreamsAsyncClient;
import software.amazon.awssdk.services.dynamodb.streams.DynamoDbStreamsClient;

import java.util.Map;

@Slf4j
@ApplicationScoped
public class MockDynamoDbClient implements QuarkusTestResourceLifecycleManager {

    private volatile AmazonDynamoDBLocal instance;

    @Alternative
    @Priority(1)
    @Singleton
    @IfBuildProperty(name = "aws.dynamo.mock.enable", stringValue = "true")
    public DynamoDbClient getDynamoDbClient() {
        log.info("Fetching mock DynamoDbClient");
        return getInstance().dynamoDbClient();
    }

    @Alternative
    @Priority(1)
    @Singleton
    @IfBuildProperty(name = "aws.dynamo.mock.enable", stringValue = "true")
    public DynamoDbAsyncClient getDynamoDbAsyncClient() {
        log.info("Fetching mock DynamoDbAsyncClient");
        return getInstance().dynamoDbAsyncClient();
    }

    @Alternative
    @Priority(1)
    @Singleton
    @IfBuildProperty(name = "aws.dynamo.mock.enable", stringValue = "true")
    public DynamoDbStreamsClient getDynamoDbStreamsClient() {
        log.info("Fetching mock DynamoDbStreamsClient");
        return getInstance().dynamoDbStreamsClient();
    }

    @Alternative
    @Priority(1)
    @Singleton
    @IfBuildProperty(name = "aws.dynamo.mock.enable", stringValue = "true")
    public DynamoDbStreamsAsyncClient getDynamoDbStreamsAsyncClient() {
        log.info("Fetching mock DynamoDbStreamsAsyncClient");
        return getInstance().dynamoDbStreamsAsyncClient();
    }

    @Alternative
    @Priority(1)
    @Singleton
    @IfBuildProperty(name = "aws.dynamo.mock.enable", stringValue = "true")
    public AmazonDynamoDB getAmazonDynamoDB() {
        log.info("Fetching mock AmazonDynamoDB");
        return getInstance().amazonDynamoDB();
    }

    @Alternative
    @Priority(1)
    @Singleton
    @IfBuildProperty(name = "aws.dynamo.mock.enable", stringValue = "true")
    public AmazonDynamoDBStreams getAmazonDynamoDBStreams() {
        log.info("Fetching mock AmazonDynamoDBStreams");
        return getInstance().amazonDynamoDBStreams();
    }

    @Alternative
    @Priority(1)
    @Singleton
    @IfBuildProperty(name = "aws.dynamo.mock.enable", stringValue = "true")
    public AmazonDynamoDBLocal getAmazonDynamoDBLocal() {
        log.info("Fetching mock AmazonDynamoDBLocal");
        return getInstance();
    }

    @Override
    public Map<String, String> start() {
        return Map.of();
    }

    @Override
    public void stop() {
        if (instance != null) {
            log.info("Shutting down mock DynamoDb instance");
            instance.shutdown();
        }
    }

    private AmazonDynamoDBLocal getInstance() {
        if (instance == null) {
            synchronized (this) {
                if (instance == null) {
                    System.setProperty("sqlite4java.library.path", "target/native-lib");
                    log.info("Starting mock DynamoDb instance");
                    instance = DynamoDBEmbedded.create();
                }
            }
        }
        return instance;
    }

    public static class Profile implements QuarkusTestProfile {

        public Map<String, String> getConfigOverrides() {
            return ImmutableMap.of("aws.dynamo.mock.enable", "true");
        }
    }
}