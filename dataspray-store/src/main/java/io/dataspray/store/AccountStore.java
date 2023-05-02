package io.dataspray.store;


import io.dataspray.singletable.DynamoTable;
import jakarta.ws.rs.ClientErrorException;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.Value;

import java.util.Optional;
import java.util.Set;

import static io.dataspray.singletable.TableType.Gsi;
import static io.dataspray.singletable.TableType.Primary;

public interface AccountStore {

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

    @Value
    @Builder(toBuilder = true)
    @AllArgsConstructor
    @DynamoTable(type = Primary, partitionKeys = "accountId", rangePrefix = "account")
    @DynamoTable(type = Gsi, indexNumber = 1, partitionKeys = {"apiKey"}, rangePrefix = "accountByApiKey")
    @DynamoTable(type = Gsi, indexNumber = 2, partitionKeys = {"oauthGuid"}, rangePrefix = "accountByOauthGuid")
    class Account {
        @NonNull
        String accountId;

        @NonNull
        String email;

        @NonNull
        Set<String> enabledStreamNames;
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
