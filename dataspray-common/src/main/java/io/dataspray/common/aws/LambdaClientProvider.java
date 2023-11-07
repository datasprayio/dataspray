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
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.LambdaClientBuilder;

import java.net.URI;
import java.util.Optional;

@Slf4j
@ApplicationScoped
public class LambdaClientProvider {

    @ConfigProperty(name = "aws.lambda.productionRegion")
    Optional<String> productionRegionOpt;
    @ConfigProperty(name = "aws.lambda.serviceEndpoint")
    Optional<String> serviceEndpointOpt;

    @Inject
    AwsCredentialsProvider awsCredentialsProvider;
    @Inject
    SdkHttpClient sdkHttpClient;

    @Singleton
    public LambdaClient getLambdaClient() {
        log.debug("Opening Lambda v2 client");
        LambdaClientBuilder lambdaClientBuilder = LambdaClient.builder()
                .credentialsProvider(awsCredentialsProvider)
                .httpClient(sdkHttpClient);
        serviceEndpointOpt.map(URI::create)
                .ifPresent(lambdaClientBuilder::endpointOverride);
        productionRegionOpt.map(Region::of)
                .ifPresent(lambdaClientBuilder::region);

        return lambdaClientBuilder.build();
    }
}
