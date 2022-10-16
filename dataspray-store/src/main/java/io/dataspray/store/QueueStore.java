package io.dataspray.store;

import software.amazon.awssdk.services.sqs.model.QueueAttributeName;

import javax.ws.rs.core.MediaType;
import java.util.Map;
import java.util.Optional;

public interface QueueStore {

    void submit(String accountId, String queueName, byte[] messageBytes, MediaType contentType);

    /** Check whether queue exists and optionally return attributes */
    Optional<Map<QueueAttributeName, String>> queueAttributes(String accountId, String queueName, QueueAttributeName... fetchAttributes);

    void createQueue(String accountId, String queueName);

    /** Converts user supplied queue name to AWS queue name */
    String getAwsQueueName(String accountId, String queueName);

    Optional<String> extractQueueNameFromAwsQueueName(String accountId, String awsQueueName);
}
