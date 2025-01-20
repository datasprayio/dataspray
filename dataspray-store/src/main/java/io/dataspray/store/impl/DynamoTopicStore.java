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
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.dataspray.singletable.SingleTable;
import io.dataspray.singletable.TableSchema;
import io.dataspray.singletable.builder.UpdateBuilder;
import io.dataspray.store.TopicStore;
import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

@Slf4j
@ApplicationScoped
public class DynamoTopicStore implements TopicStore {

    public static final long INITIAL_VERSION = 0;
    public static final int CACHE_EXPIRY_IN_MINUTES = 1;

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
                .expireAfterWrite(Duration.ofMinutes(CACHE_EXPIRY_IN_MINUTES))
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
                .executeGet(dynamo)
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
    public Topics updateDefaultTopic(String organizationName, Topic topic, Optional<Long> expectVersionOpt) {
        return updateTopic(organizationName, Optional.empty(), topic, expectVersionOpt);
    }

    @Override
    public Topics updateTopic(String organizationName, String topicName, Topic topic, Optional<Long> expectVersionOpt) {
        return updateTopic(organizationName, Optional.of(topicName), topic, expectVersionOpt);
    }

    private Topics updateTopic(String organizationName, Optional<String> topicNameOrDefault, Topic topic, Optional<Long> expectVersionOpt) {
        log.info("Updating topic {} for org {}: {}", topicNameOrDefault.orElse("default"), organizationName, topic);
        UpdateBuilder<Topics> updateBuilder = topicsSchema.update()
                .key(ImmutableMap.of("organizationName", organizationName))
                .conditionExists();

        // Prevent concurrent modifications by ensuring expected version
        expectVersionOpt.ifPresent(expectVersion -> updateBuilder.conditionFieldEquals("version", expectVersion));
        updateBuilder.setIncrement("version", 1L);

        // Update topic
        topicNameOrDefault.ifPresentOrElse(
                topicName -> updateBuilder.set(ImmutableList.of("topics", topicName), topic),
                () -> updateBuilder.set("undefinedTopic", topic));
        Topics topicsUpdated = updateBuilder
                .executeGetUpdated(dynamo);

        // Update cache
        topicsByOrganizationNameCache.put(topicsUpdated.getOrganizationName(), topicsUpdated);

        return topicsUpdated;
    }
}
