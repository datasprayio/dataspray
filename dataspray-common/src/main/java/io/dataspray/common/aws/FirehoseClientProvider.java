// SPDX-FileCopyrightText: 2019-2021 Matus Faro <matus@smotana.com>
// SPDX-License-Identifier: Apache-2.0
package io.dataspray.common.aws;

import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.services.firehose.FirehoseClient;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.inject.Singleton;

@Slf4j
@ApplicationScoped
public class FirehoseClientProvider {

    @Inject
    AwsCredentialsProvider awsCredentialsProvider;

    @Singleton
    public FirehoseClient getFirehoseClient() {
        log.debug("Opening Firehose v2 client");
        return FirehoseClient.builder()
                .credentialsProvider(awsCredentialsProvider)
                .build();
    }
}
