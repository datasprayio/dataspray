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
    @ConfigProperty(name = "accountstore.singletenant.account.id", defaultValue = "DC1EDCC9-F63C-4766-AF16-C6F3FFC6DC20")
    public String accountId;
    @ConfigProperty(name = "accountstore.singletenant.account.email", defaultValue = "dataspray@example.com")
    String accountEmail;
    @VisibleForTesting
    @ConfigProperty(name = "accountstore.singletenant.account.apikey", defaultValue = "B41B7CC9-BD31-46E3-8CD1-52B6A2BC203C")
    public String accountApiKey;
    @ConfigProperty(name = "accountstore.singletenant.account.etlRetention")
    Optional<EtlRetention> accountEtlRetentionOpt;

    @Override
    public Account getAccount(String accountId) {
        return new Account(accountId, accountEmail, ImmutableSet.of());
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
