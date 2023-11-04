// SPDX-FileCopyrightText: 2019-2021 Matus Faro <matus@smotana.com>
// SPDX-License-Identifier: Apache-2.0
package io.dataspray.common.aws;

import io.dataspray.common.NetworkUtil;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.http.SdkHttpClient;
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
    AwsCredentialsProvider awsCredentialsProviderSdk2;
    @Inject
    SdkHttpClient sdkHttpClient;
    @Inject
    NetworkUtil networkUtil;

    @Singleton
    public DynamoDbClient getDynamoDbClient() {
        log.debug("Opening Dynamo v2 client on {}", serviceEndpointOpt);
        waitUntilPortOpen();
        DynamoDbClientBuilder builder = DynamoDbClient.builder()
                .credentialsProvider(awsCredentialsProviderSdk2)
                .httpClient(sdkHttpClient);
        serviceEndpointOpt.map(URI::create).ifPresent(builder::endpointOverride);
        productionRegionOpt.map(Region::of).ifPresent(builder::region);
        return builder.build();
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
