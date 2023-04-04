// SPDX-FileCopyrightText: 2019-2021 Matus Faro <matus@smotana.com>
// SPDX-License-Identifier: Apache-2.0
package io.dataspray.common.aws;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import io.dataspray.common.NetworkUtil;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClientBuilder;

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

    @Inject
    AWSCredentialsProvider awsCredentialsProviderSdk1;
    @Inject
    AwsCredentialsProvider awsCredentialsProviderSdk2;
    @Inject
    NetworkUtil networkUtil;

    @Singleton
    public DynamoDbClient getDynamoDbClient() {
        log.debug("Opening Dynamo v2 client on {}", serviceEndpointOpt);
        waitUntilPortOpen();
        DynamoDbClientBuilder builder = DynamoDbClient.builder()
                .credentialsProvider(awsCredentialsProviderSdk2);
        serviceEndpointOpt.map(URI::create).ifPresent(builder::endpointOverride);
        productionRegionOpt.map(Region::of).ifPresent(builder::region);
        return builder.build();
    }

    @Singleton
    public AmazonDynamoDB getAmazonDynamoDB() {
        log.debug("Opening Dynamo v1 client on {}", serviceEndpointOpt);
        waitUntilPortOpen();
        AmazonDynamoDBClientBuilder amazonDynamoDBClientBuilder = AmazonDynamoDBClientBuilder
                .standard()
                .withCredentials(awsCredentialsProviderSdk1);
        if (serviceEndpointOpt.isPresent() && productionRegionOpt.isPresent()) {
            amazonDynamoDBClientBuilder.withEndpointConfiguration(
                    new AwsClientBuilder.EndpointConfiguration(serviceEndpointOpt.get(), productionRegionOpt.get()));
        }
        productionRegionOpt.map(Regions::fromName).ifPresent(amazonDynamoDBClientBuilder::withRegion);

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
