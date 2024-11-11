// SPDX-FileCopyrightText: 2019-2022 Matus Faro <matus@smotana.com>
// SPDX-License-Identifier: Apache-2.0
package io.dataspray.runner;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.time.Duration;
import java.util.Optional;

/**
 * A factory for {@link StateManager}s that allows for easy access and efficiency.
 * <p>
 * This class is thread-safe, and safe to use for across multiple incoming messages, but is not safe across tasks.
 * {@link StateManager}s are reused and only flushed when necessary. Ensure to call {@link #closeAll()} when done.
 */
public interface StateManagerFactory {

    StateManager getStateManager(String[] key, Optional<Duration> ttl);

    DynamoDbClient getDynamoClient();

    void flushAll();

    void closeAll();
}
