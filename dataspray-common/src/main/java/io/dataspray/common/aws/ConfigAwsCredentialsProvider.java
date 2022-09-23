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
import io.quarkus.arc.DefaultBean;
import io.quarkus.arc.Priority;
import io.quarkus.arc.properties.IfBuildProperty;
import io.quarkus.arc.properties.UnlessBuildProperty;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Alternative;

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
     * SDK V1 Credentials provider
     */
    @ApplicationScoped
    AWSCredentialsProvider getCredentialsProviderSdk1(SdkAgnosticAwsCredentialsProvider agnosticProvider) {
        return agnosticProvider;
    }

    /**
     * SDK V2 Credentials provider
     */
    @ApplicationScoped
    AwsCredentialsProvider getCredentialsProviderSdk2(SdkAgnosticAwsCredentialsProvider agnosticProvider) {
        return agnosticProvider;
    }

    /**
     * Underlying version-agnostic credentials provider using default chain.
     */
    @DefaultBean
    @ApplicationScoped
    SdkAgnosticAwsCredentialsProvider getCredentialsProviderAgnosticConfig() {
        return new SdkAgnosticAwsCredentialsProvider(new AWSCredentialsProviderChain(
                new EnvironmentVariableCredentialsProvider(),
                new SystemPropertiesCredentialsProvider(),
                new ProfileCredentialsProvider(),
                WebIdentityTokenCredentialsProvider.create(),
                new EC2ContainerCredentialsProviderWrapper()));
    }

    /**
     * Underlying version-agnostic credentials provider using Quarkus supplied access and secret key.
     */
    @Alternative
    @Priority(1)
    @ApplicationScoped
    @UnlessBuildProperty(name = "aws.credentials.accessKey", stringValue = "", enableIfMissing = false)
    @UnlessBuildProperty(name = "aws.credentials.secretKey", stringValue = "", enableIfMissing = false)
    @UnlessBuildProperty(name = "aws.credentials.anonymous", stringValue = "true", enableIfMissing = true)
    SdkAgnosticAwsCredentialsProvider getCredentialsProviderAgnosticConfig(
            @ConfigProperty(name = "aws.credentials.accessKey") String awsAccessKey,
            @ConfigProperty(name = "aws.credentials.secretKey") String awsSecretKey) {
        return new SdkAgnosticAwsCredentialsProvider(new AWSStaticCredentialsProvider(new BasicAWSCredentials(
                awsAccessKey, awsSecretKey)));
    }

    /**
     * Underlying version-agnostic credentials provider using anonymous credentials.
     */
    @Alternative
    @Priority(1)
    @ApplicationScoped
    @IfBuildProperty(name = "aws.credentials.anonymous", stringValue = "true", enableIfMissing = false)
    SdkAgnosticAwsCredentialsProvider getCredentialsProviderAgnosticAnonymous() {
        return new SdkAgnosticAwsCredentialsProvider(new AWSStaticCredentialsProvider(new AnonymousAWSCredentials()));
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

