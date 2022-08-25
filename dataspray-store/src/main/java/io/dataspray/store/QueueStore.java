package io.dataspray.store;

public interface QueueStore {
    String IMPL_SQS = "sqs";

    void submit(String accountId, String queueName, byte[] message);

    void createQueue(String accountId, String queueName);

}
