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
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import io.dataspray.singletable.IndexSchema;
import io.dataspray.singletable.ShardPageResult;
import io.dataspray.singletable.SingleTable;
import io.dataspray.singletable.TableSchema;
import io.dataspray.store.LambdaStore;
import io.dataspray.store.util.CycleUtil;
import io.dataspray.store.util.CycleUtil.Node;
import io.dataspray.store.util.IdUtil;
import io.quarkus.runtime.Startup;
import jakarta.inject.Inject;
import jakarta.ws.rs.ConflictException;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

@Slf4j
public class DynamoLambdaStore implements LambdaStore {

    /**
     * Deleted lambda records are kept for this duration before being permanently deleted from records.
     */
    private static final Duration DELETED_LAMBDA_EXPIRY = Duration.ofDays(180L);
    /**
     * Lock is usually released, but if a catastrophic failure occurs, it will be released after this duration.
     * This currently corresponds to AWS Lambda max timeout.
     */
    private static final Duration LOCK_EXPIRY = Duration.ofMinutes(15L);

    @Inject
    DynamoDbClient dynamo;
    @Inject
    SingleTable singleTable;
    @Inject
    IdUtil idUtil;
    @Inject
    CycleUtil cycleUtil;

    private TableSchema<LambdaRecord> lambdaRecordSchema;
    private IndexSchema<LambdaRecord> lambdasByOrganizationScanAllSchema;
    private TableSchema<LambdaMutex> lambdaMutexSchema;

    @Startup
    @VisibleForTesting
    public void init() {
        lambdaRecordSchema = singleTable.parseTableSchema(LambdaRecord.class);
        lambdasByOrganizationScanAllSchema = singleTable.parseGlobalSecondaryIndexSchema(1, LambdaRecord.class);
        lambdaMutexSchema = singleTable.parseTableSchema(LambdaMutex.class);
    }

    @Override
    public LambdaRecord set(String organizationName,
                            String taskId,
                            String username,
                            ImmutableSet<String> inputQueueNames,
                            ImmutableSet<String> outputQueueNames,
                            Optional<String> endpointUrlOpt) {
        LambdaRecord lamdbaRecord = new LambdaRecord(
                organizationName,
                taskId,
                username,
                inputQueueNames,
                outputQueueNames,
                endpointUrlOpt.orElse(null),
                false,
                null);
        lambdaRecordSchema.put()
                .item(lamdbaRecord)
                .execute(dynamo);
        return lamdbaRecord;
    }

    @Override
    public Optional<LambdaRecord> get(String organizationName, String taskId) {
        return lambdaRecordSchema.get()
                .key(Map.of(
                        "organizationName", organizationName,
                        "taskId", taskId))
                .builder(b -> b.consistentRead(true))
                .execute(dynamo);
    }

    @Override
    public void getForOrganization(String organizationName, boolean includeDeleted, Consumer<ImmutableList<LambdaRecord>> batchConsumer) {
        Optional<String> cursorOpt = Optional.empty();
        do {
            ShardPageResult<LambdaRecord> result = getForOrganization(organizationName, includeDeleted, cursorOpt);
            cursorOpt = result.getCursorOpt();
            batchConsumer.accept(result.getItems());
        } while (cursorOpt.isPresent());
    }

    @Override
    public ShardPageResult<LambdaRecord> getForOrganization(String organizationName, boolean includeDeleted, Optional<String> cursorOpt) {
        return singleTable.fetchShardNextPage(
                dynamo,
                lambdasByOrganizationScanAllSchema,
                cursorOpt,
                100,
                Map.of("organizationName", organizationName),
                includeDeleted ? null : queryBuilder -> queryBuilder
                        .filterExpression("isDeleted = :isDeleted")
                        .expressionAttributeValues(Map.of(":isDeleted", AttributeValue.fromBool(Boolean.FALSE))));
    }

    @Override
    public LambdaRecord markDeleted(String organizationName, String taskId) {
        return lambdaRecordSchema.update()
                .conditionExists()
                .set("isDeleted", true)
                .key(Map.of(
                        "organizationName", organizationName,
                        "taskId", taskId))
                .execute(dynamo)
                .orElseThrow();
    }

    @Override
    public Optional<List<Node>> checkLoops(String organizationName, String taskId, ImmutableSet<String> inputQueueNames, ImmutableSet<String> outputQueueNames) throws ConflictException {
        Map<String, Node> nodes = Maps.newHashMap();
        getForOrganization(organizationName, false, batch -> batch.forEach(lambda ->
                nodes.put(lambda.getTaskId(), Node.of(lambda.getTaskId(), lambda.getInputQueueNames(), lambda.getOutputQueueNames()))));
        nodes.put(taskId, Node.of(taskId, inputQueueNames, outputQueueNames));
        return cycleUtil.findCycle(nodes.values());
    }

    @Override
    public Optional<AutoCloseable> acquireLock(String organizationName, String taskId) {
        String reservationId = idUtil.randomId();

        // Condition to ensure there is no existing un-expired lock
        try {
            lambdaMutexSchema.put()
                    .conditionExpression(mappings -> "attribute_not_exists(" + mappings.fieldMapping("ttlInEpochSec") + ")" +
                                                     " OR " + mappings.fieldMapping("ttlInEpochSec")
                                                     + " < " + mappings.constantMapping("now", Instant.now().getEpochSecond()))
                    .item(new LambdaMutex(
                            organizationName,
                            taskId,
                            reservationId,
                            Instant.now().plus(LOCK_EXPIRY).getEpochSecond()))
                    .execute(dynamo);
        } catch (ConditionalCheckFailedException ex) {
            // A live lock already exists
            return Optional.empty();
        }

        return Optional.of(() -> releaseLock(organizationName, taskId, reservationId));
    }

    private void releaseLock(String organizationName, String taskId, String reservationId) {
        lambdaMutexSchema.delete()
                .conditionFieldEquals("reservationId", reservationId)
                .key(Map.of(
                        "organizationName", organizationName,
                        "taskId", taskId))
                .execute(dynamo);
    }
}
