package io.dataspray.common.aws.test;

import com.amazonaws.services.dynamodbv2.model.ResourceNotFoundException;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import io.quarkus.arc.Priority;
import io.quarkus.arc.properties.IfBuildProperty;
import io.quarkus.test.junit.QuarkusTestProfile;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Setter;
import lombok.Value;
import lombok.experimental.NonFinal;
import org.mockito.Mockito;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.CreateFunctionRequest;
import software.amazon.awssdk.services.lambda.model.CreateFunctionResponse;
import software.amazon.awssdk.services.lambda.model.DeleteFunctionRequest;
import software.amazon.awssdk.services.lambda.model.DeleteFunctionResponse;
import software.amazon.awssdk.services.lambda.model.FunctionConfiguration;
import software.amazon.awssdk.services.lambda.model.GetFunctionConcurrencyRequest;
import software.amazon.awssdk.services.lambda.model.GetFunctionConcurrencyResponse;
import software.amazon.awssdk.services.lambda.model.GetFunctionRequest;
import software.amazon.awssdk.services.lambda.model.GetFunctionResponse;
import software.amazon.awssdk.services.lambda.model.ListFunctionsRequest;
import software.amazon.awssdk.services.lambda.model.ListFunctionsResponse;
import software.amazon.awssdk.services.lambda.model.PutFunctionConcurrencyRequest;
import software.amazon.awssdk.services.lambda.model.PutFunctionConcurrencyResponse;
import software.amazon.awssdk.services.lambda.model.UpdateFunctionCodeRequest;
import software.amazon.awssdk.services.lambda.model.UpdateFunctionCodeResponse;

import javax.annotation.Nonnull;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Alternative;
import javax.inject.Singleton;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;

import static org.mockito.Mockito.when;

@ApplicationScoped
public class MockLambdaClient {

    @Alternative
    @Priority(1)
    @Singleton
    @IfBuildProperty(name = "aws.lambda.mock.enable", stringValue = "true")
    public LambdaClient getLambdaClient() {
        LambdaClient mock = Mockito.mock(LambdaClient.class);

        ConcurrentMap<String, Function> functions = Maps.newConcurrentMap();

        when(mock.createFunction(Mockito.<CreateFunctionRequest>any()))
                .thenAnswer(invocation -> {
                    CreateFunctionRequest request = invocation.getArgument(0, CreateFunctionRequest.class);
                    Function function = new Function(request.functionName(), null);
                    functions.put(request.functionName(), function);
                    return CreateFunctionResponse.builder()
                            .functionName(function.getName()).build();
                });
        when(mock.deleteFunction(Mockito.<DeleteFunctionRequest>any()))
                .thenAnswer(invocation -> {
                    DeleteFunctionRequest request = invocation.getArgument(0, DeleteFunctionRequest.class);
                    if (functions.remove(request.functionName()) == null) {
                        throw new ResourceNotFoundException("Not found");
                    }
                    return DeleteFunctionResponse.builder().build();
                });
        when(mock.updateFunctionCode(Mockito.<UpdateFunctionCodeRequest>any()))
                .thenReturn(UpdateFunctionCodeResponse.builder().build());

        when(mock.getFunction(Mockito.<GetFunctionRequest>any()))
                .thenAnswer(invocation -> {
                    GetFunctionRequest request = invocation.getArgument(0, GetFunctionRequest.class);
                    Function function = functions.get(request.functionName());
                    if (function == null) {
                        throw new ResourceNotFoundException("Not found");
                    }
                    return GetFunctionResponse.builder()
                            .configuration(FunctionConfiguration.builder()
                                    .functionName(function.getName()).build()).build();
                });
        when(mock.listFunctions(Mockito.<ListFunctionsRequest>any()))
                .thenAnswer(invocation -> ListFunctionsResponse.builder()
                        .functions(functions.values().stream()
                                .map(function -> FunctionConfiguration.builder()
                                        .functionName(function.getName()).build())
                                .collect(ImmutableList.toImmutableList())).build());

        when(mock.getFunctionConcurrency(Mockito.<GetFunctionConcurrencyRequest>any()))
                .thenAnswer(invocation -> {
                    GetFunctionConcurrencyRequest request = invocation.getArgument(0, GetFunctionConcurrencyRequest.class);
                    Function function = functions.get(request.functionName());
                    if (function == null) {
                        throw new ResourceNotFoundException("Not found");
                    }
                    return GetFunctionConcurrencyResponse.builder()
                            .reservedConcurrentExecutions(function.getReservedConcurrency()).build();
                });
        when(mock.putFunctionConcurrency(Mockito.<PutFunctionConcurrencyRequest>any()))
                .thenAnswer(invocation -> {
                    PutFunctionConcurrencyRequest request = invocation.getArgument(0, PutFunctionConcurrencyRequest.class);
                    Function function = functions.get(request.functionName());
                    if (function == null) {
                        throw new ResourceNotFoundException("Not found");
                    }
                    function.setReservedConcurrency(request.reservedConcurrentExecutions());
                    return PutFunctionConcurrencyResponse.builder()
                            .reservedConcurrentExecutions(function.getReservedConcurrency()).build();
                });

        return mock;
    }

    @Value
    @Builder(toBuilder = true)
    @AllArgsConstructor
    private static class Function {
        @Nonnull
        String name;
        @Setter
        @NonFinal
        Integer reservedConcurrency;
    }

    public static class TestProfile implements QuarkusTestProfile {

        public Map<String, String> getConfigOverrides() {
            return ImmutableMap.of("aws.lambda.mock.enable", "true");
        }
    }
}
