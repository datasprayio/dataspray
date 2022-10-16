package io.dataspray.store;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableMap;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest;
import software.amazon.awssdk.services.sqs.model.CreateQueueResponse;
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesRequest;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;
import software.amazon.awssdk.services.sqs.model.QueueDoesNotExistException;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.core.MediaType;
import java.util.Base64;
import java.util.Base64.Encoder;
import java.util.Map;
import java.util.Optional;

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
    public void submit(String accountId, String queueName, byte[] messageBytes, MediaType contentType) {
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
                        contentType, accountId, queueName);
            case "application/octet-stream":
            case "application/avro":
            case "application/protobuf":
                messageStr = base64Encoder.encodeToString(messageBytes);
                break;
        }

        sqsClient.sendMessage(SendMessageRequest.builder()
                .queueUrl(getAwsQueueUrl(accountId, queueName))
                .messageBody(messageStr)
                .build());
    }

    @Override
    public Optional<Map<QueueAttributeName, String>> queueAttributes(String accountId, String queueName, QueueAttributeName... fetchAttributes) {
        try {
            return Optional.of(sqsClient.getQueueAttributes(GetQueueAttributesRequest.builder()
                    .queueUrl(getAwsQueueUrl(accountId, queueName))
                    .attributeNames(fetchAttributes)
                    .build()).attributes());
        } catch (QueueDoesNotExistException ex) {
            return Optional.empty();
        }
    }

    @Override
    public void createQueue(String accountId, String queueName) {
        CreateQueueResponse queueResponse = sqsClient.createQueue(CreateQueueRequest.builder()
                .queueName(getAwsQueueName(accountId, queueName))
                .attributes(ImmutableMap.of(
                        QueueAttributeName.MESSAGE_RETENTION_PERIOD, String.valueOf(14 * 24 * 60 * 60)))
                .build());
    }

    public String getAwsQueueUrl(String accountId, String queueName) {
        return "https://sqs." + awsRegion + ".amazonaws.com/"
                + awsAccountId + "/"
                + getAwsQueueName(accountId, queueName);
    }

    @Override
    public String getAwsQueueName(String accountId, String queueName) {
        return CUSTOMER_QUEUE_PREFIX + accountId + "-" + queueName;
    }

    @Override
    public Optional<String> extractQueueNameFromAwsQueueName(String accountId, String awsQueueName) {
        String prefix = CUSTOMER_QUEUE_PREFIX + accountId + "-";
        return awsQueueName.startsWith(prefix)
                ? Optional.of(awsQueueName.substring(prefix.length()))
                : Optional.empty();
    }
}
