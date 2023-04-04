package io.dataspray.store;

import jakarta.ws.rs.core.MediaType;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;

import java.util.Map;
import java.util.Optional;

public interface QueueStore {

    void submit(String customerId, String queueName, byte[] messageBytes, MediaType contentType);

    /** Check whether queue exists */
    boolean queueExists(String customerId, String queueName);

    /** Check queue attributes */
    Optional<Map<QueueAttributeName, String>> queueAttributes(String customerId, String queueName, QueueAttributeName... fetchAttributes);

    void createQueue(String customerId, String queueName);

    /** Converts user supplied queue name to AWS queue name */
    String getAwsQueueName(String customerId, String queueName);

    Optional<String> extractQueueNameFromAwsQueueName(String customerId, String awsQueueName);
}
