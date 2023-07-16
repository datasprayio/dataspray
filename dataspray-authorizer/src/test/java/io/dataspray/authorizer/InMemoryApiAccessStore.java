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
import io.dataspray.store.ApiAccessStore;
import io.dataspray.store.DynamoApiGatewayApiAccessStore;
import io.dataspray.store.util.KeygenUtil;
import io.quarkus.test.Mock;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

@Mock
@ApplicationScoped
public class InMemoryApiAccessStore implements ApiAccessStore {
    @Inject
    KeygenUtil keygenUtil;

    private final Set<ApiAccess> apiKeys = Sets.newConcurrentHashSet();

    @Override
    public ApiAccess createApiAccess(String accountId, UsageKeyType usageKeyType, String description, Optional<ImmutableSet<String>> queueWhitelistOpt, Optional<Instant> expiryOpt) {
        ApiAccess apiAccess = new ApiAccess(
                keygenUtil.generateSecureApiKey(DynamoApiGatewayApiAccessStore.API_KEY_LENGTH),
                accountId,
                usageKeyType.getId(),
                description,
                queueWhitelistOpt.orElseGet(ImmutableSet::of),
                expiryOpt.map(Instant::getEpochSecond).orElse(null));
        apiKeys.add(apiAccess);
        return apiAccess;
    }

    @Override
    public ImmutableSet<ApiAccess> getApiAccessesByAccountId(String accountId) {
        return apiKeys.stream()
                .filter(apiAccess -> apiAccess.getAccountId().equals(accountId))
                .collect(ImmutableSet.toImmutableSet());
    }

    @Override
    public Optional<ApiAccess> getApiAccessByApiKey(String apiKey, boolean useCache) {
        return apiKeys.stream()
                .filter(apiAccess -> apiAccess.getApiKey().equals(apiKey))
                .findFirst();
    }

    @Override
    public void revokeApiKey(String apiKeyValue) {
        getApiAccessByApiKey(apiKeyValue, false).ifPresent(apiKeys::remove);
    }
}