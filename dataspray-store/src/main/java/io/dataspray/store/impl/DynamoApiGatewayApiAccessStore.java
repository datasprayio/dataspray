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

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.ImmutableSet;
import io.dataspray.common.DeployEnvironment;
import io.dataspray.singletable.IndexSchema;
import io.dataspray.singletable.ShardedIndexSchema;
import io.dataspray.singletable.SingleTable;
import io.dataspray.singletable.TableSchema;
import io.dataspray.store.ApiAccessStore;
import io.dataspray.store.CognitoJwtVerifier.VerifiedCognitoJwt;
import io.dataspray.store.OrganizationStore;
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
import software.amazon.awssdk.services.apigateway.model.DeleteUsagePlanKeyRequest;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static io.dataspray.common.DeployEnvironment.DEPLOY_ENVIRONMENT_PROP_NAME;

@Slf4j
@ApplicationScoped
public class DynamoApiGatewayApiAccessStore implements ApiAccessStore {

    public static final String ORGANIZATION_USAGE_PLAN_ID_PROP_NAME = "apiAccess.organization.usagePlan.id";
    public static final String ONE_RPS_USAGE_PLAN_ID_PROP_NAME = "apiAccess.one.usagePlan.id";
    public static final String TEN_RPS_USAGE_PLAN_ID_PROP_NAME = "apiAccess.ten.usagePlan.id";
    public static final String HUNDRED_RPS_USAGE_PLAN_ID_PROP_NAME = "apiAccess.hundred.usagePlan.id";
    public static final int API_KEY_LENGTH = 42;
    /** Usage Key prefix to satisfy req of at least 20 characters */
    public static final String USAGE_KEY_PREFIX = "dataspray-usage-key-";

    @ConfigProperty(name = DEPLOY_ENVIRONMENT_PROP_NAME)
    DeployEnvironment deployEnv;
    @ConfigProperty(name = ORGANIZATION_USAGE_PLAN_ID_PROP_NAME, defaultValue = "unset")
    String usagePlanIdOrganizationDefault;
    @ConfigProperty(name = ONE_RPS_USAGE_PLAN_ID_PROP_NAME, defaultValue = "unset")
    String usagePlanIdOrganizationOneRps;
    @ConfigProperty(name = TEN_RPS_USAGE_PLAN_ID_PROP_NAME, defaultValue = "unset")
    String usagePlanIdOrganizationTenRps;
    @ConfigProperty(name = HUNDRED_RPS_USAGE_PLAN_ID_PROP_NAME, defaultValue = "unset")
    String usagePlanIdOrganizationHundredRps;

    @Inject
    DynamoDbClient dynamo;
    @Inject
    SingleTable singleTable;
    @Inject
    ApiGatewayClient apiGatewayClient;
    @Inject
    KeygenUtil keygenUtil;
    @Inject
    OrganizationStore organizationStore;

    private TableSchema<ApiAccess> apiAccessSchema;
    private IndexSchema<ApiAccess> apiAccessByOrganizationSchema;
    private TableSchema<UsageKey> usageKeyByApiKeySchema;
    private ShardedIndexSchema<UsageKey> usageKeyScanAllSchema;
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
        usageKeyScanAllSchema = singleTable.parseShardedGlobalSecondaryIndexSchema(1, UsageKey.class);
    }

    @Override
    public ApiAccess createApiAccessForUser(String organizationName, String description, String username, UsageKeyType usageKeyType, Optional<ImmutableSet<String>> queueWhitelistOpt, Optional<Instant> expiryOpt) {
        return createApiAccess(new ApiAccess(
                keygenUtil.generateSecureApiKey(API_KEY_LENGTH),
                organizationName,
                description,
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
    public ApiAccess createApiAccessForTask(String apiKey, String organizationName, String description, String username, String taskId, String taskVersion, UsageKeyType usageKeyType, Optional<ImmutableSet<String>> queueWhitelistOpt) {
        return createApiAccess(new ApiAccess(
                apiKey,
                organizationName,
                description,
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

        // Create Api Gateway Usage Key if it doesn't exist yet
        getOrCreateUsageKeyApiKey(apiAccess.getUsageKeyType(), apiAccess.getApiKey());

        // Add api key in dynamo
        apiAccessSchema.put()
                .item(apiAccess)
                .execute(dynamo);

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
    public ImmutableSet<ApiAccess> getApiAccessesByUser(String organizationName, String username) {
        return getApiAccessesByOrganizationName(organizationName).stream()
                .filter(apiAccess -> OwnerType.USER.equals(apiAccess.getOwnerType()))
                .filter(apiAccess -> username.equals(apiAccess.getOwnerUsername()))
                .collect(ImmutableSet.toImmutableSet());
    }

    @Override
    public Optional<ApiAccess> getApiAccessesById(String organizationName, String id) {
        return getApiAccessesByOrganizationName(organizationName).stream()
                .filter(apiAccess -> id.equals(apiAccess.getId()))
                .findFirst();
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
        Optional<ApiAccess> apiAccessOpt = apiAccessSchema.get()
                .key(Map.of("apiKey", apiKey))
                .builder(b -> b.consistentRead(!useCache))
                .executeGet(dynamo)
                .filter(ApiAccess::isTtlNotExpired);

        // Update cache
        apiAccessByApiKeyCache.put(apiKey, apiAccessOpt);

        return apiAccessOpt;
    }

    @Override
    public void switchUsageKeyType(String organizationName, UsageKeyType type) {
        getOrCreateUsageKeyApiKeyForOrganization(organizationName, type);
        for (ApiAccess apiAccess : getApiAccessesByOrganizationName(organizationName)) {
            if (type.equals(apiAccess.getUsageKeyType())) {
                continue;
            }

            // Update usage key type
            apiAccessSchema.update()
                    .key(Map.of("apiKey", apiAccess.getApiKey()))
                    .set("usageKeyType", type)
                    .execute(dynamo);
        }
    }

    @Override
    public void revokeApiKey(String apiKey) {
        // Delete API Access
        apiAccessSchema.delete()
                .key(Map.of("apiKey", apiKey))
                .executeGetDeleted(dynamo)
                // Delete mapping to Usage Plan Key
                .flatMap(apiAccess -> usageKeyByApiKeySchema.delete()
                        .key(Map.of("usageKeyApiKey", apiAccess.getApiKey()))
                        .executeGetDeleted(dynamo))
                // Delete Usage Plan Key from API Gateway
                .ifPresent(usageKey -> apiGatewayClient.deleteUsagePlanKey(DeleteUsagePlanKeyRequest.builder()
                        .usagePlanId(Optional.ofNullable(usageKey.getUsagePlanId()).orElse(usagePlanIdOrganizationDefault))
                        .keyId(usageKey.getUsageKeyId()).build()));
        apiAccessByApiKeyCache.invalidate(apiKey);
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
    public String getOrCreateUsageKeyApiKeyForOrganization(String organizationName, UsageKeyType usageKeyType) {
        return getOrCreateUsageKeyApiKey(
                usageKeyType,
                getUsageKeyApiKey(
                        deployEnv,
                        usageKeyType,
                        Optional.of(organizationName)));
    }

    @Override
    public String getUsageKeyApiKey(VerifiedCognitoJwt verifiedCognitoJwt) {
        final UsageKeyType usageKeyType;
        final Optional<String> organizationNameOpt;
        if (verifiedCognitoJwt.getOrganizationNames().isEmpty()) {
            usageKeyType = UsageKeyType.GLOBAL;
            organizationNameOpt = Optional.empty();
            log.info("User {} is not part of any organization, falling back to {} usage key", verifiedCognitoJwt.getUsername(), usageKeyType);
        } else {
            usageKeyType = DEFAULT_ORGANIZATION_USAGE_KEY_TYPE;
            if (verifiedCognitoJwt.getOrganizationNames().size() == 1) {
                organizationNameOpt = Optional.of(verifiedCognitoJwt.getOrganizationNames().iterator().next());
            } else {
                organizationNameOpt = Optional.of(verifiedCognitoJwt.getOrganizationNames().stream().sorted().findFirst().get());
                log.info("User {} is part of multiple organizations, using usage key for {} out of {}", verifiedCognitoJwt.getUsername(), organizationNameOpt.get(), verifiedCognitoJwt.getOrganizationNames());
            }
        }
        return getUsageKeyApiKey(deployEnv, usageKeyType, organizationNameOpt);
    }

    @Override
    public String getUsageKeyApiKey(ApiAccess apiAccess) {
        return getUsageKeyApiKey(deployEnv, apiAccess.getUsageKeyType(), Optional.of(apiAccess.getOrganizationName()));
    }

    /**
     * Get or create a Usage Key for the given Usage Key's API key.
     */
    private String getOrCreateUsageKeyApiKey(UsageKeyType type, String apiKey) {

        final String usagePlanId;
        switch (type) {

            // Global and Unlimited are pre-created as part of CDK stack
            case GLOBAL:
            case UNLIMITED:
                // These are both created in ApiStack
                return apiKey;

            // Organization usage key is created on demand
            case ORGANIZATION:
                usagePlanId = usagePlanIdOrganizationDefault;
                break;
            case ORGANIZATION_ONE_RPS:
                usagePlanId = usagePlanIdOrganizationOneRps;
                break;
            case ORGANIZATION_TEN_RPS:
                usagePlanId = usagePlanIdOrganizationTenRps;
                break;
            case ORGANIZATION_HUNDRED_RPS:
                usagePlanId = usagePlanIdOrganizationHundredRps;
                break;

            default:
                throw new IllegalArgumentException("Unsupported UsageKeyType: " + type);
        }
        // We have fallen through, this means we are fetching an organization key

        // Lookup mapping from dynamo
        Optional<UsageKey> usageKeyOpt = usageKeyByApiKeySchema.get()
                .key(Map.of("usageKeyApiKey", apiKey))
                .executeGet(dynamo);

        // Return existing key if exists
        if (usageKeyOpt.isPresent()) {
            return usageKeyOpt.get().getUsageKeyApiKey();
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
        return usageKeyByApiKeySchema.put()
                .item(new UsageKey(apiKey, createApiKeyResponse.id(), usagePlanId))
                .executeGetNew(dynamo)
                .getUsageKeyApiKey();
    }

    @Override
    public void getAllUsageKeys(Consumer<ImmutableSet<UsageKey>> batchConsumer) {
        usageKeyScanAllSchema.querySharded()
                .executeStreamBatch(dynamo)
                .forEach(batchConsumer);
    }

    /**
     * Get the deterministic Usage Key API key for the given Usage Key type.
     * <p>
     * This method is used by both CDK to pre-create api keys and also by the API Gateway to fetch the key,
     * Do not change the format of the key without considering the implications.
     */
    public static String getUsageKeyApiKey(DeployEnvironment deployEnv, UsageKeyType type, Optional<String> organizationNameOpt) {
        if (type == UsageKeyType.GLOBAL || type == UsageKeyType.UNLIMITED) {
            // No organization name needed
            organizationNameOpt = Optional.empty();
        } else {
            checkState(organizationNameOpt.isPresent(), "Organization name must be present for organization usage key type " + type.name());
        }

        StringBuilder usageKeyApiKeyBuilder = new StringBuilder()
                .append(USAGE_KEY_PREFIX)
                .append(type.name());
        organizationNameOpt.ifPresent(organizationName -> usageKeyApiKeyBuilder
                .append("-")
                .append(organizationName));
        usageKeyApiKeyBuilder
                .append(deployEnv.getSuffix());
        return usageKeyApiKeyBuilder.toString();
    }
}
