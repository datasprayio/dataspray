package io.dataspray.store;

import io.dataspray.singletable.DynamoTable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NonNull;
import lombok.ToString;
import lombok.Value;

import java.time.Instant;

import static io.dataspray.singletable.TableType.Gsi;
import static io.dataspray.singletable.TableType.Primary;

public interface StreamStore {

    @Value
    @AllArgsConstructor
    @Builder(toBuilder = true)
    @DynamoTable(type = Primary, partitionKeys = "accountId", rangePrefix = "account")
    @DynamoTable(type = Gsi, indexNumber = 1, partitionKeys = {"email"}, rangePrefix = "accountByEmail")
    class Stream {
        @NonNull
        String accountId;

        @NonNull
        String email;

        @NonNull
        String planid;

        @NonNull
        Instant created;

        @ToString.Exclude
        @NonNull
        String password;
    }
}
