/*
 * Copyright 2025 Matus Faro
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

package io.dataspray.store.util;

import com.google.gson.Gson;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import software.amazon.awssdk.services.iam.IamClient;
import software.amazon.awssdk.services.iam.model.CreateRoleRequest;
import software.amazon.awssdk.services.iam.model.GetRolePolicyRequest;
import software.amazon.awssdk.services.iam.model.GetRoleRequest;
import software.amazon.awssdk.services.iam.model.NoSuchEntityException;
import software.amazon.awssdk.services.iam.model.PutRolePolicyRequest;
import software.amazon.awssdk.services.iam.model.Role;

import java.util.List;
import java.util.Map;

@Slf4j
@ApplicationScoped
public class IamUtil {

    @ConfigProperty(name = "aws.accountId")
    String awsAccountId;
    @ConfigProperty(name = "aws.region")
    String awsRegion;
    @Inject
    Gson gson;
    @Inject
    IamClient iamClient;
    @Inject
    WaiterUtil waiterUtil;

    public Role getOrCreateRole(String roleName, String permissionBoundaryName, String description) {
        // Setup function IAM role
        String roleArn = "arn:aws:iam::" + awsAccountId + ":role/" + roleName;
        try {
            Role role = iamClient.getRole(GetRoleRequest.builder()
                            .roleName(roleName)
                            .build())
                    .role();
            log.debug("Found role {}", roleName);
            return role;
        } catch (NoSuchEntityException ex2) {
            iamClient.createRole(CreateRoleRequest.builder()
                    .roleName(roleName)
                    .description(description)
                    .permissionsBoundary("arn:aws:iam::" + awsAccountId + ":policy/" + permissionBoundaryName)
                    .assumeRolePolicyDocument(gson.toJson(Map.of(
                            "Version", "2012-10-17",
                            "Statement", List.of(Map.of(
                                    "Effect", "Allow",
                                    "Action", List.of("sts:AssumeRole"),
                                    "Principal", Map.of(
                                            "Service", List.of("lambda.amazonaws.com")))))))
                    .build());
            log.info("Created role {}", roleName);
            return waiterUtil.resolve(iamClient.waiter().waitUntilRoleExists(GetRoleRequest.builder()
                            .roleName(roleName)
                            .build()))
                    .role();
        }

    }

    public void ensurePolicyAttachedToRole(String roleName, String policyName, String policyDocument) {
        try {
            // First see if policy is attached to the role already
            iamClient.getRolePolicy(GetRolePolicyRequest.builder()
                    .roleName(roleName)
                    .policyName(policyName)
                    .build());
            log.debug("Found role {} policy {}", roleName, policyName);
        } catch (NoSuchEntityException ex) {
            iamClient.putRolePolicy(PutRolePolicyRequest.builder()
                    .roleName(roleName)
                    .policyName(policyName)
                    .policyDocument(policyDocument)
                    .build());
            log.info("Created role {} policy {}", roleName, policyName);
            waiterUtil.resolve(waiterUtil.waitUntilPolicyAttachedToRole(roleName, policyName));
        }
    }

}
