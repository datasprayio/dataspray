package io.dataspray.store.util;

import com.google.common.collect.ImmutableSet;
import software.amazon.awssdk.core.internal.waiters.DefaultWaiter;
import software.amazon.awssdk.core.internal.waiters.ResponseOrException;
import software.amazon.awssdk.core.waiters.WaiterAcceptor;
import software.amazon.awssdk.core.waiters.WaiterResponse;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.GetEventSourceMappingRequest;
import software.amazon.awssdk.services.lambda.model.GetEventSourceMappingResponse;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

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
}
