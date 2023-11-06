/*
 * Copyright 2023 Matus Faro
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package io.dataspray.common.test.aws;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.testcontainers.containers.GenericContainer;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.firehose.FirehoseClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.sqs.SqsClient;

import java.net.URI;

@Data
@AllArgsConstructor
public class MotoInstance {
    long awsAccountId;
    String region;
    String awsAccessKey;
    String awsSecretKey;
    int port;
    String endpoint;
    @SuppressWarnings("rawtypes")
    GenericContainer motoContainer;

    public S3Client getS3Client() {
        return S3Client.builder()
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(
                        getAwsAccessKey(),
                        getAwsSecretKey())))
                .endpointOverride(URI.create(getEndpoint()))
                .region(Region.of(getRegion()))
                .httpClient(UrlConnectionHttpClient.create())
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(true).build())
                .build();
    }

    public SqsClient getSqsClient() {
        return SqsClient.builder()
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(
                        getAwsAccessKey(),
                        getAwsSecretKey())))
                .endpointOverride(URI.create(getEndpoint()))
                .region(Region.of(getRegion()))
                .httpClient(UrlConnectionHttpClient.create())
                .build();
    }

    public FirehoseClient getFirehoseClient() {
        return FirehoseClient.builder()
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(
                        getAwsAccessKey(),
                        getAwsSecretKey())))
                .endpointOverride(URI.create(getEndpoint()))
                .region(Region.of(getRegion()))
                .httpClient(UrlConnectionHttpClient.create())
                .build();
    }

    public DynamoDbClient getDynamoClient() {
        return DynamoDbClient.builder()
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(
                        getAwsAccessKey(),
                        getAwsSecretKey())))
                .endpointOverride(URI.create(getEndpoint()))
                .region(Region.of(getRegion()))
                .httpClient(UrlConnectionHttpClient.create())
                .build();
    }

    public CognitoIdentityProviderClient getCognitoClient() {
        return CognitoIdentityProviderClient.builder()
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(
                        getAwsAccessKey(),
                        getAwsSecretKey())))
                .endpointOverride(URI.create(getEndpoint()))
                .region(Region.of(getRegion()))
                .httpClient(UrlConnectionHttpClient.create())
                .build();
    }
}
