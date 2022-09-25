package io.dataspray.store;

import javax.enterprise.context.ApplicationScoped;
import java.util.Optional;

@ApplicationScoped
public class LimitlessBillingStore implements BillingStore {
    public static final String ACCOUNT_ID = "DC1EDCC9-F63C-4766-AF16-C6F3FFC6DC20";
    public static final String ACCOUNT_API_KEY = "B41B7CC9-BD31-46E3-8CD1-52B6A2BC203C";

    @Override
    public boolean recordStreamEvent(String accountId, String targetId, Optional<String> authKeyOpt) {
        return ACCOUNT_ID.equalsIgnoreCase(accountId)
                && Optional.of(ACCOUNT_API_KEY).equals(authKeyOpt);
    }
}
