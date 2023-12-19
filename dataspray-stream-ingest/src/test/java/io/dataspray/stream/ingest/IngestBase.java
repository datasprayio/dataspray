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

package io.dataspray.stream.ingest;

import com.google.common.collect.ImmutableMap;
import io.dataspray.common.json.GsonUtil;
import io.dataspray.common.test.aws.AbstractLambdaTest;
import io.dataspray.common.test.aws.MotoLifecycleManager;
import io.dataspray.store.AccountStore;
import io.dataspray.store.impl.DynamoApiGatewayApiAccessStore;
import io.dataspray.store.impl.FirehoseS3AthenaEtlStore;
import io.dataspray.store.impl.SqsQueueStore;
import io.quarkus.test.common.QuarkusTestResource;
import jakarta.ws.rs.HttpMethod;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.firehose.FirehoseClient;
import software.amazon.awssdk.services.firehose.model.BufferingHints;
import software.amazon.awssdk.services.firehose.model.CompressionFormat;
import software.amazon.awssdk.services.firehose.model.CreateDeliveryStreamRequest;
import software.amazon.awssdk.services.firehose.model.DeliveryStreamType;
import software.amazon.awssdk.services.firehose.model.ExtendedS3DestinationConfiguration;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.BucketAlreadyOwnedByYouException;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;

import java.io.InputStreamReader;
import java.util.List;
import java.util.Map;

import static io.dataspray.store.impl.FirehoseS3AthenaEtlStore.*;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON_TYPE;
import static org.junit.jupiter.api.Assertions.assertEquals;

@Slf4j
@QuarkusTestResource(MotoLifecycleManager.class)
public abstract class IngestBase extends AbstractLambdaTest {

    protected abstract DynamoDbClient getDynamoClient();

    protected abstract S3Client getS3Client();

    protected abstract SqsClient getSqsClient();

    protected abstract FirehoseClient getFirehoseClient();

    protected abstract CognitoIdentityProviderClient getCognitoClient();

    @Test
    public void test() throws Exception {
        String customerId = "B41B7CC9-BD31-46E3-8CD1-52B6A2BC203C";
        String targetId = "registration";
        String bucketName = "io-dataspray-etl";
        String firehoseName = "dataspray-ingest-etl";

        // Setup account
        DynamoApiGatewayApiAccessStore apiAccessStore = new DynamoApiGatewayApiAccessStore();

        // Setup code bucket
        try {
            getS3Client().createBucket(CreateBucketRequest.builder()
                    .bucket(bucketName)
                    .build());
        } catch (BucketAlreadyOwnedByYouException ex) {
            // Already exists and is ours
        }

        // Setup firehose
        getFirehoseClient().createDeliveryStream(CreateDeliveryStreamRequest.builder()
                .deliveryStreamName(firehoseName)
                .deliveryStreamType(DeliveryStreamType.DIRECT_PUT)
                .extendedS3DestinationConfiguration(ExtendedS3DestinationConfiguration.builder()
                        .bucketARN("arn:aws:s3:::" + bucketName)
                        .compressionFormat(CompressionFormat.ZIP)
                        .prefix(FirehoseS3AthenaEtlStore.ETL_BUCKET_PREFIX)
                        .bufferingHints(BufferingHints.builder()
                                .intervalInSeconds(0).build())
                        .build())
                .build());

        // Setup data
        Map<String, String> body = Map.of("key", "value");
        String bodyStr = GsonUtil.get().toJson(body);

        // Submit data to Ingest Resource
        request(Given.builder()
                .method(HttpMethod.POST)
                .path("/account/" + customerId + "/target/" + targetId + "/message")
                .contentType(APPLICATION_JSON_TYPE)
                .body(body)
                .build())
                .assertStatusCode(Response.Status.NO_CONTENT.getStatusCode());

        // Assert message is in queue
        String queueUrl = "https://sqs.us-east-1.amazonaws.com/479823472389/"
                          + SqsQueueStore.CUSTOMER_QUEUE_PREFIX + customerId + "-" + targetId;
        List<Message> messages = getSqsClient().receiveMessage(ReceiveMessageRequest.builder()
                .queueUrl(queueUrl)
                .maxNumberOfMessages(10).build()).messages();
        assertEquals(1, messages.size());
        assertEquals(bodyStr, messages.get(0).body());

        // Assert message is in S3
        ListObjectsV2Response objects = getS3Client().listObjectsV2(ListObjectsV2Request.builder()
                .bucket(bucketName)
                .build());
        assertEquals(1, objects.keyCount());
        String objectKey = objects.contents().get(0).key();
        log.info("Found object key {}", objectKey);
        ResponseInputStream<GetObjectResponse> objectStream = getS3Client().getObject(GetObjectRequest.builder()
                .bucket(bucketName)
                .key(objectKey)
                .build());
        Map<String, String> objectJson;
        try (InputStreamReader inputStreamReader = new InputStreamReader(objectStream)) {
            objectJson = GsonUtil.get().fromJson(inputStreamReader, Map.class);
        }
        log.info("Object content {}", objectJson);
        assertEquals(ImmutableMap.builder()
                .putAll(body)
                .put(ETL_PARTITION_KEY_RETENTION, AccountStore.EtlRetention.DEFAULT.name())
                .put(ETL_PARTITION_KEY_ACCOUNT, customerId)
                .put(ETL_PARTITION_KEY_TARGET, targetId)
                .build(), objectJson);
    }
}
