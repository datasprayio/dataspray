package io.dataspray.store.util;

import com.google.common.collect.ImmutableSet;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.internal.waiters.DefaultWaiter;
import software.amazon.awssdk.core.internal.waiters.ResponseOrException;
import software.amazon.awssdk.core.waiters.Waiter;
import software.amazon.awssdk.core.waiters.WaiterAcceptor;
import software.amazon.awssdk.core.waiters.WaiterOverrideConfiguration;
import software.amazon.awssdk.core.waiters.WaiterResponse;
import software.amazon.awssdk.core.waiters.WaiterState;
import software.amazon.awssdk.services.iam.IamClient;
import software.amazon.awssdk.services.iam.model.GetRolePolicyRequest;
import software.amazon.awssdk.services.iam.model.GetRolePolicyResponse;
import software.amazon.awssdk.services.iam.waiters.internal.WaitersRuntime;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.GetEventSourceMappingRequest;
import software.amazon.awssdk.services.lambda.model.GetEventSourceMappingResponse;

import java.time.Duration;
import java.util.Objects;

@Slf4j
@ApplicationScoped
public class WaiterUtil {

    public static final String EVENT_SOURCE_MAPPING_STATE_ENABLED = "Enabled";
    public static final String EVENT_SOURCE_MAPPING_STATE_DISABLED = "Disabled";
    public static final ImmutableSet<String> EVENT_SOURCE_MAPPING_STATES_RETRY = ImmutableSet.of(
            "Creating",
            "Enabling",
            "Disabling",
            "Updating",
            "Deleting");
    public static final ImmutableSet<String> EVENT_SOURCE_MAPPING_STATES_SUCCESS = ImmutableSet.of(
            EVENT_SOURCE_MAPPING_STATE_ENABLED,
            EVENT_SOURCE_MAPPING_STATE_DISABLED);

    @Inject
    LambdaClient lambdaClient;
    @Inject
    IamClient iamClient;

    /** Note this operation may take longer than API Gateway 30sec timeout */
    public WaiterResponse<GetEventSourceMappingResponse> waitUntilEventSourceMappingEnabled(String uuid) {
        return DefaultWaiter.<GetEventSourceMappingResponse>builder()
                .addAcceptor(WaiterAcceptor.successOnResponseAcceptor(response -> {
                    log.debug("Waiting for source mapping to be enabled, received: {} {}", response.state(), response);
                    return EVENT_SOURCE_MAPPING_STATE_ENABLED.equals(response.state());
                }))
                .addAcceptor(WaiterAcceptor.retryOnResponseAcceptor(response -> EVENT_SOURCE_MAPPING_STATES_RETRY.contains(response.state())))
                .addAcceptor(WaiterAcceptor.errorOnResponseAcceptor(response -> EVENT_SOURCE_MAPPING_STATE_DISABLED.equals(response.state())))
                .addAcceptor(WaiterAcceptor.errorOnExceptionAcceptor(error -> Objects.equals(errorCode(error), "NoSuchEntity")))
                .overrideConfiguration(eventSourceMappingWaiterConfiguration())
                .build()
                .run(() -> lambdaClient.getEventSourceMapping(GetEventSourceMappingRequest.builder()
                        .uuid(uuid).build()));
    }

    /** Note this operation may take longer than API Gateway 30sec timeout */
    public WaiterResponse<GetEventSourceMappingResponse> waitUntilEventSourceMappingDisabled(String uuid) {
        return DefaultWaiter.<GetEventSourceMappingResponse>builder()
                .addAcceptor(WaiterAcceptor.successOnResponseAcceptor(response -> {
                    log.debug("Waiting for source mapping to be disabled, received: {} {}", response.state(), response);
                    return EVENT_SOURCE_MAPPING_STATE_DISABLED.equals(response.state());
                }))
                .addAcceptor(WaiterAcceptor.successOnExceptionAcceptor(error -> Objects.equals(errorCode(error), "NoSuchEntity")))
                .addAcceptor(WaiterAcceptor.retryOnResponseAcceptor(response -> EVENT_SOURCE_MAPPING_STATES_RETRY.contains(response.state())))
                .addAcceptor(WaiterAcceptor.errorOnResponseAcceptor(response -> EVENT_SOURCE_MAPPING_STATE_ENABLED.equals(response.state())))
                .overrideConfiguration(eventSourceMappingWaiterConfiguration())
                .build()
                .run(() -> lambdaClient.getEventSourceMapping(GetEventSourceMappingRequest.builder()
                        .uuid(uuid).build()));
    }

    /** Note this operation may take longer than API Gateway 30sec timeout */
    public WaiterResponse<GetEventSourceMappingResponse> waitUntilEventSourceMappingUpdated(String uuid) {
        return DefaultWaiter.<GetEventSourceMappingResponse>builder()
                .addAcceptor(WaiterAcceptor.successOnResponseAcceptor(response -> EVENT_SOURCE_MAPPING_STATES_SUCCESS.contains(response.state())))
                .addAcceptor(WaiterAcceptor.successOnExceptionAcceptor(error -> Objects.equals(errorCode(error), "NoSuchEntity")))
                .addAcceptor(WaiterAcceptor.retryOnResponseAcceptor(response -> EVENT_SOURCE_MAPPING_STATES_RETRY.contains(response.state())))
                .overrideConfiguration(eventSourceMappingWaiterConfiguration())
                .build()
                .run(() -> lambdaClient.getEventSourceMapping(GetEventSourceMappingRequest.builder()
                        .uuid(uuid).build()));
    }

    private WaiterOverrideConfiguration eventSourceMappingWaiterConfiguration() {
        return WaiterOverrideConfiguration.builder()
                // Takes a bit of extra time for event source mapping to enable/disable
                .waitTimeout(Duration.ofSeconds(60))
                .maxAttempts(10).build();
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
