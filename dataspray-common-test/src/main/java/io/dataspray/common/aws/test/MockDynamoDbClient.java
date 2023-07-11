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

package io.dataspray.common.aws.test;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBStreams;
import com.amazonaws.services.dynamodbv2.local.embedded.DynamoDBEmbedded;
import com.amazonaws.services.dynamodbv2.local.shared.access.AmazonDynamoDBLocal;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import io.quarkus.arc.properties.IfBuildProperty;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import io.quarkus.test.junit.QuarkusTestProfile;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.streams.DynamoDbStreamsAsyncClient;
import software.amazon.awssdk.services.dynamodb.streams.DynamoDbStreamsClient;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@ApplicationScoped
public class MockDynamoDbClient implements QuarkusTestResourceLifecycleManager {

    /**
     * This is not the best way of doing this, but MockDynamoDbClient will be instantiated twice 1/ as a test resource
     * and 2/ as a bean. This property is either populated in start method or injected as a config property
     * respectively.
     */
    @ConfigProperty(name = "aws.dynamo.mock.instanceId")
    String instanceId;

    @Alternative
    @Priority(1)
    @Singleton
    @IfBuildProperty(name = "aws.dynamo.mock.enable", stringValue = "true")
    public DynamoDbClient getDynamoDbClient() {
        return instances.get(instanceId).dynamoDbClient();
    }

    @Alternative
    @Priority(1)
    @Singleton
    @IfBuildProperty(name = "aws.dynamo.mock.enable", stringValue = "true")
    public DynamoDbAsyncClient getDynamoDbAsyncClient() {
        return instances.get(instanceId).dynamoDbAsyncClient();
    }

    @Alternative
    @Priority(1)
    @Singleton
    @IfBuildProperty(name = "aws.dynamo.mock.enable", stringValue = "true")
    public DynamoDbStreamsClient getDynamoDbStreamsClient() {
        return instances.get(instanceId).dynamoDbStreamsClient();
    }

    @Alternative
    @Priority(1)
    @Singleton
    @IfBuildProperty(name = "aws.dynamo.mock.enable", stringValue = "true")
    public DynamoDbStreamsAsyncClient getDynamoDbStreamsAsyncClient() {
        return instances.get(instanceId).dynamoDbStreamsAsyncClient();
    }

    @Alternative
    @Priority(1)
    @Singleton
    @IfBuildProperty(name = "aws.dynamo.mock.enable", stringValue = "true")
    public AmazonDynamoDB getAmazonDynamoDB() {
        return instances.get(instanceId).amazonDynamoDB();
    }

    @Alternative
    @Priority(1)
    @Singleton
    @IfBuildProperty(name = "aws.dynamo.mock.enable", stringValue = "true")
    public AmazonDynamoDBStreams getAmazonDynamoDBStreams() {
        return instances.get(instanceId).amazonDynamoDBStreams();
    }

    @Singleton
    public AmazonDynamoDBLocal getAmazonDynamoDBLocal() {
        System.setProperty("sqlite4java.library.path", "target/native-lib");
        return DynamoDBEmbedded.create();
    }

    private static final Map<String, AmazonDynamoDBLocal> instances = Maps.newConcurrentMap();

    @Override
    public Map<String, String> start() {
        System.setProperty("sqlite4java.library.path", "target/native-lib");

        instanceId = UUID.randomUUID().toString();
        AmazonDynamoDBLocal amazonDynamoDBLocal = DynamoDBEmbedded.create();

        return ImmutableMap.of("aws.dynamo.mock.instanceId", instanceId);
    }

    @Override
    public void stop() {
        instances.remove(instanceId).shutdown();
    }

    public static class Profile implements QuarkusTestProfile {

        public Map<String, String> getConfigOverrides() {
            return ImmutableMap.of("aws.dynamo.mock.enable", "true");
        }

        @Override
        public List<TestResourceEntry> testResources() {
            return ImmutableList.of(new TestResourceEntry(
                    MockS3Client.class,
                    ImmutableMap.of(),
                    true));
        }
    }
}
