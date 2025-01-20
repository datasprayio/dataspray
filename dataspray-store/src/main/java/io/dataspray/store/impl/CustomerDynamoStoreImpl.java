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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.gson.Gson;
import com.jcabi.aspects.Cacheable;
import io.dataspray.common.DeployEnvironment;
import io.dataspray.singletable.DynamoConvertersProxy;
import io.dataspray.singletable.DynamoConvertersProxy.MarshallerAttrVal;
import io.dataspray.singletable.SingleTable;
import io.dataspray.singletable.StringSerdeUtil;
import io.dataspray.singletable.TableType;
import io.dataspray.store.CustomerDynamoStore;
import io.dataspray.store.CustomerLogger;
import io.dataspray.store.TopicStore;
import io.dataspray.store.TopicStore.Store;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException;
import software.amazon.awssdk.services.dynamodb.model.ReturnValue;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

import static io.dataspray.common.DeployEnvironment.DEPLOY_ENVIRONMENT_PROP_NAME;
import static io.dataspray.singletable.TableType.*;

@Slf4j
@ApplicationScoped
public class CustomerDynamoStoreImpl implements CustomerDynamoStore {

    @ConfigProperty(name = DEPLOY_ENVIRONMENT_PROP_NAME)
    DeployEnvironment deployEnv;
    @Inject
    DynamoDbClient dynamo;
    @Inject
    CustomerLogger customerLog;
    @Inject
    Gson gson;


    /** From DynamoMapperImpl */
    private final ImmutableMap<Class<?>, MarshallerAttrVal> marshallersByClass = DynamoConvertersProxy.proxy(List.of(), List.of()).getMp()
            .stream().collect(ImmutableMap.toImmutableMap(Map.Entry::getKey, Map.Entry::getValue));
    /** From DynamoMapperImpl */
    private final MarshallerAttrVal gsonMarshallerAttrVal = o -> AttributeValue.fromS(gson.toJson(o));

    @Override
    public String getTableName(String organizationName) {
        return LambdaDeployerImpl.CUSTOMER_FUN_DYNAMO_OR_ROLE_NAME_PREFIX_GETTER.apply(deployEnv) + organizationName;
    }

    @Override
    public SingleTable getSingleTable(String organizationName) {
        return SingleTable.builder()
                .tableName(getTableName(organizationName))
                .overrideGson(gson)
                .build();
    }

    @Override
    public SingleTable createTableIfNotExists(String organizationName, long gsiCount, long lsiCount) {
        SingleTable singleTable = getSingleTable(organizationName);
        singleTable.createTableIfNotExists(dynamo, (int) lsiCount, (int) gsiCount);
        return singleTable;
    }

    @Override
    public Void write(
            String organizationName,
            Store definition,
            Map<String, Object> messageJson) {

        PutItemRequest request = PutItemRequest.builder()
                .tableName(getTableName(organizationName))
                .item(getMapper(definition).apply(messageJson))
                .returnValues(ReturnValue.NONE)
                .build();

        try {
            dynamo.putItem(request);
        } catch (ResourceNotFoundException ex) {
            // Table doesn't exist
            // Infer Gsi and Lsi count
            long maxGsi = definition.getKeys().stream()
                    .filter(k -> Gsi.equals(k.getType()))
                    .mapToLong(TopicStore.Key::getIndexNumber)
                    .max()
                    .orElse(0L);
            long maxLsi = definition.getKeys().stream()
                    .filter(k -> Lsi.equals(k.getType()))
                    .mapToLong(TopicStore.Key::getIndexNumber)
                    .max()
                    .orElse(0L);
            // Create table
            createTableIfNotExists(organizationName, maxLsi, maxGsi);
            // Retry put request
            dynamo.putItem(request);
        }
        return null;
    }

    @Cacheable(lifetime = DynamoTopicStore.CACHE_EXPIRY_IN_MINUTES)
    private Function<Map<String, Object>, Map<String, AttributeValue>> getMapper(Store definition) {
        var keyMappers = definition.getKeys().stream()
                .flatMap(this::getKeyMapper)
                .collect(ImmutableList.toImmutableList());
        var attributeMapper = getAttributeMapper(definition);
        var ttlMapper = getTtlMapper(definition);
        return messageJson -> {
            Map<String, AttributeValue> item = Maps.newHashMap();
            attributeMapper.accept(messageJson, item);
            ttlMapper.accept(item);
            keyMappers.forEach(mapper -> mapper.accept(messageJson, item));
            return item;
        };
    }

    private Consumer<Map<String, AttributeValue>> getTtlMapper(Store definition) {
        long ttlInSec = definition.getTtlInSec();
        return item -> item.put(
                SingleTable.TTL_IN_EPOCH_SEC_ATTR_NAME,
                AttributeValue.fromN(Long.toString(Instant.now().plusSeconds(ttlInSec).getEpochSecond())));
    }

    private BiConsumer<Map<String, Object>, Map<String, AttributeValue>> getAttributeMapper(Store definition) {

        ImmutableSet<String> whitelist = definition.getWhitelist();
        ImmutableSet<String> blacklist = definition.getBlacklist();

        // Whitelist: iterate over list and pick out from message (skip over any blacklist keys)
        if (!whitelist.isEmpty()) {
            ImmutableSet<String> finalWhitelist = ImmutableSet.copyOf(Sets.difference(whitelist, blacklist));
            return (messageJson, item) -> {
                finalWhitelist.forEach(key -> {
                    Object value = messageJson.get(key);
                    if (value == null) {
                        return;
                    }
                    item.put(key, findMarshallerAttrVal(value.getClass()).marshall(value));
                });
            };
        }

        // Blacklist: iterate over message and skip form blacklist
        if (!blacklist.isEmpty()) {
            return (messageJson, item) -> {
                messageJson.forEach((key, value) -> {
                    if (value == null) {
                        return;
                    }
                    if (blacklist.contains(key)) {
                        return;
                    }
                    item.put(key, findMarshallerAttrVal(value.getClass()).marshall(value));
                });
            };
        }

        // No whitelist or blacklist: iterate over message and add all
        return (messageJson, item) -> messageJson.forEach((key, value) -> {
            if (value == null) {
                return;
            }
            item.put(key, findMarshallerAttrVal(value.getClass()).marshall(value));
        });
    }

    private Stream<BiConsumer<Map<String, Object>, Map<String, AttributeValue>>> getKeyMapper(TopicStore.Key definition) {

        // Primary key
        String pkFieldName = getPartitionKeyName(definition.getType(), definition.getIndexNumber());
        List<Function<Map<String, Object>, String>> pkPartMappers = definition.getPkParts().stream()
                .<Function<Map<String, Object>, String>>map(fieldName -> messageJson -> {
                    Object value = messageJson.get(fieldName);
                    return gson.toJson(value);
                })
                .toList();

        // Sort key
        String skFieldName = getRangeKeyName(definition.getType(), definition.getIndexNumber());
        String rangePrefix = definition.getRangePrefix();
        List<Function<Map<String, Object>, String>> skPartMappers = definition.getSkParts().stream()
                .<Function<Map<String, Object>, String>>map(fieldName -> messageJson -> {
                    Object value = messageJson.get(fieldName);
                    return gson.toJson(value);
                })
                .toList();

        return Stream.of(
                // Primary key
                (messageJson, item) -> item.put(pkFieldName, AttributeValue.fromS(
                        StringSerdeUtil.mergeStrings(
                                pkPartMappers.stream()
                                        .map(m -> m.apply(messageJson))
                                        .toArray(String[]::new)))),
                // Sort key
                (messageJson, item) -> item.put(skFieldName, AttributeValue.fromS(
                        StringSerdeUtil.mergeStrings(Stream.concat(
                                        Stream.of(rangePrefix),
                                        skPartMappers.stream()
                                                .map(m -> m.apply(messageJson)))
                                .toArray(String[]::new)))));
    }

    /** From DynamoMapperImpl */
    private String getPartitionKeyName(TableType type, long indexNumber) {
        return type == Primary || type == Lsi
                ? "pk"
                : type.name().toLowerCase() + "pk" + indexNumber;
    }

    /** From DynamoMapperImpl */
    private String getRangeKeyName(TableType type, long indexNumber) {
        return type == Primary
                ? "sk"
                : type.name().toLowerCase() + "sk" + indexNumber;
    }

    private MarshallerAttrVal findMarshallerAttrVal(Class itemClazz) {
        return marshallersByClass.getOrDefault(itemClazz, gsonMarshallerAttrVal);
    }
}
