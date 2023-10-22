/*
 * Copyright 2023 Matus Faro
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

package io.dataspray.common.aws.test;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.primitives.Ints;
import com.google.gson.Gson;
import io.quarkus.arc.properties.IfBuildProperty;
import io.quarkus.test.junit.QuarkusTestProfile;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Setter;
import lombok.Value;
import lombok.experimental.NonFinal;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.mockito.Mockito;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.AddPermissionRequest;
import software.amazon.awssdk.services.lambda.model.AddPermissionResponse;
import software.amazon.awssdk.services.lambda.model.CreateAliasRequest;
import software.amazon.awssdk.services.lambda.model.CreateAliasResponse;
import software.amazon.awssdk.services.lambda.model.CreateEventSourceMappingRequest;
import software.amazon.awssdk.services.lambda.model.CreateEventSourceMappingResponse;
import software.amazon.awssdk.services.lambda.model.CreateFunctionRequest;
import software.amazon.awssdk.services.lambda.model.CreateFunctionResponse;
import software.amazon.awssdk.services.lambda.model.DeleteFunctionRequest;
import software.amazon.awssdk.services.lambda.model.DeleteFunctionResponse;
import software.amazon.awssdk.services.lambda.model.EventSourceMappingConfiguration;
import software.amazon.awssdk.services.lambda.model.FunctionConfiguration;
import software.amazon.awssdk.services.lambda.model.GetAliasRequest;
import software.amazon.awssdk.services.lambda.model.GetAliasResponse;
import software.amazon.awssdk.services.lambda.model.GetEventSourceMappingRequest;
import software.amazon.awssdk.services.lambda.model.GetEventSourceMappingResponse;
import software.amazon.awssdk.services.lambda.model.GetFunctionConcurrencyRequest;
import software.amazon.awssdk.services.lambda.model.GetFunctionConcurrencyResponse;
import software.amazon.awssdk.services.lambda.model.GetFunctionRequest;
import software.amazon.awssdk.services.lambda.model.GetFunctionResponse;
import software.amazon.awssdk.services.lambda.model.GetPolicyRequest;
import software.amazon.awssdk.services.lambda.model.GetPolicyResponse;
import software.amazon.awssdk.services.lambda.model.LastUpdateStatus;
import software.amazon.awssdk.services.lambda.model.ListEventSourceMappingsRequest;
import software.amazon.awssdk.services.lambda.model.ListEventSourceMappingsResponse;
import software.amazon.awssdk.services.lambda.model.ListFunctionsRequest;
import software.amazon.awssdk.services.lambda.model.ListFunctionsResponse;
import software.amazon.awssdk.services.lambda.model.ListVersionsByFunctionRequest;
import software.amazon.awssdk.services.lambda.model.ListVersionsByFunctionResponse;
import software.amazon.awssdk.services.lambda.model.PutFunctionConcurrencyRequest;
import software.amazon.awssdk.services.lambda.model.PutFunctionConcurrencyResponse;
import software.amazon.awssdk.services.lambda.model.ResourceConflictException;
import software.amazon.awssdk.services.lambda.model.ResourceNotFoundException;
import software.amazon.awssdk.services.lambda.model.UpdateAliasRequest;
import software.amazon.awssdk.services.lambda.model.UpdateAliasResponse;
import software.amazon.awssdk.services.lambda.model.UpdateEventSourceMappingRequest;
import software.amazon.awssdk.services.lambda.model.UpdateEventSourceMappingResponse;
import software.amazon.awssdk.services.lambda.model.UpdateFunctionCodeRequest;
import software.amazon.awssdk.services.lambda.model.UpdateFunctionCodeResponse;
import software.amazon.awssdk.services.lambda.model.UpdateFunctionConfigurationRequest;
import software.amazon.awssdk.services.lambda.model.UpdateFunctionConfigurationResponse;
import software.amazon.awssdk.services.lambda.paginators.ListEventSourceMappingsIterable;
import software.amazon.awssdk.services.lambda.paginators.ListFunctionsIterable;
import software.amazon.awssdk.services.lambda.paginators.ListVersionsByFunctionIterable;
import software.amazon.awssdk.services.lambda.waiters.LambdaWaiter;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.dataspray.common.aws.test.MockIamClient.SDK_200;
import static org.mockito.Mockito.when;

@ApplicationScoped
public class MockLambdaClient {

    private static final Pattern FUNCTION_NAME_OR_ARN = Pattern.compile("(arn:(aws[a-zA-Z-]*)?:lambda:)?([a-z]{2}(-gov)?-[a-z]+-\\d{1}:)?(\\d{12}:)?(function:)?(?<functionName>[a-zA-Z0-9-_]+)(:(?<version>\\$LATEST|[a-zA-Z0-9-_]+))?");

    @ConfigProperty(name = "aws.accountId")
    String awsAccountId;
    @ConfigProperty(name = "aws.region")
    String awsRegion;

    @Inject
    Gson gson;

    @Alternative
    @Priority(1)
    @Singleton
    @IfBuildProperty(name = "aws.lambda.mock.enable", stringValue = "true")
    public LambdaClient getLambdaClient() {
        LambdaClient mock = Mockito.mock(LambdaClient.class);

        Map<String, Function> functions = Maps.newConcurrentMap();
        List<SourceMapping> sourceMappings = Collections.synchronizedList(Lists.newArrayList());
        Map<String, VersionAlias> aliases = Maps.newConcurrentMap();

        when(mock.createFunction(Mockito.<CreateFunctionRequest>any()))
                .thenAnswer(invocation -> {
                    CreateFunctionRequest request = invocation.getArgument(0, CreateFunctionRequest.class);
                    Function function = new Function(functionNameStripArn(request.functionName()), Sets.newConcurrentHashSet(), null, Maps.newConcurrentMap());
                    String version = Boolean.TRUE.equals(request.publish())
                            ? function.publish().toString() : "$LATEST";
                    functions.put(functionNameStripArn(request.functionName()), function);
                    return CreateFunctionResponse.builder()
                            .functionName(function.getName())
                            .version(version)
                            .revisionId(UUID.randomUUID().toString())
                            .sdkHttpResponse(SDK_200)
                            .build();
                });
        when(mock.deleteFunction(Mockito.<DeleteFunctionRequest>any()))
                .thenAnswer(invocation -> {
                    DeleteFunctionRequest request = invocation.getArgument(0, DeleteFunctionRequest.class);
                    if (functions.remove(functionNameStripArn(request.functionName())) == null) {
                        throw ResourceNotFoundException.builder().message("Function not found").build();
                    }
                    return DeleteFunctionResponse.builder()
                            .sdkHttpResponse(SDK_200)
                            .build();
                });
        when(mock.updateFunctionCode(Mockito.<UpdateFunctionCodeRequest>any()))
                .thenAnswer(invocation -> {
                    UpdateFunctionCodeRequest request = invocation.getArgument(0, UpdateFunctionCodeRequest.class);
                    Function function = functions.get(functionNameStripArn(request.functionName()));
                    if (function == null) {
                        throw ResourceNotFoundException.builder().message("Function not found").build();
                    }
                    String version = Boolean.TRUE.equals(request.publish())
                            ? function.publish().toString() : "$LATEST";
                    return UpdateFunctionCodeResponse.builder()
                            .functionName(function.getName())
                            .version(version)
                            .revisionId(UUID.randomUUID().toString())
                            .sdkHttpResponse(SDK_200)
                            .build();
                });
        when(mock.updateFunctionConfiguration(Mockito.<UpdateFunctionConfigurationRequest>any()))
                .thenAnswer(invocation -> {
                    UpdateFunctionConfigurationRequest request = invocation.getArgument(0, UpdateFunctionConfigurationRequest.class);
                    Function function = functions.get(functionNameStripArn(request.functionName()));
                    if (function == null) {
                        throw ResourceNotFoundException.builder().message("Function not found").build();
                    }
                    return UpdateFunctionConfigurationResponse.builder()
                            .functionName(function.getName())
                            .version("$LATEST")
                            .revisionId(UUID.randomUUID().toString())
                            .sdkHttpResponse(SDK_200)
                            .build();
                });

        when(mock.getFunction(Mockito.<GetFunctionRequest>any()))
                .thenAnswer(invocation -> {
                    GetFunctionRequest request = invocation.getArgument(0, GetFunctionRequest.class);
                    Function function = functions.get(functionNameStripArn(request.functionName()));
                    if (function == null) {
                        throw ResourceNotFoundException.builder().message("Function not found").build();
                    }
                    String version = "$LATEST";
                    if (!Strings.isNullOrEmpty(request.qualifier())) {
                        Integer qualifierVersion = Ints.tryParse(request.qualifier());
                        if (qualifierVersion != null) {
                            if (!function.getPublishedVersions().contains(Integer.valueOf(request.qualifier()))) {
                                throw ResourceNotFoundException.builder().message("Version not found").build();
                            }
                            version = qualifierVersion.toString();
                        } else {
                            VersionAlias alias = aliases.get(VersionAlias.getKey(functionNameStripArn(request.functionName()), request.qualifier()));
                            if (alias == null) {
                                throw ResourceNotFoundException.builder().message("Alias not found").build();
                            }
                            version = alias.getVersion();
                        }
                    }
                    return GetFunctionResponse.builder()
                            .configuration(FunctionConfiguration.builder()
                                    .functionName(function.getName())
                                    .lastUpdateStatus(LastUpdateStatus.SUCCESSFUL)
                                    .description("Function version " + version)
                                    .version(version).build())
                            .sdkHttpResponse(SDK_200)
                            .build();
                });
        when(mock.listFunctions(Mockito.<ListFunctionsRequest>any()))
                .thenAnswer(invocation -> ListFunctionsResponse.builder()
                        .functions(functions.values().stream()
                                .map(function -> FunctionConfiguration.builder()
                                        .functionName(function.getName())
                                        .lastUpdateStatus(LastUpdateStatus.SUCCESSFUL)
                                        .description("Function version $LATEST")
                                        .version("$LATEST").build())
                                .collect(ImmutableList.toImmutableList()))
                        .sdkHttpResponse(SDK_200)
                        .build());
        when(mock.listFunctionsPaginator(Mockito.<ListFunctionsRequest>any()))
                .thenAnswer(invocation -> new ListFunctionsIterable(mock, invocation.getArgument(0, ListFunctionsRequest.class)));

        when(mock.listVersionsByFunction(Mockito.<ListVersionsByFunctionRequest>any()))
                .thenAnswer(invocation -> {
                    ListVersionsByFunctionRequest request = invocation.getArgument(0, ListVersionsByFunctionRequest.class);
                    Function function = functions.get(functionNameStripArn(request.functionName()));
                    if (function == null) {
                        throw ResourceNotFoundException.builder().message("Function not found").build();
                    }
                    return ListVersionsByFunctionResponse.builder()
                            .versions(Stream.concat(Stream.of("$LATEST"), function.publishedVersions.stream().map(Object::toString))
                                    .map(version -> FunctionConfiguration.builder()
                                            .functionName(function.getName())
                                            .lastUpdateStatus(LastUpdateStatus.SUCCESSFUL)
                                            .version(version)
                                            .description("Function version " + version)
                                            .build())
                                    .collect(Collectors.toList()))
                            .sdkHttpResponse(SDK_200)
                            .build();
                });
        when(mock.listVersionsByFunctionPaginator(Mockito.<ListVersionsByFunctionRequest>any()))
                .thenAnswer(invocation -> new ListVersionsByFunctionIterable(mock, invocation.getArgument(0, ListVersionsByFunctionRequest.class)));

        when(mock.createEventSourceMapping(Mockito.<CreateEventSourceMappingRequest>any()))
                .thenAnswer(invocation -> {
                    CreateEventSourceMappingRequest request = invocation.getArgument(0, CreateEventSourceMappingRequest.class);
                    SourceMapping sourceMapping = new SourceMapping(
                            UUID.randomUUID().toString(),
                            request.eventSourceArn(),
                            functionNameStripArn(request.functionName()),
                            functionArnGetVersion(request.functionName()),
                            Boolean.TRUE.equals(request.enabled()));
                    sourceMappings.add(sourceMapping);
                    return CreateEventSourceMappingResponse.builder()
                            .uuid(sourceMapping.uuid)
                            .state(sourceMapping.isEnabled() ? "Enabled" : "Disabled")
                            .eventSourceArn(sourceMapping.getEventSourceArn())
                            .sdkHttpResponse(SDK_200)
                            .build();
                });
        when(mock.updateEventSourceMapping(Mockito.<UpdateEventSourceMappingRequest>any()))
                .thenAnswer(invocation -> {
                    UpdateEventSourceMappingRequest request = invocation.getArgument(0, UpdateEventSourceMappingRequest.class);
                    SourceMapping sourceMapping = sourceMappings.stream().filter(sm -> sm.getUuid().equals(request.uuid()))
                            .findAny().orElseThrow(() -> ResourceNotFoundException.builder().message("Source mapping not found").build());
                    sourceMapping.setEnabled(Boolean.TRUE.equals(request.enabled()));
                    return UpdateEventSourceMappingResponse.builder()
                            .uuid(sourceMapping.uuid)
                            .state(sourceMapping.isEnabled() ? "Enabled" : "Disabled")
                            .eventSourceArn(sourceMapping.getEventSourceArn())
                            .sdkHttpResponse(SDK_200)
                            .build();
                });
        when(mock.getEventSourceMapping(Mockito.<GetEventSourceMappingRequest>any()))
                .thenAnswer(invocation -> {
                    GetEventSourceMappingRequest request = invocation.getArgument(0, GetEventSourceMappingRequest.class);
                    SourceMapping sourceMapping = sourceMappings.stream().filter(sm -> sm.getUuid().equals(request.uuid()))
                            .findAny().orElseThrow(() -> ResourceNotFoundException.builder().message("Source mapping not found").build());
                    return GetEventSourceMappingResponse.builder()
                            .uuid(sourceMapping.getUuid())
                            .state(sourceMapping.isEnabled() ? "Enabled" : "Disabled")
                            .eventSourceArn(sourceMapping.getEventSourceArn())
                            .sdkHttpResponse(SDK_200)
                            .build();
                });
        when(mock.listEventSourceMappings(Mockito.<ListEventSourceMappingsRequest>any()))
                .thenAnswer(invocation -> {
                    ListEventSourceMappingsRequest request = invocation.getArgument(0, ListEventSourceMappingsRequest.class);
                    return ListEventSourceMappingsResponse.builder()
                            .eventSourceMappings(sourceMappings.stream()
                                    .filter(sm -> sm.getFunctionName().equals(functionNameStripArn(request.functionName())))
                                    .filter(sm -> sm.getFunctionVersion().equals(functionArnGetVersion(request.functionName())))
                                    .map(sourceMapping -> EventSourceMappingConfiguration.builder()
                                            .uuid(sourceMapping.getUuid())
                                            .state(sourceMapping.isEnabled() ? "Enabled" : "Disabled")
                                            .eventSourceArn(sourceMapping.getEventSourceArn())
                                            .build())
                                    .collect(Collectors.toList()))
                            .sdkHttpResponse(SDK_200).build();
                });
        when(mock.listEventSourceMappingsPaginator(Mockito.<ListEventSourceMappingsRequest>any()))
                .thenAnswer(invocation -> new ListEventSourceMappingsIterable(mock, invocation.getArgument(0, ListEventSourceMappingsRequest.class)));

        when(mock.createAlias(Mockito.<CreateAliasRequest>any()))
                .thenAnswer(invocation -> {
                    CreateAliasRequest request = invocation.getArgument(0, CreateAliasRequest.class);
                    VersionAlias alias = new VersionAlias(functionNameStripArn(request.functionName()), request.name(), request.functionVersion());
                    if (aliases.putIfAbsent(alias.getKey(), alias) != null) {
                        throw ResourceConflictException.builder().build();
                    }
                    return CreateAliasResponse.builder()
                            .name(alias.getName())
                            .functionVersion(alias.getVersion())
                            .sdkHttpResponse(SDK_200)
                            .build();
                });
        when(mock.updateAlias(Mockito.<UpdateAliasRequest>any()))
                .thenAnswer(invocation -> {
                    UpdateAliasRequest request = invocation.getArgument(0, UpdateAliasRequest.class);
                    VersionAlias alias = aliases.compute(VersionAlias.getKey(functionNameStripArn(request.functionName()), request.name()), (s, a) -> {
                        if (a == null) {
                            throw ResourceNotFoundException.builder().message("Alias not found").build();
                        }
                        a.setVersion(request.functionVersion());
                        return a;
                    });
                    return UpdateAliasResponse.builder()
                            .name(alias.getName())
                            .functionVersion(alias.getVersion())
                            .sdkHttpResponse(SDK_200)
                            .build();
                });
        when(mock.getAlias(Mockito.<GetAliasRequest>any()))
                .thenAnswer(invocation -> {
                    GetAliasRequest request = invocation.getArgument(0, GetAliasRequest.class);
                    VersionAlias alias = aliases.get(VersionAlias.getKey(functionNameStripArn(request.functionName()), request.name()));
                    if (alias == null) {
                        throw ResourceNotFoundException.builder().message("Alias not found").build();
                    }
                    return GetAliasResponse.builder()
                            .name(alias.getName())
                            .functionVersion(alias.getVersion())
                            .sdkHttpResponse(SDK_200)
                            .build();
                });


        when(mock.addPermission(Mockito.<AddPermissionRequest>any()))
                .thenAnswer(invocation -> {
                    AddPermissionRequest request = invocation.getArgument(0, AddPermissionRequest.class);
                    Function function = functions.get(functionNameStripArn(request.functionName()));
                    if (function == null) {
                        throw ResourceNotFoundException.builder().message("Function not found").build();
                    }
                    if (!function.getQualifierToStatementIds()
                            .computeIfAbsent(request.qualifier(), k -> Sets.newConcurrentHashSet())
                            .add(request.statementId())) {
                        throw ResourceConflictException.builder().message("Permission already exists").build();
                    }
                    return AddPermissionResponse.builder()
                            .sdkHttpResponse(SDK_200)
                            .build();
                });

        when(mock.getPolicy(Mockito.<GetPolicyRequest>any()))
                .thenAnswer(invocation -> {
                    GetPolicyRequest request = invocation.getArgument(0, GetPolicyRequest.class);
                    Function function = functions.get(functionNameStripArn(request.functionName()));
                    if (function == null) {
                        throw ResourceNotFoundException.builder().message("Function not found").build();
                    }
                    Set<String> statementIds = function.getQualifierToStatementIds().getOrDefault(request.qualifier(), Set.of());
                    String policy = gson.toJson(Map.of(
                            "Version", "2012-10-17",
                            "Statement", statementIds.stream()
                                    .map(statementId -> Map.of("Sid", statementId))
                                    .collect(Collectors.toList())));
                    return GetPolicyResponse.builder()
                            .policy(policy)
                            .sdkHttpResponse(SDK_200)
                            .build();
                });

        when(mock.getFunctionConcurrency(Mockito.<GetFunctionConcurrencyRequest>any()))
                .thenAnswer(invocation -> {
                    GetFunctionConcurrencyRequest request = invocation.getArgument(0, GetFunctionConcurrencyRequest.class);
                    Function function = functions.get(functionNameStripArn(request.functionName()));
                    if (function == null) {
                        throw ResourceNotFoundException.builder().message("Function not found").build();
                    }
                    return GetFunctionConcurrencyResponse.builder()
                            .reservedConcurrentExecutions(function.getReservedConcurrency())
                            .sdkHttpResponse(SDK_200)
                            .build();
                });
        when(mock.putFunctionConcurrency(Mockito.<PutFunctionConcurrencyRequest>any()))
                .thenAnswer(invocation -> {
                    PutFunctionConcurrencyRequest request = invocation.getArgument(0, PutFunctionConcurrencyRequest.class);
                    Function function = functions.get(functionNameStripArn(request.functionName()));
                    if (function == null) {
                        throw ResourceNotFoundException.builder().message("Function not found").build();
                    }
                    function.setReservedConcurrency(request.reservedConcurrentExecutions());
                    return PutFunctionConcurrencyResponse.builder()
                            .reservedConcurrentExecutions(function.getReservedConcurrency())
                            .sdkHttpResponse(SDK_200)
                            .build();
                });

        when(mock.waiter())
                .thenReturn(LambdaWaiter.builder().client(mock).build());

        return mock;
    }

    private String functionArnGetVersion(String functionNameOrArn) {
        Matcher matcher = FUNCTION_NAME_OR_ARN.matcher(functionNameOrArn);
        if (!matcher.matches()) {
            throw new RuntimeException("Invalid function name or arn: " + functionNameOrArn);
        }
        return Optional.ofNullable(Strings.emptyToNull(matcher.group("version")))
                .orElse("$LATEST");
    }

    private String functionNameStripArn(String functionNameOrArn) {
        Matcher matcher = FUNCTION_NAME_OR_ARN.matcher(functionNameOrArn);
        if (!matcher.matches()) {
            throw new RuntimeException("Invalid function name or arn: " + functionNameOrArn);
        }
        return matcher.group("functionName");
    }

    @Value
    @Builder(toBuilder = true)
    @AllArgsConstructor
    private static class Function {
        @Nonnull
        String name;

        @Nonnull
        Set<Integer> publishedVersions;

        @Setter
        @NonFinal
        Integer reservedConcurrency;

        @Nonnull
        Map<String, Set<String>> qualifierToStatementIds;

        Integer publish() {
            int newVersion = getPublishedVersions().stream()
                                     .max(Integer::compareTo)
                                     .orElse(0)
                             + 1;
            getPublishedVersions().add(newVersion);
            return newVersion;
        }
    }

    @Value
    @Builder(toBuilder = true)
    @AllArgsConstructor
    private static class SourceMapping {
        @Nonnull
        String uuid;

        @Nonnull
        String eventSourceArn;

        @Nonnull
        String functionName;

        @Nonnull
        String functionVersion;

        @Setter
        @NonFinal
        boolean enabled;
    }

    @Value
    @Builder(toBuilder = true)
    @AllArgsConstructor
    private static class VersionAlias {
        @Nonnull
        String functionName;

        @Nonnull
        String name;

        @Setter
        @NonFinal
        String version;

        public String getKey() {
            return VersionAlias.getKey(getFunctionName(), getName());
        }

        public static String getKey(String functionName, String aliasName) {
            return functionName + "-" + aliasName;
        }
    }

    public static class Profile implements QuarkusTestProfile {

        public Map<String, String> getConfigOverrides() {
            return ImmutableMap.of("aws.lambda.mock.enable", "true");
        }
    }
}
