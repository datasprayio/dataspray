package io.dataspray.common.aws.test;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import io.quarkus.arc.Priority;
import io.quarkus.arc.properties.IfBuildProperty;
import io.quarkus.test.junit.QuarkusTestProfile;
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

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Alternative;
import javax.inject.Singleton;
import java.util.Map;

import static org.mockito.Mockito.when;

@ApplicationScoped
public class MockLambdaClient {

    @Alternative
    @Priority(1)
    @Singleton
    @IfBuildProperty(name = "aws.lambda.mock.enable", stringValue = "true")
    public LambdaClient getLambdaClient() {
        LambdaClient mock = Mockito.mock(LambdaClient.class);

        FunctionConfiguration functionConfiguration = FunctionConfiguration.builder()
                .functionName("function_name")
                .lastUpdateStatus("last_status_update")
                .lastUpdateStatusReason("last_update_status_reason").build();

        when(mock.getFunction(Mockito.<GetFunctionRequest>any()))
                .thenReturn(GetFunctionResponse.builder()
                        .configuration(functionConfiguration).build());
        when(mock.listFunctions(Mockito.<ListFunctionsRequest>any()))
                .thenReturn(ListFunctionsResponse.builder()
                        .functions(ImmutableSet.of(functionConfiguration)).build());

        when(mock.createFunction(Mockito.<CreateFunctionRequest>any()))
                .thenReturn(CreateFunctionResponse.builder()
                        .functionName("function_name")
                        .lastUpdateStatus("last_status_update")
                        .lastUpdateStatusReason("last_update_status_reason").build());
        when(mock.deleteFunction(Mockito.<DeleteFunctionRequest>any()))
                .thenReturn(DeleteFunctionResponse.builder().build());
        when(mock.updateFunctionCode(Mockito.<UpdateFunctionCodeRequest>any()))
                .thenReturn(UpdateFunctionCodeResponse.builder().build());

        when(mock.getFunctionConcurrency(Mockito.<GetFunctionConcurrencyRequest>any()))
                .thenReturn(GetFunctionConcurrencyResponse.builder().build());
        when(mock.putFunctionConcurrency(Mockito.<PutFunctionConcurrencyRequest>any()))
                .thenReturn(PutFunctionConcurrencyResponse.builder().build());

        return mock;
    }

    public static class TestProfile implements QuarkusTestProfile {

        public Map<String, String> getConfigOverrides() {
            return ImmutableMap.of("aws.lambda.mock.enable", "true");
        }
    }
}
