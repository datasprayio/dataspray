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

package io.dataspray.store.impl;

import com.google.common.collect.ImmutableSet;
import com.google.gson.Gson;
import io.dataspray.store.ApiAccessStore;
import io.dataspray.store.OrganizationStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminAddUserToGroupRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminListGroupsForUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminRemoveUserFromGroupRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.CreateGroupRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.GetGroupRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.GroupType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UpdateGroupRequest;

/**
 * Organization management backed by Cognito groups.
 */
@Slf4j
@ApplicationScoped
public class CognitoGroupOrganizationStore implements OrganizationStore {

    @ConfigProperty(name = CognitoUserStore.USER_POOL_ID_PROP_NAME)
    String userPoolId;

    @Inject
    Gson gson;
    @Inject
    CognitoIdentityProviderClient cognitoClient;
    @Inject
    ApiAccessStore apiAccessStore;

    @Override
    public GroupType createOrganization(String organizationName, String authorUsername) {

        // Create group
        OrganizationMetadata organizationMetadata = new OrganizationMetadata(authorUsername, null);
        GroupType group = cognitoClient.createGroup(CreateGroupRequest.builder()
                .userPoolId(userPoolId)
                .groupName(organizationName)
                .description(gson.toJson(organizationMetadata))
                .build()).group();

        // Create Api Gateway Usage Key for this organization
        apiAccessStore.getOrCreateUsageKeyApiKeyForOrganization(organizationName, organizationMetadata.getUsageKeyType());

        // Add author to group
        addUserToOrganization(group.groupName(), authorUsername);

        return group;
    }

    @Override
    public ImmutableSet<Organization> getOrganizationsForUser(String username) {
        return cognitoClient.adminListGroupsForUserPaginator(AdminListGroupsForUserRequest.builder()
                        .userPoolId(userPoolId)
                        .username(username).build())
                .groups()
                .stream()
                .map(group -> new Organization(group.groupName()))
                .collect(ImmutableSet.toImmutableSet());
    }

    @Override
    public OrganizationMetadata getMetadata(String organizationName) {
        String description = cognitoClient.getGroup(GetGroupRequest.builder()
                        .userPoolId(userPoolId)
                        .groupName(organizationName)
                        .build())
                .group()
                .description();
        return gson.fromJson(description, OrganizationMetadata.class);
    }


    @Override
    public void setMetadata(String organizationName, OrganizationMetadata metadata) {
        cognitoClient.updateGroup(UpdateGroupRequest.builder()
                .userPoolId(userPoolId)
                .groupName(organizationName)
                .description(gson.toJson(metadata))
                .build());
    }

    @Override
    public void addUserToOrganization(String organizationName, String username) {
        cognitoClient.adminAddUserToGroup(AdminAddUserToGroupRequest.builder()
                .userPoolId(userPoolId)
                .groupName(organizationName)
                .username(username)
                .build());
    }

    @Override
    public void removeUserFromOrganization(String organizationName, String username) {
        cognitoClient.adminRemoveUserFromGroup(AdminRemoveUserFromGroupRequest.builder()
                .userPoolId(userPoolId)
                .groupName(organizationName)
                .username(username)
                .build());
    }
}
