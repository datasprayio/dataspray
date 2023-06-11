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

package io.dataspray.lambda.deploy;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.net.InternetDomainName;
import io.dataspray.backend.deploy.BaseStack;
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
import software.amazon.awscdk.services.route53.HostedZoneProviderProps;
import software.amazon.awscdk.services.route53.IHostedZone;
import software.amazon.awscdk.services.route53.RecordSet;
import software.amazon.awscdk.services.route53.RecordTarget;
import software.amazon.awscdk.services.route53.RecordType;
import software.amazon.awscdk.services.route53.targets.ApiGateway;
import software.constructs.Construct;

import java.io.File;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static com.google.common.base.Preconditions.checkArgument;

public class LambdaBaseStack extends BaseStack {
    private static final String QUARKUS_FUNCTION_PATH = "target/function.zip";
    private static final String QUARKUS_LAMBDA_HANDLER = "io.quarkus.amazon.lambda.runtime.QuarkusStreamHandler::handleRequest";
    protected final SingletonFunction function;
    protected final IHostedZone dnsZone;
    protected final Certificate certificate;
    protected final SpecRestApi restApi;
    protected final RecordSet recordSet;

    public LambdaBaseStack(Construct parent, Options options) {
        this(parent, options, constructOpenApiForApiGateway(options));
    }

    private LambdaBaseStack(Construct parent, Options options, Map<String, Object> openApiSpec) {
        this(parent, options, openApiSpec, getFunctionName(openApiSpec));
    }

    private LambdaBaseStack(Construct parent, Options options, Map<String, Object> openApiSpec, String functionName) {
        super(parent, functionName);
        addApiGatewayExtensionsToOpenapiSpec(openApiSpec, functionName, Optional.empty());
        checkArgument(new File(QUARKUS_FUNCTION_PATH).isFile(), "Asset file doesn't exist: " + QUARKUS_FUNCTION_PATH);

        String stackId = functionName;
        URL serverUrl = getServerUrl(openApiSpec);
        String domain = serverUrl.getHost();
        String baseDomain = InternetDomainName.from(domain).topPrivateDomain().toString();

        dnsZone = HostedZone.fromLookup(this, baseDomain + "-zone", HostedZoneProviderProps.builder()
                .domainName(baseDomain)
                .build());
        certificate = Certificate.Builder.create(this, stackId + "-cert")
                .domainName(domain)
                .validation(CertificateValidation.fromDns(dnsZone))
                .build();
        restApi = SpecRestApi.Builder.create(this, stackId + "-apigateway")
                .apiDefinition(ApiDefinition.fromInline(openApiSpec))
                .domainName(DomainNameOptions.builder()
                        .certificate(certificate)
                        .endpointType(EndpointType.REGIONAL)
                        .domainName(domain)
                        .build())
                .build();
        recordSet = RecordSet.Builder.create(this, stackId + "-recordset")
                .zone(dnsZone)
                .recordType(RecordType.A)
                .recordName(domain)
                .target(RecordTarget.fromAlias(new ApiGateway(restApi)))
                .ttl(Duration.seconds(30))
                .deleteExisting(true)
                .build();
        function = SingletonFunction.Builder.create(this, functionName + "-lambda")
                .uuid(UUID.nameUUIDFromBytes(functionName.getBytes(Charsets.UTF_8)).toString())
                .functionName(functionName)
                .code(Code.fromAsset(QUARKUS_FUNCTION_PATH))
                .handler(QUARKUS_LAMBDA_HANDLER)
                .runtime(Runtime.JAVA_11)
                .architecture(Architecture.ARM_64)
                .memorySize(options.getMemorySize())
                .timeout(Duration.seconds(30))
                .build();
        function.addPermission(functionName + "-gateway-to-lambda-permisson", Permission.builder()
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

    @SneakyThrows
    private static String getFunctionName(Map<String, Object> openApiSpec) {
        return getServerUrl(openApiSpec)
                .getHost()
                .replaceAll("[^a-zA-Z0-9]+", "-");
    }

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
    @SneakyThrows
    private void addApiGatewayExtensionsToOpenapiSpec(Map<String, Object> openApiSpec, String functionName, Optional<String> cognitoUserPoolIdOpt) {
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
                        && "x-api-key".equalsIgnoreCase((String) securityScheme.get("name"))) {
                        // API Key scheme
                        securityScheme.put("x-amazon-apigateway-api-key-source", "header");
                    } else if (cognitoUserPoolIdOpt.isPresent()
                               && securityScheme != null
                               && "apiKey".equals(securityScheme.get("type"))
                               && "header".equals(securityScheme.get("in"))
                               && securityScheme.containsKey("name")
                               && "Authorization".equalsIgnoreCase((String) securityScheme.get("name"))) {
                        // Cognito Authorization
                        securityScheme.putAll(Map.of(
                                "x-amazon-apigateway-authtype", "cognito_user_pools",
                                "x-amazon-apigateway-authorizer", Map.of(
                                        "type", "cognito_user_pools",
                                        "providerARNs", List.of(
                                                "arn:aws:cognito-idp:" + getRegion() + ":" + getAccount() + ":userpool/" + cognitoUserPoolIdOpt.get()))));
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
                        methodData.put("x-amazon-apigateway-integration", ImmutableMap.builder()
                                .put("httpMethod", "POST")
                                .put("uri", "arn:aws:apigateway:" + getRegion() + ":lambda:path/2015-03-31/functions/arn:aws:lambda:" + getRegion() + ":" + getAccount() + ":function:" + functionName + "/invocations")
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
        openApiSpec.put("x-amazon-apigateway-endpoint-configuration", ImmutableMap.of(
                "disableExecuteApiEndpoint", true));
        openApiSpec.put("x-amazon-apigateway-binary-media-types", ImmutableList.of("*/*"));
    }

    @Value
    @lombok.Builder
    public static class Options {
        @NonNull
        String openapiYamlPath;
        @NonNull
        @lombok.Builder.Default
        int memorySize = 512;
    }
}
