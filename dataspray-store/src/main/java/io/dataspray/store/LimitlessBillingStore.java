package io.dataspray.store;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Named;

import static io.dataspray.store.BillingStore.IMPL_LIMITLESS;

@ApplicationScoped
@Named(IMPL_LIMITLESS)
public class LimitlessBillingStore implements BillingStore {

    @Override
    public boolean recordStreamEvent(String accountId) {
        return true;
    }
}
