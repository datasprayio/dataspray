package io.dataspray.store;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableMap;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest;
import software.amazon.awssdk.services.sqs.model.CreateQueueResponse;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.core.MediaType;
import java.util.Base64;
import java.util.Base64.Encoder;

@Slf4j
@ApplicationScoped
public class SqsQueueStore implements QueueStore {
    public static final String CUSTOMER_QUEUE_PREFIX = "customer-";

    @ConfigProperty(name = "queue.region")
    String awsRegion;
    @ConfigProperty(name = "aws.accountId")
    String awsAccountId;

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
                .queueUrl(getQueueUrl(accountId, queueName))
                .messageBody(messageStr)
                .build());
    }

    @Override
    public void createQueue(String accountId, String queueName) {
        CreateQueueResponse queueResponse = sqsClient.createQueue(CreateQueueRequest.builder()
                .queueName(getFullQueueName(accountId, queueName))
                .attributes(ImmutableMap.of(
                        QueueAttributeName.MESSAGE_RETENTION_PERIOD, String.valueOf(14 * 24 * 60 * 60)))
                .build());
    }

    public String getQueueUrl(String accountId, String queueName) {
        return "https://sqs." + awsRegion + ".amazonaws.com/"
                + awsAccountId + "/"
                + getFullQueueName(accountId, queueName);
    }

    private String getFullQueueName(String accountId, String queueName) {
        return CUSTOMER_QUEUE_PREFIX + accountId + "-" + queueName;
    }
}
