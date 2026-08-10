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
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
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
import com.google.common.collect.ImmutableSet;
import io.dataspray.singletable.StringSerdeUtil;
import io.dataspray.store.CustomerDynamoStore;
import io.dataspray.store.StateStore;
import io.dataspray.store.util.WithCursor;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.DeleteItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException;
import software.amazon.awssdk.services.dynamodb.model.ReturnValue;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanResponse;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemResponse;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@ApplicationScoped
public class DynamoStateStore implements StateStore {

    private static final String SORT_KEY_VALUE = "state";
    private static final String TTL_ATTR = "ttlInEpochSec";
    private static final String PK_ATTR = "pk";
    private static final String SK_ATTR = "sk";
    /** Attribute names that make up the item key/TTL; user attributes must never overwrite these. */
    private static final ImmutableSet<String> RESERVED_ATTRS = ImmutableSet.of(PK_ATTR, SK_ATTR, TTL_ATTR);
    /** Bound the number of Scan pages fetched per listState call. */
    private static final int LIST_MAX_PAGES = 10;

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

        // Scan's limit applies before the filter, so keep paging until we have enough matches
        List<StateEntry> entries = new ArrayList<>();
        Optional<Map<String, AttributeValue>> exclusiveStartKey = cursor.map(this::decodeCursor);
        Optional<String> nextCursor = Optional.empty();
        for (int page = 0; page < LIST_MAX_PAGES && entries.size() < limit; page++) {
            ScanRequest.Builder scanBuilder = ScanRequest.builder()
                    .tableName(tableName)
                    .limit(limit)
                    .filterExpression(filterExpression)
                    .expressionAttributeNames(expressionNames)
                    .expressionAttributeValues(expressionValues);
            exclusiveStartKey.ifPresent(scanBuilder::exclusiveStartKey);

            ScanResponse response;
            try {
                response = dynamo.scan(scanBuilder.build());
            } catch (ResourceNotFoundException e) {
                log.warn("Table not found for organization: {}", organizationName);
                return new WithCursor<>(ImmutableList.of(), Optional.empty());
            }

            response.items().stream()
                    .limit((long) limit - entries.size())
                    .map(this::itemToStateEntry)
                    .forEach(entries::add);

            exclusiveStartKey = Optional.ofNullable(response.lastEvaluatedKey())
                    .filter(k -> !k.isEmpty());
            nextCursor = exclusiveStartKey.map(this::encodeCursor);
            if (exclusiveStartKey.isEmpty()) {
                break;
            }
        }

        return new WithCursor<>(entries, nextCursor);
    }

    @Override
    public Optional<StateEntry> getState(String organizationName, String[] keyParts) {
        validateKeyParts(keyParts);
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

        validateKeyParts(keyParts);
        attributes.keySet().forEach(attrName -> {
            if (attrName.isBlank()) {
                throw new IllegalArgumentException("Attribute names must not be blank");
            }
            if (RESERVED_ATTRS.contains(attrName)) {
                throw new IllegalArgumentException("Attribute name is reserved: " + attrName);
            }
        });

        String tableName = customerDynamoStore.getTableName(organizationName);
        String mergedKey = StringSerdeUtil.mergeStrings(keyParts);

        // Update only the supplied attributes so concurrent writers (e.g. a running task) don't lose theirs
        Map<String, String> expressionNames = new HashMap<>();
        Map<String, AttributeValue> expressionValues = new HashMap<>();
        List<String> setClauses = new ArrayList<>();
        int i = 0;
        for (Map.Entry<String, Object> entry : attributes.entrySet()) {
            String nameRef = "#a" + i;
            String valueRef = ":v" + i;
            expressionNames.put(nameRef, entry.getKey());
            expressionValues.put(valueRef, marshalValue(entry.getValue()));
            setClauses.add(nameRef + " = " + valueRef);
            i++;
        }
        if (ttlInSec.isPresent()) {
            long ttlEpoch = Instant.now().plusSeconds(ttlInSec.get()).getEpochSecond();
            expressionNames.put("#ttl", TTL_ATTR);
            expressionValues.put(":ttl", AttributeValue.fromN(String.valueOf(ttlEpoch)));
            setClauses.add("#ttl = :ttl");
        }

        UpdateItemRequest.Builder updateBuilder = UpdateItemRequest.builder()
                .tableName(tableName)
                .key(Map.of(
                        PK_ATTR, AttributeValue.fromS(mergedKey),
                        SK_ATTR, AttributeValue.fromS(SORT_KEY_VALUE)
                ))
                .returnValues(ReturnValue.ALL_NEW);
        if (!setClauses.isEmpty()) {
            updateBuilder
                    .updateExpression("SET " + String.join(", ", setClauses))
                    .expressionAttributeNames(expressionNames)
                    .expressionAttributeValues(expressionValues);
        }

        UpdateItemResponse response;
        try {
            response = dynamo.updateItem(updateBuilder.build());
        } catch (ResourceNotFoundException e) {
            log.warn("Table not found for organization: {}. State tables must be created via task deployment.", organizationName);
            throw new StateTableNotFoundException(organizationName, e);
        }

        return itemToStateEntry(response.attributes());
    }

    @Override
    public void deleteState(String organizationName, String[] keyParts) {
        validateKeyParts(keyParts);
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

    private void validateKeyParts(String[] keyParts) {
        if (keyParts == null || keyParts.length == 0) {
            throw new IllegalArgumentException("Key parts must not be empty");
        }
        for (String keyPart : keyParts) {
            if (keyPart == null || keyPart.isEmpty()) {
                throw new IllegalArgumentException("Key parts must not contain empty values");
            }
        }
    }

    private StateEntry itemToStateEntry(Map<String, AttributeValue> item) {
        String mergedKey = item.get(PK_ATTR).s();
        String[] keyParts = StringSerdeUtil.unMergeString(mergedKey);

        Optional<Long> ttl = Optional.ofNullable(item.get(TTL_ATTR))
                .filter(attr -> attr.n() != null)
                .map(attr -> Long.parseLong(attr.n()));

        Map<String, Object> attributes = new HashMap<>();
        item.forEach((attrName, attrValue) -> {
            if (!RESERVED_ATTRS.contains(attrName)) {
                attributes.put(attrName, unmarshalValue(attrValue));
            }
        });

        // Drop null values which ImmutableMap cannot hold (NUL-typed attributes)
        attributes.values().removeIf(Objects::isNull);
        return new StateEntry(keyParts, mergedKey, ImmutableMap.copyOf(attributes), ttl);
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
            Collection<?> coll = (Collection<?>) value;
            // Preserve order and duplicates by storing as a DynamoDB list
            return AttributeValue.fromL(coll.stream()
                    .map(this::marshalValue)
                    .collect(Collectors.toList()));
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
        // Use the explicit type: SDK v2 auto-construct collections are non-null even when absent
        return switch (attr.type()) {
            case S -> attr.s();
            case N -> new BigDecimal(attr.n());
            case BOOL -> attr.bool();
            case SS -> ImmutableList.copyOf(attr.ss());
            case NS -> attr.ns().stream()
                    .map(BigDecimal::new)
                    .collect(ImmutableList.toImmutableList());
            case BS -> attr.bs().stream()
                    .map(bytes -> Base64.getEncoder().encodeToString(bytes.asByteArray()))
                    .collect(ImmutableList.toImmutableList());
            case B -> Base64.getEncoder().encodeToString(attr.b().asByteArray());
            case L -> attr.l().stream()
                    .map(this::unmarshalValue)
                    .collect(Collectors.toList());
            case M -> {
                Map<String, Object> map = new HashMap<>();
                attr.m().forEach((key, val) -> map.put(key, unmarshalValue(val)));
                yield map;
            }
            case NUL -> null;
            default -> null;
        };
    }

    private String encodeCursor(Map<String, AttributeValue> lastKey) {
        // Simple Base64 encoding of pk value
        String pk = lastKey.get(PK_ATTR).s();
        return Base64.getEncoder().encodeToString(pk.getBytes());
    }

    private Map<String, AttributeValue> decodeCursor(String cursor) {
        String pk;
        try {
            pk = new String(Base64.getDecoder().decode(cursor));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid cursor", ex);
        }
        return Map.of(
                PK_ATTR, AttributeValue.fromS(pk),
                SK_ATTR, AttributeValue.fromS(SORT_KEY_VALUE)
        );
    }
}
