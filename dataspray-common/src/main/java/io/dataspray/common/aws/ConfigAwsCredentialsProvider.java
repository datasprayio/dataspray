// SPDX-FileCopyrightText: 2019-2021 Matus Faro <matus@smotana.com>
// SPDX-License-Identifier: Apache-2.0
package io.dataspray.common.aws;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSCredentialsProviderChain;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.AnonymousAWSCredentials;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.auth.EC2ContainerCredentialsProviderWrapper;
import com.amazonaws.auth.EnvironmentVariableCredentialsProvider;
import com.amazonaws.auth.SystemPropertiesCredentialsProvider;
import com.amazonaws.auth.WebIdentityTokenCredentialsProvider;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;

import javax.enterprise.context.ApplicationScoped;
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

        private final AWSCredentialsProvider provider;

        public SdkAgnosticAwsCredentialsProvider(AWSCredentialsProvider provider) {

            this.provider = provider;
        }

        @Override
        public AWSCredentials getCredentials() {
            return provider.getCredentials();
        }

        @Override
        public void refresh() {
            provider.refresh();
        }

        @Override
        public AwsCredentials resolveCredentials() {
            AWSCredentials credentials = provider.getCredentials();

            String awsAccessKeyId = credentials.getAWSAccessKeyId();
            String awsSecretKey = credentials.getAWSSecretKey();
            return new AwsCredentials() {
                @Override
                public String accessKeyId() {
                    return awsAccessKeyId;
                }

                @Override
                public String secretAccessKey() {
                    return awsSecretKey;
                }
            };
        }
    }
}

