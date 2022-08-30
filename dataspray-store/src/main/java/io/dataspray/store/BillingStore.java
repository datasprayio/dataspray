package io.dataspray.store;


import java.util.Optional;

public interface BillingStore {
    String IMPL_LIMITLESS = "limitless";

    boolean recordStreamEvent(String accountId, String targetId, Optional<String> authKeyOpt);
}
