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
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import io.dataspray.singletable.IndexSchema;
import io.dataspray.singletable.ShardPageResult;
import io.dataspray.singletable.SingleTable;
import io.dataspray.singletable.TableSchema;
import io.dataspray.store.ApiAccessStore;
import io.dataspray.store.CognitoJwtVerifier;
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
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.DeleteItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import static com.google.common.base.Preconditions.checkArgument;

@Slf4j
@ApplicationScoped
public class DynamoApiGatewayApiAccessStore implements ApiAccessStore {

    public static final String USAGE_PLAN_ID_PROP_NAME = "apiAccess.usagePlan.id";
    public static final int API_KEY_LENGTH = 42;
    /** Usage Key prefix to satisfy req of at least 20 characters */
    public static final String USAGE_KEY_PREFIX = "dataspray-usage-key-";

    @ConfigProperty(name = USAGE_PLAN_ID_PROP_NAME, defaultValue = "unset")
    String usagePlanId;

    @Inject
    public DynamoDbClient dynamo;
    @Inject
    public SingleTable singleTable;
    @Inject
    public ApiGatewayClient apiGatewayClient;
    @Inject
    public KeygenUtil keygenUtil;

    private TableSchema<ApiAccess> apiAccessSchema;
    private IndexSchema<ApiAccess> apiAccessByOrganizationSchema;
    private TableSchema<UsageKey> usageKeyByApiKeySchema;
    private IndexSchema<UsageKey> usageKeyScanAllSchema;
    private Cache<String, Optional<ApiAccess>> apiAccessByApiKeyCache;

    @Startup
    void init() {
        apiAccessByApiKeyCache = CacheBuilder.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(1))
                .maximumSize(1000)
                .build();

        apiAccessSchema = singleTable.parseTableSchema(ApiAccess.class);
        apiAccessByOrganizationSchema = singleTable.parseGlobalSecondaryIndexSchema(1, ApiAccess.class);
        usageKeyByApiKeySchema = singleTable.parseTableSchema(UsageKey.class);
        usageKeyScanAllSchema = singleTable.parseGlobalSecondaryIndexSchema(1, UsageKey.class);
    }

    @Override
    public ApiAccess createApiAccessForUser(String organizationName, String username, UsageKeyType usageKeyType, Optional<ImmutableSet<String>> queueWhitelistOpt, Optional<Instant> expiryOpt) {
        return createApiAccess(new ApiAccess(
                keygenUtil.generateSecureApiKey(API_KEY_LENGTH),
                organizationName,
                OwnerType.USER,
                username,
                null,
                null,
                usageKeyType,
                queueWhitelistOpt.orElseGet(ImmutableSet::of),
                expiryOpt.map(Instant::getEpochSecond).orElse(null)));
    }

    @Override
    public String generateApiKey() {
        return keygenUtil.generateSecureApiKey(API_KEY_LENGTH);
    }

    @Override
    public ApiAccess createApiAccessForTask(String apiKey, String organizationName, String username, String taskId, String taskVersion, UsageKeyType usageKeyType, Optional<ImmutableSet<String>> queueWhitelistOpt) {
        return createApiAccess(new ApiAccess(
                apiKey,
                organizationName,
                OwnerType.TASK,
                username,
                taskId,
                taskVersion,
                usageKeyType,
                queueWhitelistOpt.orElseGet(ImmutableSet::of),
                null));
    }

    private ApiAccess createApiAccess(ApiAccess apiAccess) {
        checkArgument(apiAccess.isTtlNotExpired());

        // Create Api Gateway Usage Key for this organization if it doesn't exist yet
        if (UsageKeyType.ORGANIZATION.equals(apiAccess.getUsageKeyType())) {
            getOrCreateUsageKey(apiAccess.getApiKey());
        }

        // Add api key in dynamo
        dynamo.putItem(PutItemRequest.builder()
                .tableName(apiAccessSchema.tableName())
                .item(apiAccessSchema.toAttrMap(apiAccess))
                .build());

        // Add to cache and return
        apiAccessByApiKeyCache.put(apiAccess.getApiKey(), Optional.of(apiAccess));
        return apiAccess;
    }

    @Override
    public ImmutableSet<ApiAccess> getApiAccessesByOrganizationName(String organizationName) {
        return dynamo.queryPaginator(QueryRequest.builder()
                        .tableName(apiAccessByOrganizationSchema.tableName())
                        .indexName(apiAccessByOrganizationSchema.indexName())
                        .keyConditions(apiAccessByOrganizationSchema.attrMapToConditions(apiAccessByOrganizationSchema.primaryKey(Map.of(
                                "organizationName", organizationName))))
                        .build())
                .items()
                .stream()
                .map(apiAccessByOrganizationSchema::fromAttrMap)
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
        Optional<ApiAccess> apiAccessOpt = Optional.ofNullable(apiAccessSchema.fromAttrMap(dynamo.getItem(GetItemRequest.builder()
                        .tableName(apiAccessSchema.tableName())
                        .key(apiAccessSchema.primaryKey(Map.of(
                                "apiKey", apiKey)))
                        .consistentRead(!useCache)
                        .build()).item()))
                .filter(ApiAccess::isTtlNotExpired);

        // Update cache
        apiAccessByApiKeyCache.put(apiKey, apiAccessOpt);

        return apiAccessOpt;
    }

    @Override
    public void revokeApiKey(String apiKey) {
        dynamo.deleteItem(DeleteItemRequest.builder()
                .tableName(apiAccessSchema.tableName())
                .key(apiAccessSchema.primaryKey(Map.of(
                        "apiKey", apiKey)))
                .build());
    }

    @Override
    public void revokeApiKeysForTaskId(String organizationName, String taskId) {
        getApiAccessesByOrganizationName(organizationName).stream()
                .filter(apiAccess -> OwnerType.TASK.equals(apiAccess.getOwnerType()))
                .filter(apiAccess -> taskId.equals(apiAccess.getOwnerTaskId()))
                .map(ApiAccess::getApiKey)
                // Delete could be batched
                .forEach(this::revokeApiKey);
    }

    @Override
    public void revokeApiKeyForTaskVersion(String organizationName, String taskId, String taskVersion) {
        getApiAccessesByOrganizationName(organizationName).stream()
                .filter(apiAccess -> OwnerType.TASK.equals(apiAccess.getOwnerType()))
                .filter(apiAccess -> taskId.equals(apiAccess.getOwnerTaskId()))
                .filter(apiAccess -> taskVersion.equals(apiAccess.getOwnerTaskVersion()))
                .map(ApiAccess::getApiKey)
                .forEach(this::revokeApiKey);
    }

    @Override
    public UsageKey createOrGetUsageKeyForOrganization(String organizationName) {
        return getOrCreateUsageKey(getUsageKeyApiKey(
                UsageKeyType.ORGANIZATION,
                Optional.empty(),
                ImmutableSet.of(organizationName))
                .orElseThrow(IllegalStateException::new));
    }

    @Override
    public Optional<String> getUsageKeyApiKey(CognitoJwtVerifier.VerifiedCognitoJwt verifiedCognitoJwt) {
        return getUsageKeyApiKey(verifiedCognitoJwt.getUsageKeyType(), Optional.of(verifiedCognitoJwt.getUsername()), verifiedCognitoJwt.getOrganizationNames());
    }

    @Override
    public Optional<String> getUsageKeyApiKey(ApiAccess apiAccess) {
        return getUsageKeyApiKey(apiAccess.getUsageKeyType(), Optional.of(apiAccess.getOwnerUsername()), ImmutableSet.of(apiAccess.getOrganizationName()));
    }

    /**
     * Get or create a Usage Key for the given Usage Key's API key.
     */
    private UsageKey getOrCreateUsageKey(String apiKey) {

        // Lookup mapping from dynamo
        Optional<UsageKey> usageKeyOpt = Optional.ofNullable(usageKeyByApiKeySchema.fromAttrMap(dynamo.getItem(GetItemRequest.builder()
                .tableName(usageKeyByApiKeySchema.tableName())
                .key(usageKeyByApiKeySchema.primaryKey(Map.of(
                        "apiKey", apiKey)))
                .build()).item()));

        // Return existing key
        if (usageKeyOpt.isPresent()) {
            return usageKeyOpt.get();
        }

        // Create a new API Gateway Usage Key
        CreateApiKeyResponse createApiKeyResponse = apiGatewayClient.createApiKey(CreateApiKeyRequest.builder()
                .name(apiKey)
                .value(apiKey)
                .enabled(true).build());
        CreateUsagePlanKeyResponse createUsagePlanKeyResponse = apiGatewayClient.createUsagePlanKey(CreateUsagePlanKeyRequest.builder()
                .keyType("API_KEY")
                .keyId(createApiKeyResponse.id())
                .usagePlanId(usagePlanId).build());

        // Store mapping in dynamo
        UsageKey usageKey = new UsageKey(apiKey, createApiKeyResponse.id());
        dynamo.putItem(PutItemRequest.builder()
                .tableName(usageKeyByApiKeySchema.tableName())
                .item(usageKeyByApiKeySchema.toAttrMap(usageKey))
                .build());

        return usageKey;
    }

    @Override
    public void getAllUsageKeys(Consumer<ImmutableList<UsageKey>> batchConsumer) {
        Optional<String> cursorOpt = Optional.empty();
        do {
            ShardPageResult<UsageKey> result = singleTable.fetchShardNextPage(
                    dynamo,
                    usageKeyScanAllSchema,
                    cursorOpt,
                    100);
            cursorOpt = result.getCursorOpt();
            batchConsumer.accept(result.getItems());
        } while (cursorOpt.isPresent());
    }

    /**
     * Get the Usage Key API key for the given Usage Key type.
     */
    @VisibleForTesting
    public static Optional<String> getUsageKeyApiKey(UsageKeyType type, Optional<String> usernameOpt, ImmutableSet<String> organizationNames) {

        // For organization wide usage key, find the organization name
        Optional<String> organizationNameOpt = Optional.empty();
        if (UsageKeyType.ORGANIZATION.equals(type)) {
            if (organizationNames.isEmpty()) {
                type = UsageKeyType.GLOBAL;
                log.info("User {} is not part of any organization, falling back to dataspray-wide usage key", usernameOpt);
            } else if (organizationNames.size() > 1) {
                organizationNameOpt = Optional.of(organizationNames.stream().sorted().findFirst().get());
                log.info("User {} is part of multiple organizations, using usage key for {} out of {}", usernameOpt, organizationNameOpt, organizationNames);
            } else {
                // Only part of one organization, use it
                organizationNameOpt = Optional.of(organizationNames.iterator().next());
            }
        }

        return switch (type) {
            case UNLIMITED -> Optional.empty();
            // Share usage key across all API keys on the same organization.
            // Let's just use the account ID as usage key since it's not a secret.
            case ORGANIZATION -> Optional.of(USAGE_KEY_PREFIX + type.getId() + "-" + organizationNameOpt.get());
            case GLOBAL -> Optional.of(USAGE_KEY_PREFIX + type.getId() + "-GLOBAL");
            default -> throw new IllegalStateException("Unknown usage key type: " + type);
        };
    }
}
