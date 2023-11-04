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

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import io.dataspray.singletable.SingleTable;
import io.dataspray.singletable.TableSchema;
import io.dataspray.store.ApiAccessStore.ApiAccess;
import io.dataspray.store.ApiAccessStore.UsageKey;
import io.dataspray.store.ApiAccessStore.UsageKeyType;
import io.dataspray.store.util.KeygenUtil;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.apigateway.ApiGatewayClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Slf4j
@QuarkusTest
@QuarkusTestResource(StoreLocalstackLifecycleManager.class)
public class ApiAccessStoreTest {

    @Inject
    ApiAccessStore apiAccessStore;
    @Inject
    DynamoDbClient dynamo;
    @Inject
    SingleTable singleTable;
    @Inject
    KeygenUtil keygenUtil;
    @Inject
    ApiGatewayClient apiGatewayClient;

    @Test
    public void testSet() throws Exception {
        String accountId = UUID.randomUUID().toString();
        ApiAccess apiAccess = apiAccessStore.createApiAccess(
                accountId,
                UsageKeyType.UNLIMITED,
                "description",
                Optional.empty(),
                Optional.empty());

        assertEquals(accountId, apiAccess.getAccountId());
        assertEquals(UsageKeyType.UNLIMITED, apiAccess.getUsageKeyType());
        assertEquals("description", apiAccess.getDescription());
        assertEquals(Set.of(), apiAccess.getQueueWhitelist());
        assertTrue(apiAccess.isTtlNotExpired());
    }

    @Test
    public void testSetUsageAccountWide() throws Exception {
        String accountId = UUID.randomUUID().toString();
        ApiAccess apiAccess1 = apiAccessStore.createApiAccess(
                accountId,
                // Should cause a fetch miss for usage key, then create it
                UsageKeyType.ACCOUNT_WIDE,
                "description",
                Optional.of(ImmutableSet.of("queue1", "queue2")),
                Optional.of(Instant.now().plusSeconds(300)));

        assertTrue(usageKeyExists(accountId));
        assertEquals(Set.of("queue1", "queue2"), apiAccess1.getQueueWhitelist());
        assertTrue(apiAccess1.isTtlNotExpired());

        ApiAccess apiAccess2 = apiAccessStore.createApiAccess(
                accountId,
                // Should fetch the previously created usage key
                UsageKeyType.ACCOUNT_WIDE,
                "description",
                Optional.of(ImmutableSet.of("queue1", "queue2")),
                Optional.of(Instant.now().plusSeconds(300)));

        assertTrue(usageKeyExists(accountId));
    }

    @Test
    public void testCannotCreateExpired() throws Exception {
        Assertions.assertThrows(IllegalArgumentException.class, () ->
                apiAccessStore.createApiAccess(
                        UUID.randomUUID().toString(),
                        UsageKeyType.UNLIMITED,
                        "description",
                        Optional.empty(),
                        Optional.of(Instant.now().minusSeconds(10))));
    }

    @Test
    public void testGetByApiKey() throws Exception {
        ApiAccess apiAccess = createApiAccessInDb(
                UUID.randomUUID().toString(),
                UsageKeyType.UNLIMITED,
                "description",
                Optional.empty(),
                Optional.empty());

        // Cache not used
        assertEquals(Optional.of(apiAccess), apiAccessStore.getApiAccessByApiKey(apiAccess.getApiKey(), false));
        // Cache miss
        assertEquals(Optional.of(apiAccess), apiAccessStore.getApiAccessByApiKey(apiAccess.getApiKey(), true));
        // Cache hit
        assertEquals(Optional.of(apiAccess), apiAccessStore.getApiAccessByApiKey(apiAccess.getApiKey(), true));

        assertEquals(Optional.empty(), apiAccessStore.getApiAccessByApiKey("non-existent", false));
        assertEquals(Optional.empty(), apiAccessStore.getApiAccessByApiKey("non-existent", true));
    }

    @Test
    public void testGetByApiKeyExpired() throws Exception {
        ApiAccess apiAccess = createApiAccessInDb(
                UUID.randomUUID().toString(),
                UsageKeyType.UNLIMITED,
                "description",
                Optional.empty(),
                Optional.of(Instant.now().minusSeconds(10)));

        // Cache not used
        assertEquals(Optional.empty(), apiAccessStore.getApiAccessByApiKey(apiAccess.getApiKey(), false));
        // Cache miss
        assertEquals(Optional.empty(), apiAccessStore.getApiAccessByApiKey(apiAccess.getApiKey(), true));
        // Cache hit
        assertEquals(Optional.empty(), apiAccessStore.getApiAccessByApiKey(apiAccess.getApiKey(), true));
    }

    @Test
    public void testGetByAccount() throws Exception {
        ApiAccess apiAccess = createApiAccessInDb(
                UUID.randomUUID().toString(),
                UsageKeyType.UNLIMITED,
                "description",
                Optional.empty(),
                Optional.empty());

        assertEquals(Set.of(apiAccess), apiAccessStore.getApiAccessesByAccountId(apiAccess.getAccountId()));

        ApiAccess apiAccess2 = apiAccessStore.createApiAccess(
                apiAccess.getAccountId(),
                UsageKeyType.UNLIMITED,
                "description2",
                Optional.of(ImmutableSet.of("queue1", "queue2")),
                Optional.of(Instant.now().plusSeconds(300)));

        assertEquals(Set.of(apiAccess, apiAccess2), apiAccessStore.getApiAccessesByAccountId(apiAccess.getAccountId()));

        assertEquals(Set.of(), apiAccessStore.getApiAccessesByAccountId("non-existent"));
    }

    @Test
    public void testGetByAccountExpired() throws Exception {
        ApiAccess apiAccess = createApiAccessInDb(
                UUID.randomUUID().toString(),
                UsageKeyType.UNLIMITED,
                "description",
                Optional.empty(),
                Optional.of(Instant.now().minusSeconds(10)));

        assertEquals(Set.of(), apiAccessStore.getApiAccessesByAccountId(apiAccess.getAccountId()));
    }

    @Test
    public void testRevoke() throws Exception {
        ApiAccess apiAccess = createApiAccessInDb(
                UUID.randomUUID().toString(),
                UsageKeyType.UNLIMITED,
                "description",
                Optional.empty(),
                Optional.empty());

        // Cache miss, populates cache
        assertEquals(Optional.of(apiAccess), apiAccessStore.getApiAccessByApiKey(apiAccess.getApiKey(), true));

        apiAccessStore.revokeApiKey(apiAccess.getApiKey());

        // Cache hit, should be still present in cache
        assertEquals(Optional.of(apiAccess), apiAccessStore.getApiAccessByApiKey(apiAccess.getApiKey(), true));
        // Cache not used, realizes key is revoked, invalidates cache
        assertEquals(Optional.empty(), apiAccessStore.getApiAccessByApiKey(apiAccess.getApiKey(), false));
        // Cache hit, should be revoked
        assertEquals(Optional.empty(), apiAccessStore.getApiAccessByApiKey(apiAccess.getApiKey(), true));
        // Also access by account id should be revoked
        assertEquals(Set.of(), apiAccessStore.getApiAccessesByAccountId(apiAccess.getAccountId()));
    }

    @Test
    public void testUsageKeyCreateGet() throws Exception {
        String accountId1 = UUID.randomUUID().toString();
        UsageKey usageKey1 = apiAccessStore.getOrCreateUsageKeyForAccount(accountId1);
        assertEquals(accountId1, usageKey1.getAccountId());

        String accountId2 = UUID.randomUUID().toString();
        UsageKey usageKey2a = apiAccessStore.getOrCreateUsageKeyForAccount(accountId2);
        UsageKey usageKey2b = apiAccessStore.getOrCreateUsageKeyForAccount(accountId2);
        assertEquals(accountId2, usageKey2a.getAccountId());
        assertEquals(accountId2, usageKey2b.getAccountId());
        assertEquals(usageKey2a.getUsageKeyId(), usageKey2b.getUsageKeyId());
    }

    @Test
    public void testUsageKeyScan() throws Exception {
        UsageKey usageKey1 = apiAccessStore.getOrCreateUsageKeyForAccount(UUID.randomUUID().toString());
        UsageKey usageKey2 = apiAccessStore.getOrCreateUsageKeyForAccount(UUID.randomUUID().toString());

        Set<UsageKey> allUsageKeys = Sets.newHashSet();
        apiAccessStore.getAllUsageKeys(allUsageKeys::addAll);

        assertTrue(allUsageKeys.contains(usageKey1));
        assertTrue(allUsageKeys.contains(usageKey2));
    }

    /**
     * Identical to apiAccessStore.createApiAccess but sidelines the code being tested
     * as well as doesn't throw if you try to insert an expired key.
     */
    private ApiAccess createApiAccessInDb(
            String accountId,
            UsageKeyType usageKeyType,
            String description,
            Optional<ImmutableSet<String>> queueWhitelistOpt,
            Optional<Instant> expiryOpt) {

        // Create access object
        ApiAccess apiAccess = new ApiAccess(
                keygenUtil.generateSecureApiKey(DynamoApiGatewayApiAccessStore.API_KEY_LENGTH),
                accountId,
                usageKeyType.getId(),
                description,
                queueWhitelistOpt.orElseGet(ImmutableSet::of),
                expiryOpt.map(Instant::getEpochSecond).orElse(null));

        // Parse dynamo schema
        TableSchema<ApiAccess> apiAccessSchema = singleTable.parseTableSchema(ApiAccess.class);

        // Insert into dynamo
        dynamo.putItem(PutItemRequest.builder()
                .tableName(apiAccessSchema.tableName())
                .item(apiAccessSchema.toAttrMap(apiAccess))
                .build());

        return apiAccess;
    }

    private boolean usageKeyExists(String accountId) {
        TableSchema<UsageKey> usageKeySchema = singleTable.parseTableSchema(UsageKey.class);
        Optional<UsageKey> usageKeyOpt = Optional.ofNullable(usageKeySchema.fromAttrMap(dynamo.getItem(GetItemRequest.builder()
                .tableName(usageKeySchema.tableName())
                .key(usageKeySchema.primaryKey(Map.of(
                        "accountId", accountId)))
                .build()).item()));
        return usageKeyOpt.isPresent();
    }
}
