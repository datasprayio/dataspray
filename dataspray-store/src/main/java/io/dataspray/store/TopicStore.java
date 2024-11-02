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

package io.dataspray.store;


import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.gson.annotations.SerializedName;
import io.dataspray.singletable.DynamoTable;
import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.annotation.Nullable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.Value;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static io.dataspray.singletable.TableType.Primary;
import static io.dataspray.store.TopicStore.BatchRetention.THREE_MONTHS;

/**
 * <b>Metadata for ingestion topics.</b> (named endpoints for data ingestion)
 * <p>
 * A topic is identified by name and defines which stream and batch destinations to write to and how.
 * </p>
 */
public interface TopicStore {

    /**
     * For undefined topics, whether to ingest it with default configuration.
     */
    boolean DEFAULT_ALLOW_UNDEFINED_TOPICS = true;
    /**
     * For undefined topics, default batch retention.
     */
    BatchRetention DEFAULT_BATCH_RETENTION = THREE_MONTHS;

    Topics getTopics(String organizationName, boolean useCache);

    Optional<Topic> getTopic(String organizationName, String topicName, boolean useCache);

    Topics updateTopics(Topics topics);

    /**
     * <b>Organization topic definitions.</b>
     * <p>Each organization has a set of topics and their definitions live here.</p>
     * <p>Each entry defines topics and their definition including a default topic if no explicit definition is
     * defined</p>
     * <p>This object is loaded from Dynamo on a hot-path and needs to stay efficient and small (under 1MB dynamo
     * limit)</p>
     */
    @Value
    @AllArgsConstructor
    @Builder(toBuilder = true)
    @DynamoTable(type = Primary, partitionKeys = "organizationName", rangePrefix = "topics")
    @RegisterForReflection
    class Topics {

        @NonNull
        String organizationName;

        /**
         * Version of this configuration. Used for preventing concurrent modification.
         */
        @NonNull
        Integer version;

        @Nullable
        Boolean allowUndefinedTopics;

        /**
         * Override configuration for default undefined topics.
         */
        @Nullable
        Topic undefinedTopic;

        @NonNull
        @Builder.Default
        Set<Topic> topics = ImmutableSet.of();

        /**
         * For a topic with no definition, whether to ingest it with default configuration.
         */
        public boolean getAllowUndefinedTopics() {
            return allowUndefinedTopics == null
                    ? DEFAULT_ALLOW_UNDEFINED_TOPICS
                    : allowUndefinedTopics;
        }

        /**
         * Get topic metadata by name.
         * <br />
         * Helper method to return defaults if not specified.
         */
        @NonNull
        public Optional<Topic> getTopic(String topic) {
            return topics.stream()
                    .filter(t -> t.getName().equals(topic))
                    .findFirst()
                    // If undefined, see if we allow undefined topics
                    .or(() -> getAllowUndefinedTopics()
                            // We do, let's see if we have an organization-wide default configuration for undefined topics
                            ? Optional.ofNullable(undefinedTopic)
                            // We don't, return DataSpray-wide default configuration for undefined topics
                            .or(() -> Optional.of(Topic.builder()
                                    .name(topic)
                                    // By default, we enable batching for undefined topics
                                    // otherwise there is no point in having a default definition for them
                                    .batch(Optional.of(Batch.builder().build()))
                                    .build()))
                            // We don't accept topics without definition
                            : Optional.empty());
        }
    }

    /**
     * <b>Topic definition.</b>
     * <p>Each topic specifies whether data ingested should be directed towards batch processing and/or stream
     * processing endpoints</p>
     */
    @Value
    @AllArgsConstructor
    @Builder(toBuilder = true)
    @RegisterForReflection
    class Topic {

        @NonNull
        @SerializedName("n")
        String name;

        /**
         * Whether this topic should send data for batch processing (e.g. Firehose -> S3).
         */
        @NonNull
        @SerializedName("b")
        @Builder.Default
        Optional<Batch> batch = Optional.empty();

        /**
         * Whether this topic should send data for stream processing (e.g. SQS queues).
         */
        @NonNull
        @SerializedName("s")
        @Builder.Default
        List<Stream> streams = ImmutableList.of();
    }

    /**
     * <b>Stream definition.</b>
     * <p>A stream definition specifies where data should be written to. (e.g. an SQS stream with given name)</p>
     */
    @Value
    @AllArgsConstructor
    @Builder(toBuilder = true)
    @RegisterForReflection
    class Stream {

        /**
         * Stream name
         */
        @NonNull
        @SerializedName("n")
        String name;
    }

    /**
     * <b>Batch definition.</b>
     * <p>A batch definition specifies whether data should be written to a batch destination (e.g. Firehose -> S3)
     */
    @Value
    @AllArgsConstructor
    @Builder(toBuilder = true)
    @RegisterForReflection
    class Batch {

        @Nullable
        @SerializedName("r")
        BatchRetention retention;

        /**
         * Batching retention, returns default if not set.
         */
        @NonNull
        public BatchRetention getRetention() {
            return retention == null
                    ? DEFAULT_BATCH_RETENTION
                    : retention;
        }
    }

    @Getter
    @AllArgsConstructor
    @RegisterForReflection
    enum BatchRetention {
        DAY(1),
        WEEK(7),
        THREE_MONTHS(3 * 30),
        YEAR(366),
        THREE_YEARS(3 * 366);
        final long retentionInDays;
    }
}
