package io.dataspray.store;


import com.google.common.collect.ImmutableSet;
import jakarta.ws.rs.ClientErrorException;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.Value;

import java.util.Optional;

public interface AccountStore {

    Account getAccount(String accountId);

    StreamMetadata authorizeStreamPut(
            String accountId,
            String targetId,
            Optional<String> authKeyOpt) throws ClientErrorException;

    StreamMetadata getStream(
            String accountId,
            String targetId) throws ClientErrorException;


    @Value
    class StreamMetadata {
        @NonNull
        Optional<EtlRetention> retentionOpt;
    }

    @Value
    @Builder(toBuilder = true)
    @AllArgsConstructor
    class Account {
        @NonNull
        String accountId;

        @NonNull
        String email;

        @NonNull
        ImmutableSet<String> enabledStreamNames;
    }

    @Getter
    @AllArgsConstructor
    enum EtlRetention {
        DAY(1),
        WEEK(7),
        THREE_MONTHS(3 * 30),
        YEAR(366),
        THREE_YEARS(3 * 366);
        public static final EtlRetention DEFAULT = THREE_MONTHS;
        int expirationInDays;
    }
}
