package io.dataspray.common.aws.test;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import io.quarkus.arc.Priority;
import io.quarkus.arc.properties.IfBuildProperty;
import io.quarkus.test.junit.QuarkusTestProfile;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.mockito.Mockito;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.http.SdkHttpResponse;
import software.amazon.awssdk.services.iam.IamClient;
import software.amazon.awssdk.services.iam.model.AttachRolePolicyRequest;
import software.amazon.awssdk.services.iam.model.AttachRolePolicyResponse;
import software.amazon.awssdk.services.iam.model.CreatePolicyRequest;
import software.amazon.awssdk.services.iam.model.CreatePolicyResponse;
import software.amazon.awssdk.services.iam.model.CreateRoleRequest;
import software.amazon.awssdk.services.iam.model.CreateRoleResponse;
import software.amazon.awssdk.services.iam.model.EntityAlreadyExistsException;
import software.amazon.awssdk.services.iam.model.GetPolicyRequest;
import software.amazon.awssdk.services.iam.model.GetPolicyResponse;
import software.amazon.awssdk.services.iam.model.GetRolePolicyRequest;
import software.amazon.awssdk.services.iam.model.GetRolePolicyResponse;
import software.amazon.awssdk.services.iam.model.GetRoleRequest;
import software.amazon.awssdk.services.iam.model.GetRoleResponse;
import software.amazon.awssdk.services.iam.model.NoSuchEntityException;
import software.amazon.awssdk.services.iam.model.Policy;
import software.amazon.awssdk.services.iam.model.PutRolePolicyRequest;
import software.amazon.awssdk.services.iam.model.PutRolePolicyResponse;
import software.amazon.awssdk.services.iam.waiters.IamWaiter;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Alternative;
import javax.inject.Singleton;
import java.util.Map;
import java.util.Set;

import static org.mockito.Mockito.when;

@ApplicationScoped
public class MockIamClient {

    public static final SdkHttpResponse SDK_200 = SdkHttpResponse.builder().statusCode(200).build();

    @ConfigProperty(name = "aws.accountId")
    String awsAccountId;

    @Alternative
    @Priority(1)
    @Singleton
    @IfBuildProperty(name = "aws.iam.mock.enable", stringValue = "true")
    public IamClient getIamClient() {
        IamClient mock = Mockito.mock(IamClient.class);

        // Managed
        Map<String, Set<String>> roleNameToAttachedPolicyNames = Maps.newConcurrentMap();
        Set<String> policyNames = Sets.newConcurrentHashSet();
        // Inline
        Map<String, Set<String>> roleNameToAttachedInlinePolicyNames = Maps.newConcurrentMap();

        when(mock.createRole(Mockito.<CreateRoleRequest>any()))
                .thenAnswer(invocation -> {
                    CreateRoleRequest request = invocation.getArgument(0, CreateRoleRequest.class);
                    if (roleNameToAttachedPolicyNames.putIfAbsent(request.roleName(), Sets.newConcurrentHashSet()) != null) {
                        throw alreadyExistsEntity();
                    }
                    if (roleNameToAttachedInlinePolicyNames.putIfAbsent(request.roleName(), Sets.newConcurrentHashSet()) != null) {
                        throw alreadyExistsEntity();
                    }
                    return CreateRoleResponse.builder()
                            .sdkHttpResponse(SDK_200).build();
                });

        when(mock.getRole(Mockito.<GetRoleRequest>any()))
                .thenAnswer(invocation -> {
                    GetRoleRequest request = invocation.getArgument(0, GetRoleRequest.class);
                    if (roleNameToAttachedPolicyNames.get(request.roleName()) == null) {
                        throw noSuchEntity();
                    }
                    return GetRoleResponse.builder()
                            .sdkHttpResponse(SDK_200).build();
                });

        when(mock.getRolePolicy(Mockito.<GetRolePolicyRequest>any()))
                .thenAnswer(invocation -> {
                    GetRolePolicyRequest request = invocation.getArgument(0, GetRolePolicyRequest.class);
                    Set<String> roleInlinePolicyNames = roleNameToAttachedInlinePolicyNames.get(request.roleName());
                    if (roleInlinePolicyNames == null
                            || !roleInlinePolicyNames.contains(request.policyName())) {
                        throw noSuchEntity();
                    }
                    return GetRolePolicyResponse.builder()
                            .sdkHttpResponse(SDK_200).build();
                });

        when(mock.putRolePolicy(Mockito.<PutRolePolicyRequest>any()))
                .thenAnswer(invocation -> {
                    PutRolePolicyRequest request = invocation.getArgument(0, PutRolePolicyRequest.class);
                    Set<String> roleInlinePolicyNames = roleNameToAttachedInlinePolicyNames.get(request.roleName());
                    if (roleInlinePolicyNames == null) {
                        throw noSuchEntity();
                    }
                    if (!roleInlinePolicyNames.add(request.policyName())) {
                        throw alreadyExistsEntity();
                    }
                    return PutRolePolicyResponse.builder()
                            .sdkHttpResponse(SDK_200).build();
                });

        when(mock.getPolicy(Mockito.<GetPolicyRequest>any()))
                .thenAnswer(invocation -> {
                    GetPolicyRequest request = invocation.getArgument(0, GetPolicyRequest.class);
                    if (!policyNames.contains(getPolicyNameFromArn(request.policyArn()))) {
                        throw noSuchEntity();
                    }
                    return GetPolicyResponse.builder()
                            .policy(Policy.builder()
                                    .arn(request.policyArn()).build())
                            .sdkHttpResponse(SDK_200).build();
                });

        when(mock.createPolicy(Mockito.<CreatePolicyRequest>any()))
                .thenAnswer(invocation -> {
                    CreatePolicyRequest request = invocation.getArgument(0, CreatePolicyRequest.class);
                    if (!policyNames.add(request.policyName())) {
                        throw alreadyExistsEntity();
                    }
                    return CreatePolicyResponse.builder()
                            .policy(Policy.builder()
                                    .arn(getArnFromPolicyName(request.policyName())).build())
                            .sdkHttpResponse(SDK_200).build();
                });

        when(mock.attachRolePolicy(Mockito.<AttachRolePolicyRequest>any()))
                .thenAnswer(invocation -> {
                    AttachRolePolicyRequest request = invocation.getArgument(0, AttachRolePolicyRequest.class);
                    Set<String> rolePolicyNames = roleNameToAttachedPolicyNames.get(request.roleName());
                    if (rolePolicyNames == null) {
                        throw noSuchEntity();
                    }
                    if (!rolePolicyNames.add(getPolicyNameFromArn(request.policyArn()))) {
                        throw alreadyExistsEntity();
                    }
                    return AttachRolePolicyResponse.builder()
                            .sdkHttpResponse(SDK_200).build();
                });

        when(mock.waiter())
                .thenReturn(IamWaiter.builder().client(mock).build());

        return mock;
    }

    private String getPolicyNameFromArn(String policyArn) {
        return policyArn.substring(getArnPolicyPrefix().length());
    }

    private String getArnFromPolicyName(String policyName) {
        return getArnPolicyPrefix() + policyName;
    }

    private String getArnPolicyPrefix() {
        return "arn:aws:iam::" + awsAccountId + ":policy/";
    }

    private NoSuchEntityException noSuchEntity() {
        throw NoSuchEntityException.builder()
                .awsErrorDetails(AwsErrorDetails.builder()
                        .errorCode("NoSuchEntity").build()).build();
    }

    private EntityAlreadyExistsException alreadyExistsEntity() {
        throw EntityAlreadyExistsException.builder()
                .awsErrorDetails(AwsErrorDetails.builder()
                        .errorCode("EntityAlreadyExists").build()).build();
    }

    public static class TestProfile implements QuarkusTestProfile {

        public Map<String, String> getConfigOverrides() {
            return ImmutableMap.of("aws.iam.mock.enable", "true");
        }
    }
}
