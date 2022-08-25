package io.dataspray.store;

import com.google.common.collect.ImmutableMap;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest;
import software.amazon.awssdk.services.sqs.model.CreateQueueResponse;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Default;
import javax.inject.Named;
import java.util.Base64;
import java.util.Base64.Encoder;

import static io.dataspray.store.QueueStore.IMPL_SQS;

@Default
@ApplicationScoped
@Named(IMPL_SQS)
public class SqsQueueStore implements QueueStore {
    @ConfigProperty(defaultValue = "us-east-1")
    String awsRegion;
    @ConfigProperty(defaultValue = "1234567890")
    String awsAccountId;

    private final SqsClient client = SqsClient.create();
    private final Encoder base64Encoder = Base64.getEncoder();

    @Override
    public void submit(String accountId, String queueName, byte[] message) {
        client.sendMessage(SendMessageRequest.builder()
                .queueUrl(getQueueUrl(accountId, queueName))
                .messageBody(base64Encoder.encodeToString(message))
                .build());
    }

    @Override
    public void createQueue(String accountId, String queueName) {
        CreateQueueResponse queueResponse = client.createQueue(CreateQueueRequest.builder()
                .queueName(getFullQueueName(accountId, queueName))
                .attributes(ImmutableMap.of(
                        QueueAttributeName.MESSAGE_RETENTION_PERIOD, String.valueOf(14 * 24 * 60 * 60)))
                .build());
    }

    private String getQueueUrl(String accountId, String queueName) {
        return "https://sqs." + awsRegion + ".amazonaws.com/"
                + awsAccountId + "/"
                + getFullQueueName(accountId, queueName);
    }

    private String getFullQueueName(String accountId, String queueName) {
        return "customer-" + accountId + "-" + queueName;
    }
}
