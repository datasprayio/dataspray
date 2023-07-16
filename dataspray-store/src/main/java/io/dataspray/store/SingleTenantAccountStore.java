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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;
import io.quarkus.arc.properties.IfBuildProperty;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.ws.rs.ClientErrorException;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Optional;

@Alternative
@Priority(1)
@ApplicationScoped
@IfBuildProperty(name = "accountstore.singletenant.enable", stringValue = "true")
public class SingleTenantAccountStore implements AccountStore {

    @VisibleForTesting
    @ConfigProperty(name = "accountstore.singletenant.account.id")
    public String accountId;
    @ConfigProperty(name = "accountstore.singletenant.account.email")
    String accountEmail;
    @ConfigProperty(name = "accountstore.singletenant.account.apikey")
    String accountApiKey;
    @ConfigProperty(name = "accountstore.singletenant.account.etlRetention")
    Optional<EtlRetention> accountEtlRetentionOpt;

    private Account getAccount() {
        return new Account(accountId, accountEmail, ImmutableSet.of());
    }

    @Override
    public Optional<Account> getAccount(String accountId) {
        if (!accountId.equals(this.accountId)) {
            return Optional.empty();
        }
        return Optional.of(getAccount());
    }

    @Override
    public StreamMetadata authorizeStreamPut(String accountId, String targetId, Optional<String> authKeyOpt) throws ClientErrorException {
        if (!this.accountId.equalsIgnoreCase(accountId)
            || !Optional.of(this.accountApiKey).equals(authKeyOpt)) {
            throw new ClientErrorException(Response.Status.PAYMENT_REQUIRED);
        }

        return getStream(accountId, targetId);
    }

    @Override
    public StreamMetadata getStream(String accountId, String targetId) throws ClientErrorException {
        return new StreamMetadata(accountEtlRetentionOpt);
    }
}
