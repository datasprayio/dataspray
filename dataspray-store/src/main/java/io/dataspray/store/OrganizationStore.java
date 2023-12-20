/*
 * Copyright 2023 Matus Faro
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package io.dataspray.store;


import com.google.common.collect.ImmutableSet;
import jakarta.ws.rs.ClientErrorException;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.Value;

import java.util.List;
import java.util.Optional;

public interface OrganizationStore {

    void addUser(String email, String organizationName);

    void removeUser(String email, String organizationName);

    /**
     * Create an organization for a user if they're not part of any organization yet.
     */
    List<Organization> getOrCreateOrganizationForUser(String email);

    Optional<Organization> getOrganization(String organizationName);

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
    class Organization {
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
