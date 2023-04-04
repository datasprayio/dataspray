package io.dataspray.store;


import jakarta.ws.rs.ClientErrorException;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;
import lombok.Value;

import java.util.Optional;

public interface BillingStore {

    StreamMetadata recordStreamEvent(
            String accountId,
            String targetId,
            Optional<String> authKeyOpt) throws ClientErrorException;

    StreamMetadata getStream(
            String accountId,
            String targetId) throws ClientErrorException;

    @Value
    class StreamMetadata {
        @NonNull Optional<EtlRetention> retentionOpt;
    }

    @Getter
    @AllArgsConstructor
    enum EtlRetention {
        DAY(1),
        WEEK(7),
        THREE_MONTHS(3 * 30),
        YEAR(366),
        THREE_YEARS(3 * 366);
        public static EtlRetention DEFAULT = THREE_MONTHS;
        int expirationInDays;
    }
}
