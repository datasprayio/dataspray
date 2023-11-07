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
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.URL;
import java.util.Optional;

@Slf4j
@ApplicationScoped
public class S3ClientProvider {

    @ConfigProperty(name = "startupWaitUntilDeps", defaultValue = "false")
    boolean startupWaitUntilDeps;
    @ConfigProperty(name = "aws.s3.productionRegion")
    Optional<String> productionRegionOpt;
    @ConfigProperty(name = "aws.s3.serviceEndpoint")
    Optional<String> serviceEndpointOpt;
    @ConfigProperty(name = "aws.s3.dnsResolverTo")
    Optional<String> dnsResolverToOpt;
    @ConfigProperty(name = "aws.s3.pathStyleEnabled", defaultValue = "false")
    boolean pathStyleEnabled;

    @Inject
    AwsCredentialsProvider awsCredentialsProviderSdk2;
    @Inject
    SdkHttpClient sdkHttpClient;
    @Inject
    NetworkUtil networkUtil;

    @Singleton
    public S3Presigner getS3Presigner() {
        log.debug("Opening S3 presigner client on {}", serviceEndpointOpt);
        waitUntilPortOpen();

        S3Presigner.Builder builder = S3Presigner.builder()
                .credentialsProvider(awsCredentialsProviderSdk2);

        if (pathStyleEnabled) {
            builder.serviceConfiguration(S3Configuration.builder()
                    .pathStyleAccessEnabled(true).build());
        }
        serviceEndpointOpt.map(URI::create).ifPresent(builder::endpointOverride);
        productionRegionOpt.map(Region::of).ifPresent(builder::region);

        return builder.build();
    }

    @Singleton
    public S3Client getS3Client() {
        log.debug("Opening S3 v2 client on {}", serviceEndpointOpt);
        waitUntilPortOpen();
        S3ClientBuilder builder = S3Client.builder()
                .credentialsProvider(awsCredentialsProviderSdk2);
        if (pathStyleEnabled) {
            builder.serviceConfiguration(S3Configuration.builder()
                    .pathStyleAccessEnabled(true).build());
        }
        serviceEndpointOpt.map(URI::create).ifPresent(builder::endpointOverride);
        productionRegionOpt.map(Region::of).ifPresent(builder::region);
        dnsResolverToOpt.ifPresentOrElse(
                // When resolver is set, ignore our injected client, we are running
                // in a test, instead use one that we can control DNS resolution on
                dnsResolverTo -> builder.httpClientBuilder(ApacheHttpClient.builder()
                        .dnsResolver(host -> {
                            log.trace("Resolving {}", host);
                            return new InetAddress[]{InetAddress.getByName(dnsResolverTo)};
                        })),
                // Otherwise use the provided client
                () -> builder.httpClient(sdkHttpClient));
        return builder.build();
    }

    private void waitUntilPortOpen() {
        if (startupWaitUntilDeps && serviceEndpointOpt.isPresent()) {
            log.info("Waiting for S3 to be up {}", serviceEndpointOpt.get());
            try {
                URL url = new URL(serviceEndpointOpt.get());
                networkUtil.waitUntilPortOpen(
                        dnsResolverToOpt.orElse(url.getHost()),
                        url.getPort() != -1
                                ? url.getPort()
                                : url.getDefaultPort());
            } catch (IOException ex) {
                throw new RuntimeException("Failed to wait until S3 port opened", ex);
            }
        }
    }
}
