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

package io.dataspray.authorizer;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import io.dataspray.store.ApiKeyStore;
import io.dataspray.store.DynamoApiGatewayApiKeyStore;
import io.dataspray.store.util.KeygenUtil;
import jakarta.inject.Inject;
import org.apache.commons.lang.NotImplementedException;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class InMemoryApiKeyStore implements ApiKeyStore {
    @Inject
    KeygenUtil keygenUtil;

    private final Set<ApiKey> apiKeys = Sets.newConcurrentHashSet();

    @Override
    public ApiKey createApiKey(String accountId, String usagePlanId, String description, Optional<ImmutableSet<String>> queueWhitelistOpt, Optional<Instant> expiryOpt) {
        String apiKeyValue = keygenUtil.generateSecureApiKey(DynamoApiGatewayApiKeyStore.API_KEY_LENGTH);
        ApiKey apiKey = new ApiKey(
                apiKeyValue,
                accountId,
                UUID.randomUUID().toString(),
                description,
                queueWhitelistOpt.orElseGet(ImmutableSet::of),
                expiryOpt.map(Instant::getEpochSecond).orElse(null));
        apiKeys.add(apiKey);
        return apiKey;
    }

    @Override
    public ImmutableSet<ApiKey> getApiKeysByAccountId(String accountId) {
        return apiKeys.stream()
                .filter(apiKey -> apiKey.getAccountId().equals(accountId))
                .collect(ImmutableSet.toImmutableSet());
    }

    @Override
    public String getApiKeyValueById(String apiKeyId) {
        return apiKeys.stream()
                .filter(apiKey -> apiKey.getApiKeyId().equals(apiKeyId))
                .findFirst()
                .orElseThrow()
                .getApiKeyValue();
    }

    @Override
    public Optional<ApiKey> getApiKey(String apiKeyValue, boolean useCache) {
        return apiKeys.stream()
                .filter(apiKey -> apiKey.getApiKeyValue().equals(apiKeyValue))
                .findFirst();
    }

    @Override
    public void switchUsagePlanId(String apiKeyValue, String usagePlanId) {
        throw new NotImplementedException();
    }

    @Override
    public void revokeApiKey(String apiKeyValue) {
        getApiKey(apiKeyValue, false).ifPresent(apiKeys::remove);
    }
}
