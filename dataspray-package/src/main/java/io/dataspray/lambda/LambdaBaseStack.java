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

package io.dataspray.lambda;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.net.InternetDomainName;
import io.dataspray.authorizer.Authorizer;
import io.dataspray.backend.BaseStack;
import io.dataspray.store.CognitoAccountStore;
import lombok.Getter;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.Value;
import software.amazon.awscdk.Duration;
import software.amazon.awscdk.services.apigateway.ApiDefinition;
import software.amazon.awscdk.services.apigateway.DomainNameOptions;
import software.amazon.awscdk.services.apigateway.EndpointType;
import software.amazon.awscdk.services.apigateway.SpecRestApi;
import software.amazon.awscdk.services.certificatemanager.Certificate;
import software.amazon.awscdk.services.certificatemanager.CertificateValidation;
import software.amazon.awscdk.services.iam.ServicePrincipal;
import software.amazon.awscdk.services.lambda.Architecture;
import software.amazon.awscdk.services.lambda.Code;
import software.amazon.awscdk.services.lambda.Permission;
import software.amazon.awscdk.services.lambda.Runtime;
import software.amazon.awscdk.services.lambda.SingletonFunction;
import software.amazon.awscdk.services.route53.HostedZone;
import software.amazon.awscdk.services.route53.RecordSet;
import software.amazon.awscdk.services.route53.RecordTarget;
import software.amazon.awscdk.services.route53.RecordType;
import software.amazon.awscdk.services.route53.targets.ApiGateway;
import software.constructs.Construct;

import java.io.File;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class LambdaBaseStack extends BaseStack {
    private static final String QUARKUS_LAMBDA_HANDLER = "io.quarkus.amazon.lambda.runtime.QuarkusStreamHandler::handleRequest";
    @Getter
    private final SingletonFunction function;
    @Getter
    private final Certificate certificate;
    @Getter
    private final SpecRestApi restApi;
    @Getter
    private final RecordSet recordSet;

    public LambdaBaseStack(Construct parent, Options options) {
        super(parent, "lambda-" + options.getFunctionName(), options.getEnv());

        function = SingletonFunction.Builder.create(this, getSubConstructId("lambda"))
                .uuid(UUID.nameUUIDFromBytes(getConstructId().getBytes(Charsets.UTF_8)).toString())
                .functionName(options.getFunctionName())
                .code(Code.fromAsset(options.getCodePath()))
                .handler(QUARKUS_LAMBDA_HANDLER)
                .runtime(Runtime.JAVA_11)
                .architecture(Architecture.ARM_64)
                .memorySize(options.getMemorySize())
                .timeout(Duration.seconds(30))
                .build();

        Map<String, Object> openApiSpec = constructOpenApiForApiGateway(options);
        addApiGatewayExtensionsToOpenapiSpec(openApiSpec, options, function);

        URL serverUrl = getServerUrl(openApiSpec);
        String domain = serverUrl.getHost();
        String baseDomain = InternetDomainName.from(domain).topPrivateDomain().toString();

        certificate = Certificate.Builder.create(this, getSubConstructId("cert"))
                .domainName(domain)
                .validation(CertificateValidation.fromDns(options.getDnsZone()))
                .build();
        restApi = SpecRestApi.Builder.create(this, getSubConstructId("apigateway"))
                .apiDefinition(ApiDefinition.fromInline(openApiSpec))
                .domainName(DomainNameOptions.builder()
                        .certificate(certificate)
                        .endpointType(EndpointType.REGIONAL)
                        .domainName(domain)
                        .build())
                .build();
        recordSet = RecordSet.Builder.create(this, getSubConstructId("recordset"))
                .zone(options.getDnsZone())
                .recordType(RecordType.A)
                .recordName(domain)
                .target(RecordTarget.fromAlias(new ApiGateway(restApi)))
                .ttl(Duration.seconds(30))
                .deleteExisting(true)
                .build();
        getFunction().addPermission(options.getFunctionName() + "-gateway-to-lambda-permission", Permission.builder()
                .sourceArn(restApi.arnForExecuteApi())
                .principal(ServicePrincipal.Builder
                        .create("apigateway.amazonaws.com").build())
                .action("lambda:InvokeFunction")
                .build());
    }

    @SneakyThrows
    private static Map<String, Object> constructOpenApiForApiGateway(Options options) {
        return new ObjectMapper(new YAMLFactory()).readValue(new File(options.openapiYamlPath), new TypeReference<Map<String, Object>>() {
        });
    }

    @SuppressWarnings("unchecked")
    @SneakyThrows
    private static URL getServerUrl(Map<String, Object> openApiSpec) {
        List<Map<String, Object>> servers = (List<Map<String, Object>>) openApiSpec.get("servers");
        String serverUrlStr = (String) servers.get(0).get("url");
        return new URL(serverUrlStr);
    }

    /**
     * <a
     * href="https://docs.aws.amazon.com/apigateway/latest/developerguide/api-gateway-swagger-extensions.html">Documentation
     * on Amazon OpenAPI extensions</a>
     */
    @SuppressWarnings("unchecked")
    @SneakyThrows
    private void addApiGatewayExtensionsToOpenapiSpec(Map<String, Object> openApiSpec, Options options, SingletonFunction function) {
        Map<String, Object> components = (Map<String, Object>) openApiSpec.get("components");
        if (components != null) {
            Map<String, Object> securitySchemes = (Map<String, Object>) components.get("securitySchemes");
            if (securitySchemes != null) {
                for (String securitySchemeName : ImmutableSet.copyOf(securitySchemes.keySet())) {
                    Map<String, Object> securityScheme = (Map<String, Object>) securitySchemes.get(securitySchemeName);
                    if (securityScheme != null
                        && "apiKey".equals(securityScheme.get("type"))
                        && "header".equals(securityScheme.get("in"))
                        && securityScheme.containsKey("name")
                        && Authorizer.AUTHORIZATION_HEADER.equalsIgnoreCase((String) securityScheme.get("name"))) {
                        // Cognito Authorization
                        securityScheme.putAll(Map.of(
                                // Docs https://docs.aws.amazon.com/apigateway/latest/developerguide/api-gateway-swagger-extensions-authtype.html
                                "x-amazon-apigateway-authtype", "custom",
                                // Docs https://docs.aws.amazon.com/apigateway/latest/developerguide/api-gateway-swagger-extensions-authorizer.html
                                "x-amazon-apigateway-authorizer", Map.of(
                                        "type", "request",
                                        "identitySource", "method.request.header." + Authorizer.AUTHORIZATION_HEADER.toLowerCase(),
                                        "authorizerCredentials", options.getAuthorizerStack().getRoleApiGatewayInvoke().getRoleArn(),
                                        "authorizerUri", options.getAuthorizerStack().getFunction().getFunctionArn() + "/invocations",
                                        "authorizerResultTtlInSeconds", TimeUnit.MINUTES.toSeconds(5))));
                    }
                }
            }
        }
        Map<String, Object> paths = (Map<String, Object>) openApiSpec.get("paths");
        if (paths != null) {
            for (String path : ImmutableSet.copyOf(paths.keySet())) {
                Map<String, Object> methods = (Map<String, Object>) paths.get(path);
                if (methods != null) {
                    for (String method : ImmutableSet.copyOf(methods.keySet())) {
                        Map<String, Object> methodData = (Map<String, Object>) methods.get(method);
                        // Docs https://docs.aws.amazon.com/apigateway/latest/developerguide/api-gateway-swagger-extensions-integration.html
                        methodData.put("x-amazon-apigateway-integration", ImmutableMap.builder()
                                .put("httpMethod", "POST")
                                .put("uri", function.getFunctionArn() + "/invocations")
                                .put("responses", ImmutableMap.of(
                                        "default", ImmutableMap.of(
                                                "statusCode", "200")))
                                .put("passthroughBehavior", "when_no_match")
                                .put("contentHandling", "CONVERT_TO_TEXT")
                                .put("type", "aws_proxy")
                                .build());
                    }
                }
            }
        }
        // Docs https://docs.aws.amazon.com/apigateway/latest/developerguide/api-gateway-swagger-extensions-endpoint-configuration.html
        openApiSpec.put("x-amazon-apigateway-endpoint-configuration", ImmutableMap.of(
                "disableExecuteApiEndpoint", true));
        // Docs https://docs.aws.amazon.com/apigateway/latest/developerguide/api-gateway-swagger-extensions-binary-media-types.html
        openApiSpec.put("x-amazon-apigateway-binary-media-types", ImmutableList.of("*/*"));
    }

    public void withCognitoUserPoolIdRef(String cognitoUserPoolId) {
        getFunction().addEnvironment(CognitoAccountStore.USER_POOL_ID_PROP_NAME, cognitoUserPoolId);
    }

    @Value
    @lombok.Builder
    public static class Options {
        @NonNull
        String env;
        @NonNull
        String functionName;
        @NonNull
        String codePath;
        @NonNull
        String openapiYamlPath;
        @NonNull
        HostedZone dnsZone;
        @NonNull
        AuthorizerStack authorizerStack;
        @lombok.Builder.Default
        int memorySize = 512;
    }
}
