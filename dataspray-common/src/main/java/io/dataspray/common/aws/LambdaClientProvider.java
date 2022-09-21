// SPDX-FileCopyrightText: 2019-2021 Matus Faro <matus@smotana.com>
// SPDX-License-Identifier: Apache-2.0
package io.dataspray.common.aws;

import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.LambdaClientBuilder;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.inject.Singleton;

@Slf4j
@ApplicationScoped
public class LambdaClientProvider {

    @Inject
    ConfigAwsCredentialsProvider awsCredentialsProvider;

    @Singleton
    public LambdaClient getLambdaClient() {
        log.debug("Opening Lambda v2 client");

        ApacheHttpClient.Builder httpClientBuilder = ApacheHttpClient.builder();
        LambdaClientBuilder builder = LambdaClient.builder()
                .credentialsProvider(awsCredentialsProvider)
                .httpClientBuilder(httpClientBuilder);

        return builder.build();
    }
}
