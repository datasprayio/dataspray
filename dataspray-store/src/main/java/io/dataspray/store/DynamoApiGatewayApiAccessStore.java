/*
 * Copyright 2023 Matus Faro
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

import com.amazonaws.services.dynamodbv2.document.RangeKeyCondition;
import com.amazonaws.services.dynamodbv2.document.spec.DeleteItemSpec;
import com.amazonaws.services.dynamodbv2.document.spec.GetItemSpec;
import com.amazonaws.services.dynamodbv2.document.spec.QuerySpec;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import io.dataspray.singletable.IndexSchema;
import io.dataspray.singletable.ShardPageResult;
import io.dataspray.singletable.SingleTable;
import io.dataspray.singletable.TableSchema;
import io.dataspray.store.util.KeygenUtil;
import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import software.amazon.awssdk.services.apigateway.ApiGatewayClient;
import software.amazon.awssdk.services.apigateway.model.CreateApiKeyRequest;
import software.amazon.awssdk.services.apigateway.model.CreateApiKeyResponse;
import software.amazon.awssdk.services.apigateway.model.CreateUsagePlanKeyRequest;
import software.amazon.awssdk.services.apigateway.model.CreateUsagePlanKeyResponse;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.StreamSupport;

import static com.google.common.base.Preconditions.checkArgument;

@Slf4j
@ApplicationScoped
public class DynamoApiGatewayApiAccessStore implements ApiAccessStore {

    public static final String USAGE_PLAN_ID_PROP_NAME = "apiAccess.usagePlan.id";
    public static final int API_KEY_LENGTH = 42;

    @ConfigProperty(name = USAGE_PLAN_ID_PROP_NAME, defaultValue = "unset")
    String usagePlanId;

    @Inject
    SingleTable singleTable;
    @Inject
    ApiGatewayClient apiGatewayClient;
    @Inject
    KeygenUtil keygenUtil;

    private TableSchema<ApiAccess> apiAccessSchema;
    private IndexSchema<ApiAccess> apiAccessByAccountSchema;
    private TableSchema<UsageKey> usageKeySchema;
    private IndexSchema<UsageKey> usageKeyScanSchema;
    private Cache<String, Optional<ApiAccess>> apiAccessByApiKeyCache;

    @Startup
    void init() {
        apiAccessByApiKeyCache = CacheBuilder.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(1))
                .build();

        apiAccessSchema = singleTable.parseTableSchema(ApiAccess.class);
        apiAccessByAccountSchema = singleTable.parseGlobalSecondaryIndexSchema(1, ApiAccess.class);
        usageKeySchema = singleTable.parseTableSchema(UsageKey.class);
        usageKeyScanSchema = singleTable.parseGlobalSecondaryIndexSchema(1, UsageKey.class);
    }

    @Override
    public ApiAccess createApiAccess(String accountId, UsageKeyType usageKeyType, String description, Optional<ImmutableSet<String>> queueWhitelistOpt, Optional<Instant> expiryOpt) {
        ApiAccess apiAccess = new ApiAccess(
                keygenUtil.generateSecureApiKey(API_KEY_LENGTH),
                accountId,
                usageKeyType.getId(),
                description,
                queueWhitelistOpt.orElseGet(ImmutableSet::of),
                expiryOpt.map(Instant::getEpochSecond).orElse(null));
        checkArgument(apiAccess.isTtlNotExpired());

        if (UsageKeyType.ACCOUNT_WIDE.equals(usageKeyType)) {
            getOrCreateUsageKeyForAccount(accountId);
        }

        apiAccessSchema.table().putItem(apiAccessSchema.toItem(apiAccess));

        apiAccessByApiKeyCache.put(apiAccess.getApiKey(), Optional.of(apiAccess));
        return apiAccess;
    }

    @Override
    public ImmutableSet<ApiAccess> getApiAccessesByAccountId(String accountId) {
        return StreamSupport.stream(apiAccessByAccountSchema.index().query(new QuerySpec()
                                .withHashKey(apiAccessByAccountSchema.partitionKey(Map.of(
                                        "accountId", accountId)))
                                .withRangeKeyCondition(new RangeKeyCondition(apiAccessByAccountSchema.rangeKeyName())
                                        .beginsWith(apiAccessByAccountSchema.rangeValuePartial(Map.of()))))
                        .pages()
                        .spliterator(), false)
                .flatMap(p -> StreamSupport.stream(p.spliterator(), false))
                .map(apiAccessByAccountSchema::fromItem)
                .filter(ApiAccess::isTtlNotExpired)
                .collect(ImmutableSet.toImmutableSet());
    }

    @Override
    public Optional<ApiAccess> getApiAccessByApiKey(String apiKey, boolean useCache) {
        // Check cache first
        if (useCache) {
            Optional<ApiAccess> apiAccessOptFromCache = apiAccessByApiKeyCache.getIfPresent(apiKey);
            //noinspection OptionalAssignedToNull
            if (apiAccessOptFromCache != null) {
                if (apiAccessOptFromCache.isPresent()
                    && !apiAccessOptFromCache.get().isTtlNotExpired()) {
                    // Api key expired inside the cache, invalidate it
                    apiAccessByApiKeyCache.invalidate(apiKey);
                } else {
                    return apiAccessOptFromCache;
                }
            }
        }

        // Fetch from DB
        Optional<ApiAccess> apiAccessOpt = Optional.ofNullable(apiAccessSchema.fromItem(apiAccessSchema.table().getItem(new GetItemSpec()
                        .withPrimaryKey(apiAccessSchema.primaryKey(Map.of(
                                "apiKey", apiKey)))
                        .withConsistentRead(!useCache))))
                .filter(ApiAccess::isTtlNotExpired);

        // Update cache
        apiAccessByApiKeyCache.put(apiKey, apiAccessOpt);

        return apiAccessOpt;
    }

    @Override
    public void revokeApiKey(String apiKey) {
        apiAccessSchema.table().deleteItem(new DeleteItemSpec().withPrimaryKey(apiAccessSchema.primaryKey(Map.of(
                "apiKey", apiKey))));
    }

    @Override
    public UsageKey getOrCreateUsageKeyForAccount(String accountId) {

        // Lookup mapping from dynamo
        Optional<UsageKey> usageKeyOpt = Optional.ofNullable(usageKeySchema.fromItem(usageKeySchema.table().getItem(new GetItemSpec()
                .withPrimaryKey(usageKeySchema.primaryKey(Map.of(
                        "accountId", accountId))))));

        // Return existing key
        if (usageKeyOpt.isPresent()) {
            return usageKeyOpt.get();
        }

        // Create a new API Gateway API Key
        CreateApiKeyResponse createApiKeyResponse = apiGatewayClient.createApiKey(CreateApiKeyRequest.builder()
                .name(accountId)
                // For account-wide usage key, the key itself is the account ID
                .value(accountId)
                .enabled(true).build());
        CreateUsagePlanKeyResponse createUsagePlanKeyResponse = apiGatewayClient.createUsagePlanKey(CreateUsagePlanKeyRequest.builder()
                .keyType("API_KEY")
                .keyId(createApiKeyResponse.id())
                .usagePlanId(usagePlanId).build());

        // Store mapping in dynamo
        UsageKey usageKey = new UsageKey(accountId, createApiKeyResponse.id());
        usageKeySchema.table().putItem(usageKeySchema.toItem(usageKey));

        return usageKey;
    }

    @Override
    public void getAllUsageKeys(Consumer<ImmutableList<UsageKey>> batchConsumer) {
        Optional<String> cursorOpt = Optional.empty();
        do {
            ShardPageResult<UsageKey> result = singleTable.fetchShardNextPage(
                    usageKeyScanSchema,
                    cursorOpt,
                    100);
            cursorOpt = result.getCursorOpt();
            batchConsumer.accept(result.getItems());
        } while (cursorOpt.isPresent());
    }
}
