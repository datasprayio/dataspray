/*
 * Copyright 2025 Matus Faro
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
 * THE SOFTWARE IS PROVIDED "AS IS"), WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package io.dataspray.store.impl;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.dataspray.singletable.StringSerdeUtil;
import io.dataspray.store.CustomerDynamoStore;
import io.dataspray.store.StateStore;
import io.dataspray.store.util.WithCursor;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@ApplicationScoped
public class DynamoStateStore implements StateStore {

    private static final String SORT_KEY_VALUE = "state";
    private static final String TTL_ATTR = "ttlInEpochSec";
    private static final String PK_ATTR = "pk";
    private static final String SK_ATTR = "sk";

    @Inject
    DynamoDbClient dynamo;

    @Inject
    CustomerDynamoStore customerDynamoStore;

    @Override
    public WithCursor<List<StateEntry>> listState(
            String organizationName,
            Optional<String[]> keyPrefix,
            Optional<String> cursor,
            int limit) {

        String tableName = customerDynamoStore.getTableName(organizationName);

        // Build scan request
        ScanRequest.Builder scanBuilder = ScanRequest.builder()
                .tableName(tableName)
                .limit(limit);

        // Build filter expression
        Map<String, String> expressionNames = new HashMap<>();
        Map<String, AttributeValue> expressionValues = new HashMap<>();

        expressionNames.put("#sk", SK_ATTR);
        expressionValues.put(":stateValue", AttributeValue.fromS(SORT_KEY_VALUE));

        String filterExpression = "#sk = :stateValue";

        // Add key prefix filter if provided
        if (keyPrefix.isPresent() && keyPrefix.get().length > 0) {
            String mergedPrefix = StringSerdeUtil.mergeStrings(keyPrefix.get());
            expressionNames.put("#pk", PK_ATTR);
            expressionValues.put(":prefix", AttributeValue.fromS(mergedPrefix));
            filterExpression += " AND begins_with(#pk, :prefix)";
        }

        scanBuilder
                .filterExpression(filterExpression)
                .expressionAttributeNames(expressionNames)
                .expressionAttributeValues(expressionValues);

        // Add cursor for pagination
        cursor.ifPresent(c -> {
            Map<String, AttributeValue> startKey = decodeCursor(c);
            scanBuilder.exclusiveStartKey(startKey);
        });

        ScanResponse response;
        try {
            response = dynamo.scan(scanBuilder.build());
        } catch (ResourceNotFoundException e) {
            log.warn("Table not found for organization: {}", organizationName);
            return new WithCursor<>(ImmutableList.of(), Optional.empty());
        }

        List<StateEntry> entries = response.items().stream()
                .map(this::itemToStateEntry)
                .collect(Collectors.toList());

        Optional<String> nextCursor = Optional.ofNullable(response.lastEvaluatedKey())
                .filter(k -> !k.isEmpty())
                .map(this::encodeCursor);

        return new WithCursor<>(entries, nextCursor);
    }

    @Override
    public Optional<StateEntry> getState(String organizationName, String[] keyParts) {
        String tableName = customerDynamoStore.getTableName(organizationName);
        String mergedKey = StringSerdeUtil.mergeStrings(keyParts);

        GetItemResponse response;
        try {
            response = dynamo.getItem(GetItemRequest.builder()
                    .tableName(tableName)
                    .key(Map.of(
                            PK_ATTR, AttributeValue.fromS(mergedKey),
                            SK_ATTR, AttributeValue.fromS(SORT_KEY_VALUE)
                    ))
                    .build());
        } catch (ResourceNotFoundException e) {
            log.warn("Table not found for organization: {}", organizationName);
            return Optional.empty();
        }

        return Optional.ofNullable(response.item())
                .filter(item -> !item.isEmpty())
                .map(this::itemToStateEntry);
    }

    @Override
    public StateEntry upsertState(
            String organizationName,
            String[] keyParts,
            ImmutableMap<String, Object> attributes,
            Optional<Long> ttlInSec) {

        String tableName = customerDynamoStore.getTableName(organizationName);
        String mergedKey = StringSerdeUtil.mergeStrings(keyParts);

        Map<String, AttributeValue> item = new HashMap<>();
        item.put(PK_ATTR, AttributeValue.fromS(mergedKey));
        item.put(SK_ATTR, AttributeValue.fromS(SORT_KEY_VALUE));

        // Add TTL if provided
        ttlInSec.ifPresent(ttl -> {
            long ttlEpoch = Instant.now().plusSeconds(ttl).getEpochSecond();
            item.put(TTL_ATTR, AttributeValue.fromN(String.valueOf(ttlEpoch)));
        });

        // Convert attributes to DynamoDB format
        attributes.forEach((key, value) -> {
            item.put(key, marshalValue(value));
        });

        try {
            dynamo.putItem(PutItemRequest.builder()
                    .tableName(tableName)
                    .item(item)
                    .build());
        } catch (ResourceNotFoundException e) {
            log.error("Table not found for organization: {}. State tables must be created via task deployment.", organizationName);
            throw new RuntimeException("State table not found for organization: " + organizationName, e);
        }

        return getState(organizationName, keyParts)
                .orElseThrow(() -> new RuntimeException("Failed to retrieve state after upsert"));
    }

    @Override
    public void deleteState(String organizationName, String[] keyParts) {
        String tableName = customerDynamoStore.getTableName(organizationName);
        String mergedKey = StringSerdeUtil.mergeStrings(keyParts);

        try {
            dynamo.deleteItem(DeleteItemRequest.builder()
                    .tableName(tableName)
                    .key(Map.of(
                            PK_ATTR, AttributeValue.fromS(mergedKey),
                            SK_ATTR, AttributeValue.fromS(SORT_KEY_VALUE)
                    ))
                    .build());
        } catch (ResourceNotFoundException e) {
            log.warn("Table not found for organization: {}", organizationName);
            // Silent failure - item effectively doesn't exist
        }
    }

    // Helper methods

    private StateEntry itemToStateEntry(Map<String, AttributeValue> item) {
        String mergedKey = item.get(PK_ATTR).s();
        String[] keyParts = StringSerdeUtil.unMergeString(mergedKey);

        Optional<Long> ttl = Optional.ofNullable(item.get(TTL_ATTR))
                .filter(attr -> attr.n() != null)
                .map(attr -> Long.parseLong(attr.n()));

        ImmutableMap<String, Object> attributes = item.entrySet().stream()
                .filter(e -> !e.getKey().equals(PK_ATTR)
                        && !e.getKey().equals(SK_ATTR)
                        && !e.getKey().equals(TTL_ATTR))
                .collect(ImmutableMap.toImmutableMap(
                        Map.Entry::getKey,
                        e -> unmarshalValue(e.getValue())
                ));

        return new StateEntry(keyParts, mergedKey, attributes, ttl);
    }

    private AttributeValue marshalValue(Object value) {
        if (value == null) {
            return AttributeValue.fromNul(true);
        } else if (value instanceof String) {
            return AttributeValue.fromS((String) value);
        } else if (value instanceof Number) {
            return AttributeValue.fromN(value.toString());
        } else if (value instanceof Boolean) {
            return AttributeValue.fromBool((Boolean) value);
        } else if (value instanceof Collection) {
            // Try to handle as string set
            Collection<?> coll = (Collection<?>) value;
            if (coll.isEmpty()) {
                return AttributeValue.fromSs(Collections.emptyList());
            }
            Object first = coll.iterator().next();
            if (first instanceof String) {
                @SuppressWarnings("unchecked")
                Collection<String> stringColl = (Collection<String>) coll;
                return AttributeValue.fromSs(new ArrayList<>(stringColl));
            } else if (first instanceof Number) {
                List<String> numbers = coll.stream()
                        .map(Object::toString)
                        .collect(Collectors.toList());
                return AttributeValue.fromNs(numbers);
            }
            // Fall through to generic handling
        } else if (value instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) value;
            Map<String, AttributeValue> dynamoMap = map.entrySet().stream()
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            e -> marshalValue(e.getValue())
                    ));
            return AttributeValue.fromM(dynamoMap);
        }

        // Default: convert to string representation
        return AttributeValue.fromS(value.toString());
    }

    private Object unmarshalValue(AttributeValue attr) {
        if (attr.s() != null) {
            return attr.s();
        } else if (attr.n() != null) {
            return new BigDecimal(attr.n());
        } else if (attr.bool() != null) {
            return attr.bool();
        } else if (attr.ss() != null && !attr.ss().isEmpty()) {
            return ImmutableList.copyOf(attr.ss());
        } else if (attr.ns() != null && !attr.ns().isEmpty()) {
            return attr.ns().stream()
                    .map(BigDecimal::new)
                    .collect(ImmutableList.toImmutableList());
        } else if (attr.m() != null) {
            return attr.m().entrySet().stream()
                    .collect(ImmutableMap.toImmutableMap(
                            Map.Entry::getKey,
                            e -> unmarshalValue(e.getValue())
                    ));
        } else if (attr.nul() != null && attr.nul()) {
            return null;
        }
        return null;
    }

    private String encodeCursor(Map<String, AttributeValue> lastKey) {
        // Simple Base64 encoding of pk value
        String pk = lastKey.get(PK_ATTR).s();
        return Base64.getEncoder().encodeToString(pk.getBytes());
    }

    private Map<String, AttributeValue> decodeCursor(String cursor) {
        String pk = new String(Base64.getDecoder().decode(cursor));
        return Map.of(
                PK_ATTR, AttributeValue.fromS(pk),
                SK_ATTR, AttributeValue.fromS(SORT_KEY_VALUE)
        );
    }
}
