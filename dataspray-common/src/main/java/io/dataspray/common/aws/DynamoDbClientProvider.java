// SPDX-FileCopyrightText: 2019-2021 Matus Faro <matus@smotana.com>
// SPDX-License-Identifier: Apache-2.0
package io.dataspray.common.aws;

import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import io.dataspray.common.NetworkUtil;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClientBuilder;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.net.URI;
import java.util.Optional;

@Slf4j
@ApplicationScoped
public class DynamoDbClientProvider {

    @ConfigProperty(name = "startupWaitUntilDeps", defaultValue = "false")
    boolean startupWaitUntilDeps;
    @ConfigProperty(name = "aws.dynamo.productionRegion")
    Optional<String> productionRegionOpt;
    @ConfigProperty(name = "aws.dynamo.serviceEndpoint")
    Optional<String> serviceEndpointOpt;
    @ConfigProperty(name = "aws.dynamo.signingRegion")
    Optional<String> signingRegionOpt;

    @Inject
    ConfigAwsCredentialsProvider awsCredentialsProvider;
    @Inject
    NetworkUtil networkUtil;

    @Singleton
    public DynamoDbClient getDynamoDbClient() {
        log.debug("Opening Dynamo v2 client on {}", serviceEndpointOpt);
        waitUntilPortOpen();
        ApacheHttpClient.Builder httpClientBuilder = ApacheHttpClient.builder();
        DynamoDbClientBuilder builder = DynamoDbClient.builder()
                .credentialsProvider(awsCredentialsProvider)
                .httpClientBuilder(httpClientBuilder);

        if (serviceEndpointOpt.isPresent()) {
            builder.endpointOverride(URI.create(serviceEndpointOpt.get()));
        } else if (productionRegionOpt.isPresent()) {
            builder.region(Region.of(productionRegionOpt.get()));
        }

        return builder.build();
    }

    @Singleton
    public AmazonDynamoDB getAmazonDynamoDB() {
        log.debug("Opening Dynamo v1 client on {}", serviceEndpointOpt);
        waitUntilPortOpen();
        AmazonDynamoDBClientBuilder amazonDynamoDBClientBuilder = AmazonDynamoDBClientBuilder
                .standard()
                .withCredentials(awsCredentialsProvider);
        if (serviceEndpointOpt.isPresent() && signingRegionOpt.isPresent()) {
            amazonDynamoDBClientBuilder.withEndpointConfiguration(
                    new AwsClientBuilder.EndpointConfiguration(serviceEndpointOpt.get(), signingRegionOpt.get()));
        } else if (productionRegionOpt.isPresent()) {
            amazonDynamoDBClientBuilder.withRegion(productionRegionOpt.get());
        }

        return amazonDynamoDBClientBuilder.build();
    }

    @Singleton
    public DynamoDB getDynamoDB(AmazonDynamoDB dynamo) {
        return new DynamoDB(dynamo);
    }

    private void waitUntilPortOpen() {
        if (startupWaitUntilDeps && serviceEndpointOpt.isPresent()) {
            log.info("Waiting for Dynamo to be up {}", serviceEndpointOpt.get());
            try {
                networkUtil.waitUntilPortOpen(serviceEndpointOpt.get());
            } catch (IOException ex) {
                throw new RuntimeException("Failed to wait until Dynamo port opened", ex);
            }
        }
    }
}
