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

package io.dataspray.store;

import com.google.common.base.Charsets;
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

import java.util.Base64;
import java.util.Base64.Encoder;
import java.util.Map;
import java.util.Optional;

import static io.dataspray.store.LambdaDeployerImpl.LAMBDA_DEFAULT_TIMEOUT;

@Slf4j
@ApplicationScoped
public class SqsQueueStore implements QueueStore {
    public static final String CUSTOMER_QUEUE_PREFIX = "customer-";
    // ARN with queue name wildcard is supported:
    // https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-basic-examples-of-iam-policies.html
    public static final String CUSTOMER_QUEUE_WILDCARD = CUSTOMER_QUEUE_PREFIX + "*";

    @ConfigProperty(name = "aws.accountId")
    String awsAccountId;
    @ConfigProperty(name = "aws.region")
    String awsRegion;

    @Inject
    SqsClient sqsClient;

    private final Encoder base64Encoder = Base64.getEncoder();

    @Override
    public void submit(String customerId, String queueName, byte[] messageBytes, MediaType contentType) {
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
                        contentType, customerId, queueName);
            case "application/octet-stream":
            case "application/avro":
            case "application/protobuf":
                messageStr = base64Encoder.encodeToString(messageBytes);
                break;
        }

        sqsClient.sendMessage(SendMessageRequest.builder()
                .queueUrl(getAwsQueueUrl(customerId, queueName))
                .messageBody(messageStr)
                .build());
    }

    @Override
    public boolean queueExists(String customerId, String queueName) {
        try {
            sqsClient.getQueueUrl(GetQueueUrlRequest.builder()
                    .queueName(getAwsQueueName(customerId, queueName))
                    .build());
            return true;
        } catch (QueueDoesNotExistException ex) {
            return false;
        }
    }

    @Override
    public Optional<Map<QueueAttributeName, String>> queueAttributes(String customerId, String queueName, QueueAttributeName... fetchAttributes) {
        try {
            return Optional.of(sqsClient.getQueueAttributes(GetQueueAttributesRequest.builder()
                    .queueUrl(getAwsQueueUrl(customerId, queueName))
                    .attributeNames(fetchAttributes)
                    .build()).attributes());
        } catch (QueueDoesNotExistException ex) {
            return Optional.empty();
        }
    }

    @Override
    public void createQueue(String customerId, String queueName) {
        CreateQueueResponse queueResponse = sqsClient.createQueue(CreateQueueRequest.builder()
                .queueName(getAwsQueueName(customerId, queueName))
                .attributes(Map.of(
                        // Queue visibility timeout cannot be less than function timeout
                        QueueAttributeName.VISIBILITY_TIMEOUT, Integer.toString(LAMBDA_DEFAULT_TIMEOUT),
                        QueueAttributeName.MESSAGE_RETENTION_PERIOD, String.valueOf(14 * 24 * 60 * 60)))
                .build());
    }

    public String getAwsQueueUrl(String customerId, String queueName) {
        return "https://sqs." + awsRegion + ".amazonaws.com/"
               + awsAccountId + "/"
               + getAwsQueueName(customerId, queueName);
    }

    @Override
    public String getAwsQueueName(String customerId, String queueName) {
        return CUSTOMER_QUEUE_PREFIX + customerId + "-" + queueName;
    }

    @Override
    public Optional<String> extractQueueNameFromAwsQueueName(String customerId, String awsQueueName) {
        String prefix = CUSTOMER_QUEUE_PREFIX + customerId + "-";
        return awsQueueName.startsWith(prefix)
                ? Optional.of(awsQueueName.substring(prefix.length()))
                : Optional.empty();
    }
}
