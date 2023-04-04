// SPDX-FileCopyrightText: 2019-2021 Matus Faro <matus@smotana.com>
// SPDX-License-Identifier: Apache-2.0
package io.dataspray.common.aws;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSCredentialsProviderChain;
import com.amazonaws.auth.AWSSessionCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.AnonymousAWSCredentials;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.auth.BasicSessionCredentials;
import com.amazonaws.auth.EC2ContainerCredentialsProviderWrapper;
import com.amazonaws.auth.EnvironmentVariableCredentialsProvider;
import com.amazonaws.auth.SystemPropertiesCredentialsProvider;
import com.amazonaws.auth.WebIdentityTokenCredentialsProvider;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;

import java.util.Optional;

/**
 * Extends AWS credentials provider to:
 * - Allow anonymous credentials using Quarkus config 'aws.credentials.anonymous'
 * - Allow specifying credentials using Quarkus config 'aws.credentials.accessKey' and 'aws.credentials.secretKey'
 * - Uses default chain in the absence of any Quarkus config
 * - Fulfills both AWS SDK v1 and v2 credentials provider
 */
@Slf4j
@ApplicationScoped
public class ConfigAwsCredentialsProvider {

    /**
     * Underlying version-agnostic credentials provider.
     */
    @ApplicationScoped
    SdkAgnosticAwsCredentialsProvider getCredentialsProviderAgnostic(
            @ConfigProperty(name = "aws.credentials.anonymous", defaultValue = "false") boolean isAnonymous,
            @ConfigProperty(name = "aws.credentials.accessKey") Optional<String> awsAccessKeyOpt,
            @ConfigProperty(name = "aws.credentials.secretKey") Optional<String> awsSecretKeyOpt) {

        if (isAnonymous) {
            log.debug("Using anonymous AWS credentials");
            return new SdkAgnosticAwsCredentialsProvider(new AWSStaticCredentialsProvider(new AnonymousAWSCredentials()));
        } else if (awsAccessKeyOpt.isPresent() && awsSecretKeyOpt.isPresent()) {
            log.debug("Using config provided AWS key pair");
            return new SdkAgnosticAwsCredentialsProvider(new AWSStaticCredentialsProvider(new BasicAWSCredentials(
                    awsAccessKeyOpt.get(), awsSecretKeyOpt.get())));
        } else {
            log.debug("Using default chain for AWS Credentials");
            return new SdkAgnosticAwsCredentialsProvider(new AWSCredentialsProviderChain(
                    new EnvironmentVariableCredentialsProvider(),
                    new SystemPropertiesCredentialsProvider(),
                    new ProfileCredentialsProvider(),
                    WebIdentityTokenCredentialsProvider.create(),
                    new EC2ContainerCredentialsProviderWrapper()));
        }
    }

    /**
     * Implementation of version-agnostic credentials provider.
     */
    public static class SdkAgnosticAwsCredentialsProvider implements AWSCredentialsProvider, AwsCredentialsProvider {

        private final Optional<AwsCredentialsProvider> providerV1Opt;
        private final Optional<AWSCredentialsProvider> providerV2Opt;

        public SdkAgnosticAwsCredentialsProvider(AwsCredentialsProvider provider) {
            this.providerV1Opt = Optional.of(provider);
            this.providerV2Opt = Optional.empty();
        }

        public SdkAgnosticAwsCredentialsProvider(AWSCredentialsProvider provider) {
            this.providerV1Opt = Optional.empty();
            this.providerV2Opt = Optional.of(provider);
        }

        public SdkAgnosticAwsCredentialsProvider(AwsCredentialsProvider providerV1, AWSCredentialsProvider providerV2) {
            this.providerV1Opt = Optional.of(providerV1);
            this.providerV2Opt = Optional.of(providerV2);
        }

        @Override
        public AwsCredentials resolveCredentials() {
            return providerV1Opt.map(AwsCredentialsProvider::resolveCredentials)
                    .or(() -> providerV2Opt.map(AWSCredentialsProvider::getCredentials)
                            .map(credentialsV2 -> {
                                if (credentialsV2 instanceof AWSSessionCredentials) {
                                    return AwsSessionCredentials.create(
                                            credentialsV2.getAWSAccessKeyId(),
                                            credentialsV2.getAWSSecretKey(),
                                            ((AWSSessionCredentials) credentialsV2).getSessionToken());
                                } else {
                                    return new AwsCredentials() {
                                        @Override
                                        public String accessKeyId() {
                                            return credentialsV2.getAWSAccessKeyId();
                                        }

                                        @Override
                                        public String secretAccessKey() {
                                            return credentialsV2.getAWSSecretKey();
                                        }
                                    };
                                }
                            }))
                    .orElseGet(() -> AnonymousCredentialsProvider.create().resolveCredentials());
        }

        @Override
        public AWSCredentials getCredentials() {
            return providerV2Opt.map(AWSCredentialsProvider::getCredentials)
                    .or(() -> providerV1Opt.map(AwsCredentialsProvider::resolveCredentials)
                            .map(credentialsV1 -> {
                                if (credentialsV1 instanceof AwsSessionCredentials) {
                                    return new BasicSessionCredentials(
                                            credentialsV1.accessKeyId(),
                                            credentialsV1.secretAccessKey(),
                                            ((AwsSessionCredentials) credentialsV1).sessionToken());
                                } else {
                                    return new AWSCredentials() {
                                        @Override
                                        public String getAWSAccessKeyId() {
                                            return credentialsV1.accessKeyId();
                                        }

                                        @Override
                                        public String getAWSSecretKey() {
                                            return credentialsV1.secretAccessKey();
                                        }
                                    };
                                }
                            }))
                    .orElseGet(AnonymousAWSCredentials::new);
        }

        @Override
        public void refresh() {
            providerV2Opt.ifPresent(AWSCredentialsProvider::refresh);
        }
    }
}

