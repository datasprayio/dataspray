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

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.gson.Gson;
import io.dataspray.runner.util.StringSerdeUtil;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.ReturnValue;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.google.common.base.Preconditions.checkState;

/**
 * A {@link StateManager} implementation that stores state in a DynamoDB table.
 */
@Slf4j
public class DynamoStateManager implements StateManager {

    public static final String TTL_IN_EPOCH_SEC_KEY_NAME = "ttlInEpochSec";
    public static final String SORT_KEY = "state";
    private final String tableName;
    private final String[] key;
    private final String keyStr;
    private final Gson gson;
    private final DynamoDbClient dynamo;
    private final Optional<Duration> ttl;
    private Map<String, String> setUpdates = Maps.newHashMap();
    private Set<String> removeUpdates = Sets.newHashSet();
    private Map<String, String> addUpdates = Maps.newHashMap();
    private Map<String, String> deleteUpdates = Maps.newHashMap();
    private Map<String, String> nameMap = Maps.newHashMap();
    private Map<String, AttributeValue> valMap = Maps.newHashMap();
    private Optional<Map<String, AttributeValue>> itemOpt = Optional.empty();
    private boolean isClosed = false;

    DynamoStateManager(String tableName, Gson gson, DynamoDbClient dynamo, String[] key, Optional<Duration> ttl) {
        this.tableName = tableName;
        this.key = key;
        this.keyStr = StringSerdeUtil.mergeStrings(key);
        this.gson = gson;
        this.dynamo = dynamo;
        this.ttl = ttl;
    }

    @Override
    public String[] getKey() {
        return key;
    }

    @Override
    public void touch() {
        checkState(!isClosed);
        if (ttl.isEmpty()) {
            return;
        }
        long ttlInEpochSec = Instant.now().getEpochSecond() + ttl.get().getSeconds();
        set(TTL_IN_EPOCH_SEC_KEY_NAME, AttributeValue.fromN(Long.toString(ttlInEpochSec)));
    }

    @Override
    public <T> Optional<T> getJson(String key, Class<T> type) {
        return Optional.ofNullable(Strings.emptyToNull(getString(key)))
                .map(s -> gson.fromJson(s, type));
    }

    @Override
    public <T> void setJson(String key, T item) {
        setString(key, gson.toJson(item));
    }

    @Override
    public String getString(String key) {
        checkState(!isClosed);
        return get(key)
                .flatMap(a -> Optional.ofNullable(a.s()))
                .orElse("");
    }

    @Override
    public void setString(String key, String value) {
        checkState(!isClosed);
        flushForKey(key);
        touch();
        set(key, AttributeValue.fromS(value));
    }

    @Override
    public boolean getBoolean(String key) {
        checkState(!isClosed);
        return get(key)
                .flatMap(a -> Optional.ofNullable(a.bool()))
                .orElse(false);
    }

    @Override
    public void setBoolean(String key, boolean value) {
        checkState(!isClosed);
        flushForKey(key);
        touch();
        set(key, AttributeValue.fromBool(value));
    }

    @Override
    public BigDecimal getNumber(String key) {
        checkState(!isClosed);
        return get(key)
                .flatMap(a -> Optional.ofNullable(a.n()))
                .map(BigDecimal::new)
                .orElse(BigDecimal.ZERO);
    }

    @Override
    public void setNumber(String key, Number number) {
        checkState(!isClosed);
        flushForKey(key);
        touch();
        set(key, AttributeValue.fromN(number.toString()));
    }

    @Override
    public synchronized void addToNumber(String key, Number increment) {
        checkState(!isClosed);
        flushForKey(key);
        touch();
        setUpdates.put(key, String.format(
                "%s = if_not_exists(%s, %s) + %s",
                fieldMapping(key),
                fieldMapping(key),
                constantMapping("zero", AttributeValue.fromN("0")),
                constantMapping(key, AttributeValue.fromN(increment.toString()))));
    }

    @Override
    public Set<String> getStringSet(String key) {
        checkState(!isClosed);
        return get(key)
                .map(AttributeValue::ss)
                .map(ImmutableSet::copyOf)
                .orElseGet(ImmutableSet::of);
    }

    @Override
    public void setStringSet(String key, Set<String> set) {
        checkState(!isClosed);
        flushForKey(key);
        touch();
        set(key, AttributeValue.fromSs(set.stream().toList()));
    }

    @Override
    public synchronized void addToStringSet(String key, String... values) {
        checkState(!isClosed);
        flushForKey(key);
        touch();
        addUpdates.put(key, String.format(
                "%s %s",
                fieldMapping(key),
                constantMapping(key, AttributeValue.fromSs(List.of(values)))));
    }

    @Override
    public synchronized void deleteFromStringSet(String key, String... values) {
        checkState(!isClosed);
        flushForKey(key);
        touch();
        deleteUpdates.put(key, String.format(
                "%s %s",
                fieldMapping(key),
                constantMapping(key, AttributeValue.fromSs(List.of(values)))));
    }

    @Override
    public synchronized void delete(String key) {
        checkState(!isClosed);
        flushForKey(key);
        touch();
        removeUpdates.add(key);
    }

    private synchronized void set(String key, AttributeValue value) {
        setUpdates.put(key, String.format(
                "%s = %s",
                fieldMapping(key),
                constantMapping(key, value)));
    }

    private Optional<AttributeValue> get(String key) {
        return Optional.ofNullable(getAttrVals().get(key));
    }

    private Map<String, AttributeValue> getAttrVals() {
        return flushAndGet().orElseGet(this::getItem);
    }

    private synchronized void flushForKey(String key) {
        if (setUpdates.containsKey(key)
            || removeUpdates.contains(key)
            || addUpdates.containsKey(key)
            || deleteUpdates.containsKey(key)) {
            flushAndGet();
        }
        itemOpt = Optional.empty();
    }

    @Override
    public void flush() {
        flushAndGet();
    }

    /**
     * Flushes the current state to the database if any pending updates exist.
     *
     * @return The updated item if any updates were flushed, otherwise empty.
     */
    private synchronized Optional<Map<String, AttributeValue>> flushAndGet() {
        if (setUpdates.isEmpty()
            && removeUpdates.isEmpty()
            && addUpdates.isEmpty()
            && deleteUpdates.isEmpty()) {
            return Optional.empty();
        }
        String updateExpression = "";
        if (!setUpdates.isEmpty()) {
            updateExpression += " SET " + String.join(", ", setUpdates.values());
        }
        if (!removeUpdates.isEmpty()) {
            updateExpression += " REMOVE " + String.join(", ", removeUpdates);
        }
        if (!addUpdates.isEmpty()) {
            updateExpression += " ADD " + String.join(", ", addUpdates.values());
        }
        if (!deleteUpdates.isEmpty()) {
            updateExpression += " DELETE " + String.join(", ", deleteUpdates.values());
        }
        updateExpression = updateExpression.trim();
        log.info("Flushing dynamo update for table {} key {}: {}",
                tableName, key, updateExpression);
        itemOpt = Optional.of(dynamo.updateItem(UpdateItemRequest.builder()
                .tableName(tableName)
                .key(Map.of("pk", AttributeValue.fromS(keyStr),
                        "sk", AttributeValue.fromS(SORT_KEY)))
                .updateExpression(updateExpression)
                .expressionAttributeNames(nameMap)
                .expressionAttributeValues(valMap)
                .returnValues(ReturnValue.ALL_NEW)
                .build()).attributes());

        setUpdates.clear();
        removeUpdates.clear();
        deleteUpdates.clear();
        addUpdates.clear();
        nameMap.clear();
        valMap.clear();

        return itemOpt;
    }

    private Map<String, AttributeValue> getItem() {
        Map<String, AttributeValue> item = itemOpt.orElse(null);
        if (item == null) {
            synchronized (this) {
                if (itemOpt.isEmpty()) {
                    log.info("Fetching dynamo item for table {} partitionKey {} sortKey {}",
                            tableName, keyStr, SORT_KEY);
                    itemOpt = Optional.of(Optional.ofNullable(dynamo.getItem(GetItemRequest.builder()
                                    .tableName(tableName)
                                    .key(Map.of("pk", AttributeValue.fromS(keyStr),
                                            "sk", AttributeValue.fromS(SORT_KEY)))
                                    .build()).item())
                            .orElseGet(Maps::newHashMap));
                }
                item = itemOpt.get();
            }
        }
        return item;
    }

    public String fieldMapping(String fieldName) {
        checkState(itemOpt.isEmpty());
        String mappedName = "#" + sanitizeFieldMapping(fieldName);
        nameMap.put(mappedName, fieldName);
        return mappedName;
    }

    public String constantMapping(String name, AttributeValue value) {
        checkState(itemOpt.isEmpty());
        String mappedName = ":" + sanitizeFieldMapping(name);
        valMap.put(mappedName, value);
        return mappedName;
    }

    private String sanitizeFieldMapping(String fieldName) {
        return fieldName.replaceAll("(^[^a-z])|[^a-zA-Z0-9]", "x");
    }

    @Override
    public void close() {
        flush();
        isClosed = true;
    }
}
