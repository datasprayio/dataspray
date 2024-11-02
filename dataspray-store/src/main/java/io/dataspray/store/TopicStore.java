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
     * For undefined targets, whether to ingest it with default configuration.
     */
    boolean DEFAULT_ALLOW_UNDEFINED_TARGETS = true;
    /**
     * For undefined targets, default batch retention.
     */
    BatchRetention DEFAULT_BATCH_RETENTION = THREE_MONTHS;

    Targets getTopics(String organizationName, boolean useCache);

    Optional<Target> getTopic(String organizationName, String targetName, boolean useCache);

    Targets updateTargets(Targets targets);

    /**
     * <b>Organization target definitions.</b>
     * <p>Each organization has a set of targets and their definitions live here.</p>
     * <p>Each entry defines targets and their definition including a default target if no explicit definition is
     * defined</p>
     * <p>This object is loaded from Dynamo on a hot-path and needs to stay efficient and small (under 1MB dynamo
     * limit)</p>
     */
    @Value
    @AllArgsConstructor
    @Builder(toBuilder = true)
    @DynamoTable(type = Primary, partitionKeys = "organizationName", rangePrefix = "targets")
    @RegisterForReflection
    class Targets {

        @NonNull
        String organizationName;

        /**
         * Version of this configuration. Used for preventing concurrent modification.
         */
        @NonNull
        Integer version;

        @Nullable
        Boolean allowUndefinedTargets;

        /**
         * Override configuration for default undefined targets.
         */
        @Nullable
        Target undefinedTarget;

        @NonNull
        @Builder.Default
        Set<Target> targets = ImmutableSet.of();

        /**
         * For a target with no definition, whether to ingest it with default configuration.
         */
        public boolean isAllowUndefinedTargets() {
            return allowUndefinedTargets == null
                    ? DEFAULT_ALLOW_UNDEFINED_TARGETS
                    : allowUndefinedTargets;
        }

        /**
         * Get target metadata by name.
         * <br />
         * Helper method to return defaults if not specified.
         */
        @NonNull
        public Optional<Target> getTopic(String target) {
            return targets.stream()
                    .filter(t -> t.getName().equals(target))
                    .findFirst()
                    // If undefined, see if we allow undefined targets
                    .or(() -> isAllowUndefinedTargets()
                            // We do, let's see if we have an organization-wide default configuration for undefined targets
                            ? Optional.ofNullable(undefinedTarget)
                            // We don't, return DataSpray-wide default configuration for undefined targets
                            .or(() -> Optional.of(Target.builder()
                                    .name(target)
                                    // By default, we enable batching for undefined targets
                                    // otherwise there is no point in having a default definition for them
                                    .batch(Optional.of(Batch.builder().build()))
                                    .build()))
                            // We don't accept targets without definition
                            : Optional.empty());
        }
    }

    /**
     * <b>Target definition.</b>
     * <p>Each target specifies whether data ingested should be directed towards batch processing and/or stream
     * processing endpoints</p>
     */
    @Value
    @AllArgsConstructor
    @Builder(toBuilder = true)
    @RegisterForReflection
    class Target {

        @NonNull
        @SerializedName("n")
        String name;

        /**
         * Whether this target should send data for batch processing (e.g. Firehose -> S3).
         */
        @NonNull
        @SerializedName("b")
        @Builder.Default
        Optional<Batch> batch = Optional.empty();

        /**
         * Whether this target should send data for stream processing (e.g. SQS queues).
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
