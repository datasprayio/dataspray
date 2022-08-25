package io.dataspray.store;


public interface BillingStore {
    String IMPL_LIMITLESS = "limitless";

    boolean recordStreamEvent(String accountId);
}
