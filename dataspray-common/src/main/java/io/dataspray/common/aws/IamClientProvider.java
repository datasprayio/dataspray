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
import software.amazon.awssdk.services.iam.IamClient;
import software.amazon.awssdk.services.iam.IamClientBuilder;

import java.net.URI;
import java.util.Optional;

@Slf4j
@ApplicationScoped
public class IamClientProvider {

    @ConfigProperty(name = "aws.iam.productionRegion")
    Optional<String> productionRegionOpt;
    @ConfigProperty(name = "aws.iam.serviceEndpoint")
    Optional<String> serviceEndpointOpt;

    @Inject
    AwsCredentialsProvider awsCredentialsProvider;
    @Inject
    SdkHttpClient sdkHttpClient;

    @Singleton
    public IamClient getIamClient() {
        log.debug("Opening IAM v2 client");
        IamClientBuilder iamClientBuilder = IamClient.builder()
                .credentialsProvider(awsCredentialsProvider)
                .region(Region.AWS_GLOBAL)
                .httpClient(sdkHttpClient);
        serviceEndpointOpt.map(URI::create)
                .ifPresent(iamClientBuilder::endpointOverride);
        productionRegionOpt.map(Region::of)
                .ifPresent(iamClientBuilder::region);

        return iamClientBuilder.build();
    }
}
