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

package io.dataspray.store.impl;

import com.google.common.base.Charsets;
import io.dataspray.store.CustomerLogger;
import io.dataspray.store.StreamStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.MediaType;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest;
import software.amazon.awssdk.services.sqs.model.CreateQueueResponse;
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;
import software.amazon.awssdk.services.sqs.model.QueueDoesNotExistException;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SqsException;

import java.util.Base64;
import java.util.Map;
import java.util.Optional;

import static io.dataspray.store.impl.LambdaDeployerImpl.LAMBDA_DEFAULT_TIMEOUT;

@Slf4j
@ApplicationScoped
public class SqsStreamStore implements StreamStore {
    public static final String CUSTOMER_QUEUE_PREFIX = "customer-";
    public static final String CUSTOMER_QUEUE_SUFFIX = ".fifo";
    // ARN with queue name wildcard is supported:
    // https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-basic-examples-of-iam-policies.html
    public static final String CUSTOMER_QUEUE_WILDCARD = CUSTOMER_QUEUE_PREFIX + "*" + CUSTOMER_QUEUE_SUFFIX;

    @ConfigProperty(name = "aws.accountId")
    String awsAccountId;
    @ConfigProperty(name = "aws.region")
    String awsRegion;

    @Inject
    SqsClient sqsClient;
    @Inject
    CustomerLogger customerLog;

    @Override
    public void submit(String organizationName, String streamName, Optional<String> messageIdOpt, String messageKey, byte[] messageBytes, MediaType contentType) {
        // Since SQS accepts messages as String, convert each message appropriately
        final String messageStr;
        switch (contentType.toString()) {
            // Text based messages send as string
            case MediaType.APPLICATION_JSON:
            case MediaType.TEXT_PLAIN:
                messageStr = new String(messageBytes, Charsets.UTF_8);
                break;
            // Binary messages send as base64
            default:
                log.warn("Unknown content type {} received from accountId {} queue {}, sending as base64",
                        contentType, organizationName, streamName);
            case "application/octet-stream":
            case "application/avro":
            case "application/protobuf":
                messageStr = Base64.getEncoder().encodeToString(messageBytes);
                break;
        }

        try {
            sendMessage(organizationName, streamName, messageKey, messageIdOpt, messageStr);
        } catch (SqsException ex) {
            if (isQueueDoesNotExist(ex)) {
                // If the queue does not exist, create it
                createStream(organizationName, streamName);

                // and retry
                sendMessage(organizationName, streamName, messageKey, messageIdOpt, messageStr);
            } else {
                throw ex;
            }
        }
    }

    private void sendMessage(String organizationName, String streamName, String groupId, Optional<String> deduplicationId, String messageStr) {
        sqsClient.sendMessage(SendMessageRequest.builder()
                .messageGroupId(groupId)
                .messageDeduplicationId(deduplicationId.orElse(null))
                .queueUrl(getAwsQueueUrl(organizationName, streamName))
                .messageBody(messageStr)
                .build());
    }

    @Override
    public boolean streamExists(String organizationName, String streamName) {
        try {
            sqsClient.getQueueUrl(GetQueueUrlRequest.builder()
                    .queueName(getAwsQueueName(organizationName, streamName))
                    .build());
            return true;
        } catch (SqsException ex) {
            if (isQueueDoesNotExist(ex)) {
                return false;
            }

            throw ex;
        }
    }

    @Override
    public Optional<Map<QueueAttributeName, String>> queueAttributes(String organizationName, String queueName, QueueAttributeName... fetchAttributes) {
        try {
            return Optional.of(sqsClient.getQueueAttributes(GetQueueAttributesRequest.builder()
                    .queueUrl(getAwsQueueUrl(organizationName, queueName))
                    .attributeNames(fetchAttributes)
                    .build()).attributes());
        } catch (SqsException ex) {
            if (isQueueDoesNotExist(ex)) {
                return Optional.empty();
            }
            throw ex;
        }
    }

    @Override
    public void createStream(String organizationName, String streamName) {
        CreateQueueResponse queueResponse = sqsClient.createQueue(CreateQueueRequest.builder()
                .queueName(getAwsQueueName(organizationName, streamName))
                // Docs: https://docs.aws.amazon.com/AWSSimpleQueueService/latest/APIReference/API_CreateQueue.html#API_CreateQueue_RequestParameters
                .attributes(Map.of(
                        QueueAttributeName.FIFO_QUEUE, Boolean.toString(true),
                        QueueAttributeName.CONTENT_BASED_DEDUPLICATION, Boolean.toString(true),
                        // Enables High throughput FIFO queue https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/high-throughput-fifo.html
                        QueueAttributeName.DEDUPLICATION_SCOPE, "messageGroup",
                        QueueAttributeName.FIFO_THROUGHPUT_LIMIT, "perMessageGroupId",
                        // Queue visibility timeout cannot be less than function timeout
                        QueueAttributeName.VISIBILITY_TIMEOUT, Integer.toString(LAMBDA_DEFAULT_TIMEOUT),
                        QueueAttributeName.MESSAGE_RETENTION_PERIOD, String.valueOf(14 * 24 * 60 * 60),
                        QueueAttributeName.RECEIVE_MESSAGE_WAIT_TIME_SECONDS, String.valueOf(20)))
                .build());
        customerLog.info("Created new queue " + streamName, organizationName);
    }

    public String getAwsQueueUrl(String organizationName, String queueName) {
        return "https://sqs." + awsRegion + ".amazonaws.com/"
               + awsAccountId + "/"
               + getAwsQueueName(organizationName, queueName);
    }

    @Override
    public String getAwsQueueName(String organizationName, String queueName) {
        return CUSTOMER_QUEUE_PREFIX + organizationName + "-" + queueName + CUSTOMER_QUEUE_SUFFIX;
    }

    @Override
    public Optional<String> extractStreamNameFromAwsQueueName(String organizationName, String awsQueueName) {
        String prefix = CUSTOMER_QUEUE_PREFIX + organizationName + "-";
        return awsQueueName.startsWith(prefix) && awsQueueName.endsWith(CUSTOMER_QUEUE_SUFFIX)
                ? Optional.of(awsQueueName.substring(prefix.length(), awsQueueName.length() - CUSTOMER_QUEUE_SUFFIX.length()))
                : Optional.empty();
    }

    private boolean isQueueDoesNotExist(SqsException ex) {
        return ex instanceof QueueDoesNotExistException
               // Moto behavior: need to match against the message, is not an instance of QueueDoesNotExistException
               || ex.getMessage().contains("The specified queue does not exist");
    }
}
