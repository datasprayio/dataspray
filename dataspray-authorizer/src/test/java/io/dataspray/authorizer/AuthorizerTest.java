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
import io.dataspray.singletable.SingleTable;
import io.dataspray.singletable.TableSchema;
import io.dataspray.store.ApiAccessStore;
import io.dataspray.store.util.KeygenUtil;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.Optional;

import static io.dataspray.store.DynamoApiGatewayApiAccessStore.API_KEY_LENGTH;

@Slf4j
@QuarkusTest
class AuthorizerTest extends AuthorizerBase {

    @Inject
    ApiAccessStore apiAccessStore;
    @Inject
    SingleTable singleTable;
    @Inject
    KeygenUtil keygenUtil;

    @Override
    protected ApiAccessStore.ApiAccess createApiAccess(
            String accountId,
            ApiAccessStore.UsageKeyType usageKeyType,
            String description,
            Optional<ImmutableSet<String>> queueWhitelistOpt,
            Optional<Instant> expiryOpt) {

        ApiAccessStore.ApiAccess apiAccess = new ApiAccessStore.ApiAccess(
                keygenUtil.generateSecureApiKey(API_KEY_LENGTH),
                accountId,
                usageKeyType.getId(),
                description,
                queueWhitelistOpt.orElseGet(ImmutableSet::of),
                expiryOpt.map(Instant::getEpochSecond).orElse(null));

        TableSchema<ApiAccessStore.ApiAccess> apiAccessSchema = singleTable.parseTableSchema(ApiAccessStore.ApiAccess.class);
        apiAccessSchema.table().putItem(apiAccessSchema.toItem(apiAccess));

        return apiAccess;
    }
}
