// SPDX-FileCopyrightText: 2019-2021 Matus Faro <matus@smotana.com>
// SPDX-License-Identifier: Apache-2.0
package io.dataspray.common.aws;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;

import java.util.Optional;

/**
 * Extends AWS credentials provider to:
 * - Allow anonymous credentials using Quarkus config 'aws.credentials.anonymous'
 * - Allow specifying credentials using Quarkus config 'aws.credentials.accessKey' and 'aws.credentials.secretKey'
 * - Uses default chain in the absence of any Quarkus config
 */
@Slf4j
@ApplicationScoped
public class ConfigAwsCredentialsProvider {

    /**
     * Underlying version-agnostic credentials provider.
     */
    @ApplicationScoped
    AwsCredentialsProvider getCredentialsProvider(
            @ConfigProperty(name = "aws.credentials.anonymous", defaultValue = "false") boolean isAnonymous,
            @ConfigProperty(name = "aws.credentials.accessKey") Optional<String> awsAccessKeyOpt,
            @ConfigProperty(name = "aws.credentials.secretKey") Optional<String> awsSecretKeyOpt) {

        if (awsAccessKeyOpt.isPresent() && awsSecretKeyOpt.isPresent()) {
            log.debug("Using config provided AWS key pair");
            return software.amazon.awssdk.auth.credentials.StaticCredentialsProvider.create(AwsBasicCredentials.create(
                    awsAccessKeyOpt.get(), awsSecretKeyOpt.get()));
        } else if (isAnonymous) {
            log.debug("Using anonymous AWS credentials");
            return AnonymousCredentialsProvider.create();
        } else {
            log.debug("Using default chain for AWS Credentials");
            return DefaultCredentialsProvider.create();
        }
    }
}

