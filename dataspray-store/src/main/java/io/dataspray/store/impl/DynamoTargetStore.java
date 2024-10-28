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
import io.dataspray.store.TargetStore;
import io.quarkus.runtime.Startup;
import jakarta.inject.Inject;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemResponse;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

public class DynamoTargetStore implements TargetStore {

    public static final int INITIAL_VERSION = 0;

    @Inject
    @VisibleForTesting
    public DynamoDbClient dynamo;
    @Inject
    @VisibleForTesting
    public SingleTable singleTable;

    private TableSchema<Targets> targetsSchema;
    private Cache<String, Targets> targetsByOrganizationNameCache;

    @Startup
    @VisibleForTesting
    public void init() {
        targetsByOrganizationNameCache = CacheBuilder.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(1))
                .softValues()
                .build();

        targetsSchema = singleTable.parseTableSchema(Targets.class);
    }

    @Override
    public Targets getTargets(String organizationName, boolean useCache) {

        // Check cache first
        if (useCache) {
            Targets targets = targetsByOrganizationNameCache.getIfPresent(organizationName);
            if (targets != null) {
                return targets;
            }
        }

        // Fetch from DB
        Targets targets = targetsSchema.fromAttrMap(dynamo.getItem(GetItemRequest.builder()
                        .tableName(targetsSchema.tableName())
                        .key(targetsSchema.primaryKey(Map.of(
                                "organizationName", organizationName)))
                        .consistentRead(!useCache)
                        .build())
                .item());
        // Construct default if not found
        if (targets == null) {
            targets = Targets.builder()
                    .organizationName(organizationName)
                    .version(INITIAL_VERSION)
                    .build();
        }

        // Update cache
        targetsByOrganizationNameCache.put(organizationName, targets);

        return targets;
    }

    @Override
    public Optional<Target> getTarget(String organizationName, String targetName, boolean useCache) {
        return getTargets(organizationName, useCache).getTarget(targetName);
    }

    @Override
    public Targets updateTargets(Targets targets) {

        // Bump the version
        Targets targetsVersionBumped = targets.toBuilder()
                .version(targets.getVersion() + 1)
                .build();

        // To prevent concurrent modification, we check that the database entry matches our
        // expected version using dynamo conditions
        PutBuilder<Targets> putBuilder = targetsSchema.put();
        if (targets.getVersion() == INITIAL_VERSION) {
            putBuilder.conditionNotExists().build();
        } else {
            putBuilder.conditionFieldEquals("version", targets.getVersion()).build();
        }
        // Make the update
        PutItemResponse execute = putBuilder.item(targetsVersionBumped)
                .execute(dynamo);

        // Update cache
        targetsByOrganizationNameCache.put(targets.getOrganizationName(), targetsVersionBumped);

        // Return targets with version bumped
        return targetsVersionBumped;
    }
}
