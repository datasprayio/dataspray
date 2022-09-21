// SPDX-FileCopyrightText: 2019-2021 Matus Faro <matus@smotana.com>
// SPDX-License-Identifier: Apache-2.0
package io.dataspray.common.aws;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import io.dataspray.common.NetworkUtil;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.URL;
import java.util.Optional;

@Slf4j
@ApplicationScoped
public class S3ClientProvider {

    @ConfigProperty(name = "startupWaitUntilDeps")
    boolean startupWaitUntilDeps;
    @ConfigProperty(name = "aws.s3.productionRegion")
    Optional<String> productionRegionOpt;
    @ConfigProperty(name = "aws.s3.serviceEndpoint")
    Optional<String> serviceEndpointOpt;
    @ConfigProperty(name = "aws.s3.signingRegion")
    Optional<String> signingRegionOpt;
    @ConfigProperty(name = "aws.s3.dnsResolverTo")
    Optional<String> dnsResolverToOpt;

    @Inject
    ConfigAwsCredentialsProvider awsCredentialsProvider;
    @Inject
    NetworkUtil networkUtil;

    @Singleton
    public S3Presigner getS3Presigner() {
        log.debug("Opening S3 presigner client on {}", serviceEndpointOpt);
        waitUntilPortOpen();

        S3Presigner.Builder builder = S3Presigner.builder()
                .credentialsProvider(awsCredentialsProvider);

        if (serviceEndpointOpt.isPresent()) {
            builder.endpointOverride(URI.create(serviceEndpointOpt.get()));
        } else if (productionRegionOpt.isPresent()) {
            builder.region(Region.of(productionRegionOpt.get()));
        }

        return builder.build();
    }

    @Singleton
    public S3Client getS3Client() {
        log.debug("Opening S3 v2 client on {}", serviceEndpointOpt);
        waitUntilPortOpen();

        ApacheHttpClient.Builder httpClientBuilder = ApacheHttpClient.builder();
        S3ClientBuilder builder = S3Client.builder()
                .credentialsProvider(awsCredentialsProvider)
                .httpClientBuilder(httpClientBuilder);

        if (serviceEndpointOpt.isPresent()) {
            builder.endpointOverride(URI.create(serviceEndpointOpt.get()));
        } else if (productionRegionOpt.isPresent()) {
            builder.region(Region.of(productionRegionOpt.get()));
        }
        if (dnsResolverToOpt.isPresent()) {
            httpClientBuilder.dnsResolver(host -> {
                log.trace("Resolving {}", host);
                return new InetAddress[]{InetAddress.getByName(dnsResolverToOpt.get())};
            });
        }

        return builder.build();
    }

    @Singleton
    public AmazonS3 getAmazonS3() {
        log.debug("Opening S3 v1 client on {}", serviceEndpointOpt);
        waitUntilPortOpen();
        AmazonS3ClientBuilder amazonS3ClientBuilder = AmazonS3ClientBuilder
                .standard()
                .withCredentials(awsCredentialsProvider);
        if (serviceEndpointOpt.isPresent() && signingRegionOpt.isPresent()) {
            amazonS3ClientBuilder.withEndpointConfiguration(
                    new AwsClientBuilder.EndpointConfiguration(serviceEndpointOpt.get(), signingRegionOpt.get()));
        } else if (productionRegionOpt.isPresent()) {
            amazonS3ClientBuilder.withRegion(productionRegionOpt.get());
        }
        if (dnsResolverToOpt.isPresent()) {
            amazonS3ClientBuilder.withClientConfiguration(new ClientConfiguration()
                    .withDnsResolver(host -> {
                        log.trace("Resolving {}", host);
                        return new InetAddress[]{InetAddress.getByName(dnsResolverToOpt.get())};
                    }));
        }

        return amazonS3ClientBuilder.build();
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
