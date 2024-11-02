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

package io.dataspray.store.impl;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import io.dataspray.singletable.SingleTable;
import io.dataspray.singletable.TableSchema;
import io.dataspray.singletable.builder.PutBuilder;
import io.dataspray.store.TopicStore;
import io.quarkus.runtime.Startup;
import jakarta.inject.Inject;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.PutItemResponse;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

public class DynamoTopicStore implements TopicStore {

    public static final int INITIAL_VERSION = 0;

    @Inject
    @VisibleForTesting
    public DynamoDbClient dynamo;
    @Inject
    @VisibleForTesting
    public SingleTable singleTable;

    private TableSchema<Topics> topicsSchema;
    private Cache<String, Topics> topicsByOrganizationNameCache;

    @Startup
    @VisibleForTesting
    public void init() {
        topicsByOrganizationNameCache = CacheBuilder.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(1))
                .softValues()
                .build();

        topicsSchema = singleTable.parseTableSchema(Topics.class);
    }

    @Override
    public Topics getTopics(String organizationName, boolean useCache) {

        // Check cache first
        if (useCache) {
            Topics topics = topicsByOrganizationNameCache.getIfPresent(organizationName);
            if (topics != null) {
                return topics;
            }
        }

        // Fetch from DB
        Topics topics = topicsSchema.get()
                .key(Map.of("organizationName", organizationName))
                .builder(b -> b.consistentRead(!useCache))
                .execute(dynamo)
                // Construct default if not found
                .orElseGet(() -> Topics.builder()
                        .organizationName(organizationName)
                        .version(INITIAL_VERSION)
                        .build());

        // Update cache
        topicsByOrganizationNameCache.put(organizationName, topics);

        return topics;
    }

    @Override
    public Optional<Topic> getTopic(String organizationName, String topicName, boolean useCache) {
        return getTopics(organizationName, useCache).getTopic(topicName);
    }

    @Override
    public Topics updateTopics(Topics topics) {

        // Bump the version
        Topics topicsVersionBumped = topics.toBuilder()
                .version(topics.getVersion() + 1)
                .build();

        // Make the update
        // Prevent concurrent modifications by ensuring expected version
        PutBuilder<Topics> putBuilder = topicsSchema.put();
        if (topics.getVersion() == INITIAL_VERSION) {
            putBuilder.conditionNotExists();
        } else {
            putBuilder.conditionFieldEquals("version", topics.getVersion());
        }
        PutItemResponse execute = putBuilder.item(topicsVersionBumped)
                .execute(dynamo);

        // Update cache
        topicsByOrganizationNameCache.put(topics.getOrganizationName(), topicsVersionBumped);

        // Return topics with version bumped
        return topicsVersionBumped;
    }
}
