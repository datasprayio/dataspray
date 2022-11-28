package io.dataspray.store;

import io.dataspray.store.BillingStore.EtlRetention;
import software.amazon.awssdk.services.glue.model.DataFormat;

public interface EtlStore {

    void putRecord(String customerId,
                   String targetId,
                   byte[] jsonBytes,
                   EtlRetention retention);

    void setTableDefinition(String customerId,
                            String targetId,
                            DataFormat dataFormat,
                            String schemaDefinition);
}
