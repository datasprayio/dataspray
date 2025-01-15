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

package io.dataspray.stream.control;

import com.google.common.base.Strings;
import io.dataspray.store.ApiAccessStore;
import io.dataspray.store.ApiAccessStore.UsageKeyType;
import io.dataspray.store.OrganizationStore;
import io.dataspray.store.UserStore;
import io.dataspray.web.resource.AbstractResource;
import jakarta.annotation.Nullable;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.ClientErrorException;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.cognitoidentityprovider.model.GroupExistsException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserNotFoundException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UsernameExistsException;

@Slf4j
@ApplicationScoped
public class OrganizationResource extends AbstractResource implements OrganizationApi {

    @Inject
    OrganizationStore organizationStore;
    @Inject
    UserStore userStore;
    @Inject
    ApiAccessStore apiAccessStore;

    @Override
    public void createOrganization(String organizationName) {
        // Validate your username
        String username = getUsername().orElseThrow(() -> new ClientErrorException(403));

        // Create organization
        try {
            organizationStore.createOrganization(organizationName, username);
        } catch (GroupExistsException ex) {
            throw new ClientErrorException(409, ex);
        }
    }

    @Override
    public void inviteToOrganization(String organizationName, String email, String username) {

        if (Strings.isNullOrEmpty(username)) {
            // Check that the user exists given the email
            try {
                username = userStore.getUser(email).username();
            } catch (UserNotFoundException ex) {
                // User doesn't exist, create instead by supplying a username
                throw new ClientErrorException(404, ex);
            }
        } else {
            // Create user given email and username
            try {
                username = userStore.createUser(username, email).user().username();
            } catch (UsernameExistsException ex) {
                // Username is already taken
                throw new ClientErrorException(409, ex);
            }
        }

        // Finally add the user to the organization
        organizationStore.addUserToOrganization(organizationName, username);
    }

    @Override
    public void rateLimitAdjust(String organizationName, String level) {
        @Nullable UsageKeyType usageKeyType = switch (level) {
            case "DEFAULT" -> UsageKeyType.ORGANIZATION;
            case "ONE" -> UsageKeyType.ORGANIZATION_ONE_RPS;
            case "TEN" -> UsageKeyType.ORGANIZATION_TEN_RPS;
            case "HUNDRED" -> UsageKeyType.ORGANIZATION_HUNDRED_RPS;
            default -> throw new ClientErrorException(400);
        };
        log.info("Organization {} switching to usage key type {}", organizationName, usageKeyType);
        organizationStore.setMetadata(organizationName, organizationStore.getMetadata(organizationName)
                .toBuilder()
                .usageKeyType(usageKeyType)
                .build());
        apiAccessStore.switchUsageKeyType(organizationName, usageKeyType);
    }
}
