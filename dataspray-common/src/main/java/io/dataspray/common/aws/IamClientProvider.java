// SPDX-FileCopyrightText: 2019-2021 Matus Faro <matus@smotana.com>
// SPDX-License-Identifier: Apache-2.0
package io.dataspray.common.aws;

import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.iam.IamClient;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.inject.Singleton;

@Slf4j
@ApplicationScoped
public class IamClientProvider {

    @Inject
    AwsCredentialsProvider awsCredentialsProvider;

    @Singleton
    public IamClient getIamClient() {
        log.debug("Opening IAM v2 client");
        return IamClient.builder()
                .credentialsProvider(awsCredentialsProvider)
                .region(Region.AWS_GLOBAL)
                .build();
    }
}
