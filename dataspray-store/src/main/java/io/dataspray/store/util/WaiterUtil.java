package io.dataspray.store.util;

import com.google.common.collect.ImmutableSet;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.internal.waiters.DefaultWaiter;
import software.amazon.awssdk.core.internal.waiters.ResponseOrException;
import software.amazon.awssdk.core.waiters.Waiter;
import software.amazon.awssdk.core.waiters.WaiterAcceptor;
import software.amazon.awssdk.core.waiters.WaiterResponse;
import software.amazon.awssdk.core.waiters.WaiterState;
import software.amazon.awssdk.services.iam.IamClient;
import software.amazon.awssdk.services.iam.model.GetRolePolicyRequest;
import software.amazon.awssdk.services.iam.model.GetRolePolicyResponse;
import software.amazon.awssdk.services.iam.waiters.internal.WaitersRuntime;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.GetEventSourceMappingRequest;
import software.amazon.awssdk.services.lambda.model.GetEventSourceMappingResponse;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.Objects;

@ApplicationScoped
public class WaiterUtil {

    public static final ImmutableSet<String> EVENT_SOURCE_MAPPING_STATES_RETRY = ImmutableSet.of(
            "Creating",
            "Enabling",
            "Disabling",
            "Updating");
    public static final ImmutableSet<String> EVENT_SOURCE_MAPPING_STATES_SUCCESS = ImmutableSet.of(
            "Enabled",
            "Disabled");
    public static final ImmutableSet<String> EVENT_SOURCE_MAPPING_STATES_DELETING = ImmutableSet.of(
            "Deleting");

    @Inject
    LambdaClient lambdaClient;
    @Inject
    IamClient iamClient;

    public WaiterResponse<GetEventSourceMappingResponse> waitUntilEventSourceMappingEnabled(String uuid) {
        return DefaultWaiter.<GetEventSourceMappingResponse>builder()
                .addAcceptor(WaiterAcceptor.successOnResponseAcceptor(response -> "Enabled".equals(response.state())))
                .addAcceptor(WaiterAcceptor.errorOnResponseAcceptor(response -> "Disabled".equals(response.state())))
                .addAcceptor(WaiterAcceptor.successOnResponseAcceptor(response -> EVENT_SOURCE_MAPPING_STATES_DELETING.contains(response.state())))
                .addAcceptor(WaiterAcceptor.retryOnResponseAcceptor(response -> EVENT_SOURCE_MAPPING_STATES_RETRY.contains(response.state())))
                .build()
                .run(() -> lambdaClient.getEventSourceMapping(GetEventSourceMappingRequest.builder()
                        .uuid(uuid).build()));
    }

    public WaiterResponse<GetEventSourceMappingResponse> waitUntilEventSourceMappingDisabled(String uuid) {
        return DefaultWaiter.<GetEventSourceMappingResponse>builder()
                .addAcceptor(WaiterAcceptor.successOnResponseAcceptor(response -> "Disabled".equals(response.state())))
                .addAcceptor(WaiterAcceptor.errorOnResponseAcceptor(response -> "Enabled".equals(response.state())))
                .addAcceptor(WaiterAcceptor.successOnResponseAcceptor(response -> EVENT_SOURCE_MAPPING_STATES_DELETING.contains(response.state())))
                .addAcceptor(WaiterAcceptor.retryOnResponseAcceptor(response -> EVENT_SOURCE_MAPPING_STATES_RETRY.contains(response.state())))
                .build()
                .run(() -> lambdaClient.getEventSourceMapping(GetEventSourceMappingRequest.builder()
                        .uuid(uuid).build()));
    }

    public WaiterResponse<GetEventSourceMappingResponse> waitUntilEventSourceMappingUpdated(GetEventSourceMappingRequest request) {
        return DefaultWaiter.<GetEventSourceMappingResponse>builder()
                .addAcceptor(WaiterAcceptor.successOnResponseAcceptor(response -> EVENT_SOURCE_MAPPING_STATES_SUCCESS.contains(response.state())))
                .addAcceptor(WaiterAcceptor.successOnResponseAcceptor(response -> EVENT_SOURCE_MAPPING_STATES_DELETING.contains(response.state())))
                .addAcceptor(WaiterAcceptor.retryOnResponseAcceptor(response -> EVENT_SOURCE_MAPPING_STATES_RETRY.contains(response.state())))
                .build()
                .run(() -> lambdaClient.getEventSourceMapping(request));
    }

    public WaiterResponse<GetRolePolicyResponse> waitUntilPolicyAttachedToRole(String roleName, String policyName) {
        Waiter.Builder<GetRolePolicyResponse> builder = DefaultWaiter.<GetRolePolicyResponse>builder()
                .addAcceptor(new WaitersRuntime.ResponseStatusAcceptor(200, WaiterState.SUCCESS))
                .addAcceptor(WaiterAcceptor.retryOnExceptionAcceptor(error -> Objects.equals(errorCode(error), "NoSuchEntity")));
        WaitersRuntime.DEFAULT_ACCEPTORS.forEach(builder::addAcceptor);
        return builder.build().run(() -> iamClient.getRolePolicy(GetRolePolicyRequest.builder()
                .roleName(roleName)
                .policyName(policyName)
                .build()));
    }

    public <R> R resolve(WaiterResponse<R> waiterResponse) {
        ResponseOrException<R> responseOrException = waiterResponse.matched();
        try {
            return responseOrException.response()
                    .orElseThrow(() -> responseOrException
                            .exception()
                            .orElseThrow());
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Throwable th) {
            throw new RuntimeException(th);
        }
    }

    private String errorCode(Throwable error) {
        if (error instanceof AwsServiceException) {
            return ((AwsServiceException) error).awsErrorDetails().errorCode();
        }
        return null;
    }
}
