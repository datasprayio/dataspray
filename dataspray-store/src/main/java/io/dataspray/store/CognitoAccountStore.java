package io.dataspray.store;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.ClientErrorException;

import java.util.Optional;

@ApplicationScoped
public class CognitoAccountStore implements AccountStore {

    @Override
    public StreamMetadata recordStreamEvent(String accountId, String targetId, Optional<String> authKeyOpt) throws ClientErrorException {
        return getStream(accountId, targetId);
    }

    @Override
    public StreamMetadata getStream(String accountId, String targetId) throws ClientErrorException {
        return new StreamMetadata(Optional.of(EtlRetention.DEFAULT));
    }
}
