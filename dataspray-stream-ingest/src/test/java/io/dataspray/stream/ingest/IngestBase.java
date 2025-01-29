/*
 * Copyright 2024 Matus Faro
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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import io.dataspray.common.DeployEnvironment;
import io.dataspray.common.json.GsonUtil;
import io.dataspray.common.test.aws.AbstractLambdaTest;
import io.dataspray.common.test.aws.MotoInstance;
import io.dataspray.common.test.aws.MotoLifecycleManager;
import io.dataspray.singletable.SingleTable;
import io.dataspray.store.SingleTableProvider;
import io.dataspray.store.TopicStore;
import io.dataspray.store.TopicStore.Batch;
import io.dataspray.store.TopicStore.BatchRetention;
import io.dataspray.store.TopicStore.Stream;
import io.dataspray.store.TopicStore.Topic;
import io.dataspray.store.impl.DynamoTopicStore;
import io.dataspray.store.impl.FirehoseS3AthenaBatchStore;
import io.dataspray.store.impl.LambdaDeployerImpl;
import io.dataspray.store.impl.SqsStreamStore;
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
import java.util.Optional;

import static io.dataspray.singletable.TableType.Gsi;
import static io.dataspray.singletable.TableType.Primary;
import static io.dataspray.store.impl.FirehoseS3AthenaBatchStore.*;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON_TYPE;
import static org.junit.jupiter.api.Assertions.assertEquals;

@Slf4j
@QuarkusTestResource(MotoLifecycleManager.class)
public abstract class IngestBase extends AbstractLambdaTest {

    MotoInstance motoInstance;

    protected abstract DynamoDbClient getDynamoClient();

    protected abstract S3Client getS3Client();

    protected abstract SqsClient getSqsClient();

    protected abstract FirehoseClient getFirehoseClient();

    protected abstract CognitoIdentityProviderClient getCognitoClient();

    @Test
    public void test() throws Exception {
        String topicName = "registration";
        String messageKey = "message-key";
        String messageId = "message-id";
        String bucketName = "io-dataspray-etl";
        String firehoseName = "dataspray-ingest-etl";

        // Setup Target store
        SingleTable singleTable = SingleTable.builder()
                .tablePrefix(SingleTableProvider.TABLE_PREFIX_DEFAULT)
                .overrideGson(GsonUtil.get())
                .build();
        DynamoTopicStore dynamoTargetStore = new DynamoTopicStore();
        dynamoTargetStore.dynamo = getDynamoClient();
        dynamoTargetStore.singleTable = singleTable;
        dynamoTargetStore.init();

        // Setup Customer Dynamo store
        String customerTableName = LambdaDeployerImpl.CUSTOMER_FUN_DYNAMO_OR_ROLE_NAME_PREFIX_GETTER.apply(DeployEnvironment.TEST) + getOrganizationName();
        SingleTable singleTableCustomer = SingleTable.builder()
                .tableName(customerTableName)
                .overrideGson(GsonUtil.get())
                .build();
        singleTableCustomer.createTableIfNotExists(getDynamoClient(), 0, 1);

        // Setup topic to perform batch and stream processing
        TopicStore.Topics topics = dynamoTargetStore.getTopics(getOrganizationName(), true);
        dynamoTargetStore.updateTopic(getOrganizationName(), topicName, Topic.builder()
                        .batch(Batch.builder()
                                .retention(BatchRetention.YEAR).build())
                        .streams(ImmutableList.of(
                                Stream.builder()
                                        .name(topicName)
                                        .build()))
                        .store(TopicStore.Store.builder()
                                .keys(ImmutableSet.of(
                                        TopicStore.Key.builder()
                                                .type(Primary)
                                                .indexNumber(0)
                                                .pkParts(ImmutableList.of("someString"))
                                                .skParts(ImmutableList.of("someInt"))
                                                .rangePrefix("data")
                                                .build(),
                                        TopicStore.Key.builder()
                                                .type(Gsi)
                                                .indexNumber(1)
                                                .pkParts(ImmutableList.of("someString", "someInt"))
                                                .skParts(ImmutableList.of("someString"))
                                                .rangePrefix("dataGsi")
                                                .build()))

                                .ttlInSec(1_000)
                                .whitelist(ImmutableSet.of())
                                .blacklist(ImmutableSet.of())
                                .build())
                        .build(),
                Optional.of(topics.getVersion()));

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
                        .prefix(FirehoseS3AthenaBatchStore.ETL_BUCKET_PREFIX)
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
                .path("/v1/organization/" + getOrganizationName() + "/topic/" + topicName + "/message")
                .query(Map.of(
                        "messageKey", List.of(messageKey),
                        "messageId", List.of(messageId)))
                .contentType(APPLICATION_JSON_TYPE)
                .body(body)
                .build())
                .assertStatusCode(Response.Status.NO_CONTENT.getStatusCode());

        // Assert message is in queue
        String queueUrl = "https://sqs." + motoInstance.getRegion() + ".amazonaws.com/"
                          + motoInstance.getAwsAccountId() + "/"
                          + SqsStreamStore.CUSTOMER_QUEUE_PREFIX + getOrganizationName() + "-" + topicName
                          + SqsStreamStore.CUSTOMER_QUEUE_SUFFIX;
        log.info("Asserting message from queue {}", queueUrl);
        List<Message> messages = getSqsClient().receiveMessage(ReceiveMessageRequest.builder()
                .queueUrl(queueUrl)
                .maxNumberOfMessages(10).build()).messages();
        assertEquals(1, messages.size());
        assertEquals(bodyStr, messages.getFirst().body());

        // Assert message is in S3
        ListObjectsV2Response objects = getS3Client().listObjectsV2(ListObjectsV2Request.builder()
                .bucket(bucketName)
                .build());
        assertEquals(1, objects.keyCount());
        String objectKey = objects.contents().getFirst().key();
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
                .put(ETL_MESSAGE_KEY, messageKey)
                .put(ETL_MESSAGE_ID, messageId)
                .put(ETL_PARTITION_KEY_RETENTION, BatchRetention.YEAR.name())
                .put(ETL_PARTITION_KEY_ORGANIZATION, getOrganizationName())
                .put(ETL_PARTITION_KEY_TOPIC, topicName)
                .build(), objectJson);
    }
}
