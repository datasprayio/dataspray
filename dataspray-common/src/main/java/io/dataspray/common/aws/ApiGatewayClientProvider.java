// SPDX-FileCopyrightText: 2019-2021 Matus Faro <matus@smotana.com>
// SPDX-License-Identifier: Apache-2.0
package io.dataspray.common.aws;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.apigateway.ApiGatewayClient;
import software.amazon.awssdk.services.apigateway.ApiGatewayClientBuilder;

import java.net.URI;
import java.util.Optional;

@Slf4j
@ApplicationScoped
public class ApiGatewayClientProvider {

    @ConfigProperty(name = "aws.apigateway.productionRegion")
    Optional<String> productionRegionOpt;
    @ConfigProperty(name = "aws.apigateway.serviceEndpoint")
    Optional<String> serviceEndpointOpt;

    @Inject
    AwsCredentialsProvider awsCredentialsProvider;
    @Inject
    SdkHttpClient sdkHttpClient;

    @Singleton
    public ApiGatewayClient getAthenaClient() {
        log.debug("Opening ApiGateway v2 client");
        ApiGatewayClientBuilder apiGatewayClientBuilder = ApiGatewayClient.builder()
                .credentialsProvider(awsCredentialsProvider)
                .httpClient(sdkHttpClient);
        serviceEndpointOpt.map(URI::create)
                .ifPresent(apiGatewayClientBuilder::endpointOverride);
        productionRegionOpt.map(Region::of)
                .ifPresent(apiGatewayClientBuilder::region);

        return apiGatewayClientBuilder.build();
    }
}
