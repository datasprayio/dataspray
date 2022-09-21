// SPDX-FileCopyrightText: 2019-2021 Matus Faro <matus@smotana.com>
// SPDX-License-Identifier: Apache-2.0
package io.dataspray.common.aws;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSCredentialsProviderChain;
import com.amazonaws.auth.EC2ContainerCredentialsProviderWrapper;
import com.amazonaws.auth.EnvironmentVariableCredentialsProvider;
import com.amazonaws.auth.SystemPropertiesCredentialsProvider;
import com.amazonaws.auth.WebIdentityTokenCredentialsProvider;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;

import javax.enterprise.context.ApplicationScoped;
import java.util.Optional;

/**
 * Extends DefaultAWSCredentialsProviderChain to load access keys from Quarkus config as well.
 *
 * Implements both AWS SDK v1 and v2 credentials provider.
 */
@Slf4j
@ApplicationScoped
public class ConfigAwsCredentialsProvider extends AWSCredentialsProviderChain implements AwsCredentialsProvider {

    ConfigAwsCredentialsProvider(
            @ConfigProperty(name = "aws.accessKey") Optional<String> awsAccessKeyOpt,
            @ConfigProperty(name = "aws.secretKey") Optional<String> awsSecretKeyOpt) {
        super(new AWSCredentialsProvider() {
                  @Override
                  public AWSCredentials getCredentials() {
                      return new AWSCredentials() {
                          @Override
                          public String getAWSAccessKeyId() {
                              return awsAccessKeyOpt.orElse(null);
                          }

                          @Override
                          public String getAWSSecretKey() {
                              return awsSecretKeyOpt.orElse(null);
                          }
                      };
                  }

                  @Override
                  public void refresh() {
                      // No-op
                  }
              },
                new EnvironmentVariableCredentialsProvider(),
                new SystemPropertiesCredentialsProvider(),
                new ProfileCredentialsProvider(),
                WebIdentityTokenCredentialsProvider.create(),
                new EC2ContainerCredentialsProviderWrapper());
    }

    @Override
    public AwsCredentials resolveCredentials() {
        AWSCredentials credentials = getCredentials();
        return AwsBasicCredentials.create(
                credentials.getAWSAccessKeyId(),
                credentials.getAWSSecretKey());
    }
}
