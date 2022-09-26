package io.dataspray.store;

import javax.ws.rs.core.MediaType;

public interface QueueStore {

    void submit(String accountId, String queueName, byte[] messageBytes, MediaType contentType);

    void createQueue(String accountId, String queueName);
}
