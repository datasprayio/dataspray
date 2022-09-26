package io.dataspray.store;

import io.dataspray.store.BillingStore.EtlRetention;

public interface EtlStore {

    void putRecord(String accountId,
                   String targetId,
                   byte[] jsonBytes,
                   EtlRetention retention);
}
