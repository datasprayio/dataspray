// SPDX-FileCopyrightText: 2019-2021 Matus Faro <matus@smotana.com>
// SPDX-License-Identifier: Apache-2.0
package io.dataspray.common.aws;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.services.firehose.FirehoseClient;

@Slf4j
@ApplicationScoped
public class FirehoseClientProvider {

    @Inject
    AwsCredentialsProvider awsCredentialsProvider;
    @Inject
    SdkHttpClient sdkHttpClient;

    @Singleton
    public FirehoseClient getFirehoseClient() {
        log.debug("Opening Firehose v2 client");
        return FirehoseClient.builder()
                .credentialsProvider(awsCredentialsProvider)
                .httpClient(sdkHttpClient)
                .build();
    }
}
