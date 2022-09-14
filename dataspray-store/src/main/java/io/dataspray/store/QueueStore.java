package io.dataspray.store;

public interface QueueStore {

    void submit(String accountId, String queueName, byte[] message);

    void createQueue(String accountId, String queueName);
}
