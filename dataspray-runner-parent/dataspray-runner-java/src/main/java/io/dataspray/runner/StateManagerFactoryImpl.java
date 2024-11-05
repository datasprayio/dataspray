/*
 * Copyright 2024 Matus Faro
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package io.dataspray.runner;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Maps;
import com.google.gson.Gson;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ConcurrentMap;

@Slf4j
public class StateManagerFactoryImpl implements StateManagerFactory {

    private final String tableName;
    private final Gson gson = new Gson();
    private final DynamoDbClient dynamo;
    private final ConcurrentMap<String[], StateManager> stateManagers = Maps.newConcurrentMap();
    private static volatile StateManagerFactory INSTANCE;

    @VisibleForTesting
    StateManagerFactoryImpl(String tableName, DynamoDbClient dynamo) {
        this.tableName = tableName;
        this.dynamo = dynamo;
    }

    public static Optional<StateManagerFactory> get() {
        return Optional.ofNullable(INSTANCE);
    }

    public static StateManagerFactory getOrCreate() {
        if (INSTANCE == null) {
            synchronized (StateManagerFactoryImpl.class) {
                if (INSTANCE == null) {
                    INSTANCE = new StateManagerFactoryImpl(
                            System.getenv("STATE_TABLE_NAME"),
                            DynamoDbClient.create()
                    );
                }
            }
        }
        return INSTANCE;
    }

    @Override
    public StateManager getStateManager(String[] key, Optional<Duration> ttl) {
        return stateManagers.computeIfAbsent(key,
                k -> new DynamoStateManager(tableName, gson, dynamo, k, ttl));
    }

    @Override
    public void flushAll() {
        stateManagers.values().forEach(StateManager::flush);
    }

    @Override
    @SneakyThrows
    public void closeAll() {
        for (StateManager stateManager : stateManagers.values()) {
            stateManager.close();
        }
    }
}