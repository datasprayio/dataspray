package io.dataspray.store;

import com.google.common.collect.ImmutableMap;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest;
import software.amazon.awssdk.services.sqs.model.CreateQueueResponse;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.Base64;
import java.util.Base64.Encoder;

@ApplicationScoped
public class SqsQueueStore implements QueueStore {

    @ConfigProperty(name = "queue.region", defaultValue = "us-east-1")
    String awsRegion;
    @ConfigProperty(name = "aws.accountId")
    String awsAccountId;

    @Inject
    SqsClient sqsClient;

    private final Encoder base64Encoder = Base64.getEncoder();

    @Override
    public void submit(String accountId, String queueName, byte[] message) {
        sqsClient.sendMessage(SendMessageRequest.builder()
                .queueUrl(getQueueUrl(accountId, queueName))
                .messageBody(base64Encoder.encodeToString(message))
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
        return "customer-" + accountId + "-" + queueName;
    }
}
