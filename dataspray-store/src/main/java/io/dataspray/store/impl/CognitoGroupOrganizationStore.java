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

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.gson.Gson;
import io.dataspray.common.DeployEnvironment;
import io.dataspray.common.StringUtil;
import io.dataspray.store.ApiAccessStore;
import io.dataspray.store.OrganizationStore;
import io.dataspray.store.TopicStore;
import io.dataspray.store.util.IamUtil;
import io.dataspray.store.util.WaiterUtil;
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
import software.amazon.awssdk.services.iam.IamClient;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static io.dataspray.common.DeployEnvironment.DEPLOY_ENVIRONMENT_PROP_NAME;
import static io.dataspray.store.impl.FirehoseS3AthenaBatchStore.*;
import static io.dataspray.store.impl.LambdaDeployerImpl.*;

/**
 * Organization management backed by Cognito groups.
 */
@Slf4j
@ApplicationScoped
public class CognitoGroupOrganizationStore implements OrganizationStore {

    @ConfigProperty(name = CognitoUserStore.USER_POOL_ID_PROP_NAME)
    String userPoolId;

    @ConfigProperty(name = "aws.accountId")
    String awsAccountId;
    @ConfigProperty(name = "aws.region")
    String awsRegion;
    @ConfigProperty(name = CUSTOMER_FUNCTION_PERMISSION_BOUNDARY_NAME_PROP_NAME, defaultValue = "customer-permission-boundary")
    String customerFunctionPermissionBoundaryName;
    @ConfigProperty(name = DEPLOY_ENVIRONMENT_PROP_NAME)
    DeployEnvironment deployEnv;
    @ConfigProperty(name = ETL_BUCKET_PROP_NAME)
    String etlBucketName;
    @Inject
    Gson gson;
    @Inject
    CognitoIdentityProviderClient cognitoClient;
    @Inject
    ApiAccessStore apiAccessStore;
    @Inject
    IamClient iamClient;
    @Inject
    WaiterUtil waiterUtil;
    @Inject
    IamUtil iamUtil;

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
        String description = getGroup(organizationName)
                .description();
        return gson.fromJson(description, OrganizationMetadata.class);
    }

    private GroupType getGroup(String organizationName) {
        return cognitoClient.getGroup(GetGroupRequest.builder()
                        .userPoolId(userPoolId)
                        .groupName(organizationName)
                        .build())
                .group();
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

    @Override
    public void addGlueDatabaseToOrganization(String organizationName, String databaseName) {
        String groupRoleArn = getOrCreateGroupRoleArn(organizationName);
        String policyName = CUSTOMER_FUNCTION_POLICY_PATH_PREFIX + "GroupGlue" + StringUtil.camelCase(databaseName, true);
        iamUtil.ensurePolicyAttachedToRole(groupRoleArn, policyName, gson.toJson(Map.of(
                "Version", "2012-10-17",
                "Statement", List.of(Map.of(
                                "Effect", "Allow",
                                "Action", List.of(
                                        "glue:GetDatabase",
                                        "glue:GetTable",
                                        "glue:GetTables",
                                        "glue:GetPartition",
                                        "glue:GetPartitions"
                                ),
                                "Resource", List.of(
                                        "arn:aws:glue:" + awsRegion + ":" + awsAccountId + ":database/" + databaseName,
                                        "arn:aws:glue:" + awsRegion + ":" + awsAccountId + ":table/" + databaseName + "/*"
                                )
                        ), Map.of(
                                "Effect", "Allow",
                                "Action", List.of(
                                        "s3:GetObject",
                                        "s3:ListBucket"
                                ),
                                "Resource", ImmutableList.<String>builder()
                                        .add("arn:aws:s3:::" + etlBucketName)
                                        .addAll(
                                                Arrays.stream(TopicStore.BatchRetention.values())
                                                        .map(retention ->
                                                                "arn:aws:s3:::" + etlBucketName + "/" +
                                                                ETL_BUCKET_ORGANIZATION_PREFIX
                                                                        .replace("!{partitionKeyFromQuery:" + ETL_PARTITION_KEY_RETENTION + "}", retention.name())
                                                                        .replace("!{partitionKeyFromQuery:" + ETL_PARTITION_KEY_ORGANIZATION + "}", organizationName)
                                                                + "/*")
                                                        .collect(Collectors.toSet()))
                                        .build()
                        ), Map.of(
                                "Effect", "Allow",
                                "Action", List.of(
                                        "athena:StartQueryExecution",
                                        "athena:GetQueryExecution",
                                        "athena:GetQueryResults"
                                ),
                                "Resource", "*"
                        ), Map.of(
                                "Effect", "Allow",
                                "Action", List.of(
                                        "s3:GetObject",
                                        "s3:PutObject"
                                ),
                                "Resource",
                                "arn:aws:s3:::"
                                + ETL_BUCKET_ATHENA_RESULTS_PREFIX
                                        .replace("!{partitionKeyFromQuery:" + ETL_PARTITION_KEY_ORGANIZATION + "}", organizationName)
                                + "/*"

                        )
                )
        )));
    }

    @Override
    public void addDynamoToOrganization(String organizationName, String tableName) {
        String groupRoleArn = getOrCreateGroupRoleArn(organizationName);
        String policyName = CUSTOMER_FUNCTION_POLICY_PATH_PREFIX + "GroupDynamo" + StringUtil.camelCase(tableName, true);
        iamUtil.ensurePolicyAttachedToRole(groupRoleArn, policyName, gson.toJson(Map.of(
                "Version", "2012-10-17",
                "Statement", List.of(Map.of(
                        "Effect", "Allow",
                        "Action", List.of(
                                // NOTE: this is what a customer user can do, what a customer lambda can do,
                                // take a look at LambdaDeployerImpl under CUSTOMER_FUNCTION_PERMISSION_CUSTOMER_LAMBDA_DYNAMO
                                // NOTE: Changing this does not change the permissions of existing roles,
                                // you must bump the name and create a new role
                                "dynamodb:GetItem",
                                "dynamodb:BatchGetItem",
                                "dynamodb:Query",
                                "dynamodb:Scan", // User has scan ability, lambda doesn't
                                "dynamodb:PutItem",
                                "dynamodb:UpdateItem",
                                "dynamodb:BatchWriteItem",
                                "dynamodb:DeleteItem"),
                        "Resource", Map.of(
                                "arn:aws:dynamodb:" + awsRegion + ":" + awsAccountId + ":table/" + tableName,
                                "arn:aws:dynamodb:" + awsRegion + ":" + awsAccountId + ":table/" + tableName + "/index/*"
                        ))))));
    }

    private String getOrCreateGroupRoleArn(String organizationName) {
        Optional<String> roleArnOpt = Optional.ofNullable(Strings.emptyToNull(getGroup(organizationName)
                .roleArn()));

        if (roleArnOpt.isPresent()) {
            return roleArnOpt.get();
        }

        String groupRoleName = CUSTOMER_FUN_DYNAMO_OR_ROLE_NAME_PREFIX_GETTER.apply(deployEnv) + organizationName + "-" + "-group-role";
        String groupRoleArn = iamUtil.getOrCreateRole(groupRoleName, customerFunctionPermissionBoundaryName, "Auto-created for group role for org " + organizationName)
                .arn();

        // Attach role to Cognito group
        cognitoClient.updateGroup(UpdateGroupRequest.builder()
                .userPoolId(userPoolId)
                .groupName(organizationName)
                .roleArn(groupRoleArn)
                .build());

        return groupRoleArn;
    }
}
