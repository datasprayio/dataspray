// SPDX-FileCopyrightText: 2019-2022 Matus Faro <matus@smotana.com>
// SPDX-License-Identifier: Apache-2.0
package io.dataspray.runner;

import jakarta.annotation.Nullable;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.time.Duration;

public interface RawCoordinator {

    void send(String messageKey, byte[] data, StoreType storeType, String storeName, String streamName, @Nullable String messageId);

    StateManager getStateManager(String[] key, @Nullable Duration ttl);

    DynamoDbClient getDynamoClient();
}
