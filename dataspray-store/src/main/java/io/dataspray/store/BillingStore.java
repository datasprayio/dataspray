package io.dataspray.store;


import java.util.Optional;

public interface BillingStore {

    boolean recordStreamEvent(String accountId, String targetId, Optional<String> authKeyOpt);
}
