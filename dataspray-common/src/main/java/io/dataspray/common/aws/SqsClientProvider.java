// SPDX-FileCopyrightText: 2019-2021 Matus Faro <matus@smotana.com>
// SPDX-License-Identifier: Apache-2.0
package io.dataspray.common.aws;

import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.SqsClientBuilder;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.inject.Singleton;

@Slf4j
@ApplicationScoped
public class SqsClientProvider {

    @Inject
    AwsCredentialsProvider awsCredentialsProvider;

    @Singleton
    public SqsClient getLambdaClient() {
        log.debug("Opening SQS v2 client");

        ApacheHttpClient.Builder httpClientBuilder = ApacheHttpClient.builder();
        SqsClientBuilder builder = SqsClient.builder()
                .credentialsProvider(awsCredentialsProvider)
                .httpClientBuilder(httpClientBuilder);

        return builder.build();
    }
}
