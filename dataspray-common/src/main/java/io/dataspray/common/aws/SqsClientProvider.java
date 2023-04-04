// SPDX-FileCopyrightText: 2019-2021 Matus Faro <matus@smotana.com>
// SPDX-License-Identifier: Apache-2.0
package io.dataspray.common.aws;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.services.sqs.SqsClient;

@Slf4j
@ApplicationScoped
public class SqsClientProvider {

    @Inject
    AwsCredentialsProvider awsCredentialsProvider;

    @Singleton
    public SqsClient getLambdaClient() {
        log.debug("Opening SQS v2 client");
        return SqsClient.builder()
                .credentialsProvider(awsCredentialsProvider).build();
    }
}
