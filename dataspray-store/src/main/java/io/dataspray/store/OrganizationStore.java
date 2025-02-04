/*
 * Copyright 2024 Matus Faro
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
import io.dataspray.store.ApiAccessStore.UsageKeyType;
import jakarta.annotation.Nullable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NonNull;
import lombok.Value;
import software.amazon.awssdk.services.cognitoidentityprovider.model.GroupType;

import java.util.Optional;

public interface OrganizationStore {

    GroupType createOrganization(String organizationName, String authorUsername);

    ImmutableSet<Organization> getOrganizationsForUser(String username);

    OrganizationMetadata getMetadata(String organizationName);

    void setMetadata(String organizationName, OrganizationMetadata metadata);

    void addUserToOrganization(String organizationName, String username);

    void removeUserFromOrganization(String organizationName, String username);

    void addGlueDatabaseToOrganization(String organizationName, String databaseName);

    void addDynamoToOrganization(String organizationName, String dynamoTableName);

    @Value
    @Builder(toBuilder = true)
    @AllArgsConstructor
    class Organization {
        @NonNull
        String name;
    }

    @Value
    @Builder(toBuilder = true)
    @AllArgsConstructor
    class OrganizationMetadata {

        @NonNull
        String authorUsername;

        /**
         * Type of usage key to use for API Access.
         * <p>
         * Defaults to {@link UsageKeyType#ORGANIZATION}
         * <p>
         * Note that Cognito access always defaults to {@link UsageKeyType#ORGANIZATION} regardless of this setting.
         */
        @Nullable
        UsageKeyType usageKeyType;

        public UsageKeyType getUsageKeyType() {
            return Optional.ofNullable(usageKeyType)
                    .orElse(ApiAccessStore.DEFAULT_ORGANIZATION_USAGE_KEY_TYPE);
        }
    }
}
