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
import com.amazonaws.services.dynamodbv2.model.ReturnValue;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.ImmutableSet;
import io.dataspray.singletable.IndexSchema;
import io.dataspray.singletable.SingleTable;
import io.dataspray.singletable.TableSchema;
import io.dataspray.store.util.KeygenUtil;
import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.NotImplementedException;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import software.amazon.awssdk.services.apigateway.ApiGatewayClient;
import software.amazon.awssdk.services.apigateway.model.CreateApiKeyRequest;
import software.amazon.awssdk.services.apigateway.model.CreateApiKeyResponse;
import software.amazon.awssdk.services.apigateway.model.CreateUsagePlanKeyRequest;
import software.amazon.awssdk.services.apigateway.model.CreateUsagePlanKeyResponse;
import software.amazon.awssdk.services.apigateway.model.DeleteApiKeyRequest;
import software.amazon.awssdk.services.apigateway.model.GetApiKeyRequest;
import software.amazon.awssdk.services.apigateway.model.GetUsagePlanRequest;
import software.amazon.awssdk.services.apigateway.model.NotFoundException;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.stream.StreamSupport;

@Slf4j
@ApplicationScoped
public class DynamoApiGatewayApiAccessStore implements ApiAccessStore {

    public static final String USAGE_PLAN_ID_PROP_NAME = "apiAccess.usagePlan.id";
    public static final int API_KEY_LENGTH = 42;

    @ConfigProperty(name = USAGE_PLAN_ID_PROP_NAME)
    String usagePlanId;

    @Inject
    SingleTable singleTable;
    @Inject
    ApiGatewayClient apiGatewayClient;
    @Inject
    KeygenUtil keygenUtil;

    private TableSchema<ApiAccess> apiKeySchema;
    private IndexSchema<ApiAccess> apiKeyByAccountSchema;
    private Cache<String, Optional<ApiAccess>> apiAccessByApiKeyCache;

    @Startup
    void init(SingleTable singleTable) {
        apiAccessByApiKeyCache = CacheBuilder.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(1))
                .build();

        apiKeySchema = singleTable.parseTableSchema(ApiAccess.class);
        apiKeyByAccountSchema = singleTable.parseGlobalSecondaryIndexSchema(1, ApiAccess.class);
    }

    @Override
    public ApiAccess createApiAccess(String accountId, UsageKeyType usageKeyType, String description, Optional<ImmutableSet<String>> queueWhitelistOpt, Optional<Instant> expiryOpt) {
        String apiKeyValue = keygenUtil.generateSecureApiKey(API_KEY_LENGTH);

        // If this is a first API key on the account, create a new API Gateway API Key, otherwise use existing one
        // noinspection UnnecessaryLocalVariable
        String apiGatewayApiKey = accountId;
        try {
            apiGatewayClient.getApiKey(GetApiKeyRequest.builder()
                    .apiKey(apiGatewayApiKey)
                    .includeValue(false).build());
        } catch (NotFoundException ex) {
            CreateApiKeyResponse createApiKeyResponse = apiGatewayClient.createApiKey(CreateApiKeyRequest.builder()
                    .name(accountId)
                    .value(apiGatewayApiKey)
                    .description(description)
                    .enabled(true).build());
            CreateUsagePlanKeyResponse createUsagePlanKeyResponse = apiGatewayClient.createUsagePlanKey(CreateUsagePlanKeyRequest.builder()
                    .keyId(createApiKeyResponse.id())
                    .usagePlanId(usagePlanId).build());
        }

        ApiAccess apiKey = new ApiAccess(
                apiKeyValue,
                accountId,
                apiKeyId,
                description,
                queueWhitelistOpt.orElseGet(ImmutableSet::of),
                expiryOpt.map(Instant::getEpochSecond).orElse(null));
        apiKeySchema.table().putItem(apiKeySchema.toItem(apiKey));
        return apiKey;
    }

    @Override
    public String getOrCreateDefaultUsagePlanId() {
        apiGatewayClient.getUsagePlan(GetUsagePlanRequest.builder()
                .build())
    }

    @Override
    public ImmutableSet<ApiAccess> getApiAccessesByAccountId(String accountId) {
        return StreamSupport.stream(apiKeyByAccountSchema.index().query(new QuerySpec()
                                .withHashKey(apiKeyByAccountSchema.partitionKey(Map.of(
                                        "accountId", accountId)))
                                .withRangeKeyCondition(new RangeKeyCondition(apiKeyByAccountSchema.rangeKeyName())
                                        .beginsWith(apiKeyByAccountSchema.rangeValuePartial(Map.of()))))
                        .pages()
                        .spliterator(), false)
                .flatMap(p -> StreamSupport.stream(p.spliterator(), false))
                .map(apiKeyByAccountSchema::fromItem)
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
                    && apiAccessOptFromCache.get().getTtlInEpochSec() < Instant.now().getEpochSecond()) {
                    // Api key expired inside the cache, invalidate it
                    apiAccessByApiKeyCache.invalidate(apiKey);
                } else {
                    return apiAccessOptFromCache;
                }
            }
        }

        // Fetch from DB
        Optional<ApiAccess> apiAccessOpt = Optional.ofNullable(apiKeySchema.fromItem(apiKeySchema.table().getItem(new GetItemSpec()
                        .withPrimaryKey(apiKeySchema.primaryKey(Map.of(
                                "apiKeyValue", apiKey))))))
                .filter(apiAccess -> apiAccess.getTtlInEpochSec() >= Instant.now().getEpochSecond());

        // Update cache
        apiAccessByApiKeyCache.put(apiKey, apiAccessOpt);

        return apiAccessOpt;
    }

    @Override
    public void switchUsagePlanId(String apiKeyValue, String usagePlanId) {
        // TODO Need to first find current usagePlanKey (somehow ??), then delete it, then add new one
        // While usage plan key is deleted, api is non-functional, maybe it's better API key is rotated instead?
        throw new NotImplementedException();
    }

    @Override
    public void revokeApiKey(String apiKeyValue) {
        Optional.ofNullable(apiKeySchema.table().deleteItem(new DeleteItemSpec()
                                .withReturnValues(ReturnValue.ALL_OLD)
                                .withPrimaryKey(apiKeySchema.primaryKey(Map.of(
                                        "apiKeyValue", apiKeyValue))))
                        .getItem())
                .map(apiKeySchema::fromItem)
                .map(ApiAccess::getApiKeyId)
                .ifPresent(apiKeyId -> {
                    try {
                        apiGatewayClient.deleteApiKey(DeleteApiKeyRequest.builder()
                                .apiKey(apiKeyId).build());
                    } catch (NotFoundException ex) {
                        // Do nothing
                    }
                });
    }
}
