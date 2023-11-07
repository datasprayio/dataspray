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
import software.amazon.awssdk.services.firehose.FirehoseClient;
import software.amazon.awssdk.services.firehose.FirehoseClientBuilder;

import java.net.URI;
import java.util.Optional;

@Slf4j
@ApplicationScoped
public class FirehoseClientProvider {

    @ConfigProperty(name = "aws.firehose.productionRegion")
    Optional<String> productionRegionOpt;
    @ConfigProperty(name = "aws.firehose.serviceEndpoint")
    Optional<String> serviceEndpointOpt;

    @Inject
    AwsCredentialsProvider awsCredentialsProvider;
    @Inject
    SdkHttpClient sdkHttpClient;

    @Singleton
    public FirehoseClient getFirehoseClient() {
        log.debug("Opening Firehose v2 client");
        FirehoseClientBuilder firehoseClientBuilder = FirehoseClient.builder()
                .credentialsProvider(awsCredentialsProvider)
                .httpClient(sdkHttpClient);
        serviceEndpointOpt.map(URI::create)
                .ifPresent(firehoseClientBuilder::endpointOverride);
        productionRegionOpt.map(Region::of)
                .ifPresent(firehoseClientBuilder::region);

        return firehoseClientBuilder.build();
    }
}
