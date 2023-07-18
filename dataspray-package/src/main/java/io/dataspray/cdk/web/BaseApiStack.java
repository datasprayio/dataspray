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

package io.dataspray.cdk.web;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import io.dataspray.authorizer.Authorizer;
import io.dataspray.cdk.dns.DnsStack;
import io.dataspray.cdk.template.BaseStack;
import lombok.Getter;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.Value;
import software.amazon.awscdk.Duration;
import software.amazon.awscdk.services.apigateway.ApiDefinition;
import software.amazon.awscdk.services.apigateway.DomainNameOptions;
import software.amazon.awscdk.services.apigateway.EndpointType;
import software.amazon.awscdk.services.apigateway.Period;
import software.amazon.awscdk.services.apigateway.QuotaSettings;
import software.amazon.awscdk.services.apigateway.SpecRestApi;
import software.amazon.awscdk.services.apigateway.ThrottleSettings;
import software.amazon.awscdk.services.apigateway.UsagePlan;
import software.amazon.awscdk.services.apigateway.UsagePlanPerApiStage;
import software.amazon.awscdk.services.certificatemanager.Certificate;
import software.amazon.awscdk.services.certificatemanager.CertificateValidation;
import software.amazon.awscdk.services.iam.Effect;
import software.amazon.awscdk.services.iam.PolicyDocument;
import software.amazon.awscdk.services.iam.PolicyStatement;
import software.amazon.awscdk.services.iam.Role;
import software.amazon.awscdk.services.iam.ServicePrincipal;
import software.amazon.awscdk.services.lambda.Architecture;
import software.amazon.awscdk.services.lambda.Code;
import software.amazon.awscdk.services.lambda.Permission;
import software.amazon.awscdk.services.lambda.Runtime;
import software.amazon.awscdk.services.lambda.SingletonFunction;
import software.amazon.awscdk.services.route53.RecordSet;
import software.amazon.awscdk.services.route53.RecordTarget;
import software.amazon.awscdk.services.route53.RecordType;
import software.amazon.awscdk.services.route53.targets.ApiGateway;
import software.constructs.Construct;

import java.io.File;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class BaseApiStack extends BaseStack {

    @Getter
    private final String authorizerFunctionName;
    @Getter
    private final SingletonFunction authorizerFunction;
    @Getter
    private final Role roleApiGatewayInvoke;
    @Getter
    private final Certificate certificate;
    @Getter
    private final SpecRestApi restApi;
    @Getter
    private final RecordSet recordSet;
    @Getter
    private final UsagePlan activeUsagePlan;
    @Getter
    private final Options options;

    public BaseApiStack(Construct parent, Options options) {
        super(parent, "api-gateway", options.getEnv());
        this.options = options;

        authorizerFunctionName = "authorizer-" + options.getEnv();
        authorizerFunction = SingletonFunction.Builder.create(this, getSubConstructId("lambda"))
                .uuid(UUID.nameUUIDFromBytes(getSubConstructId("lambda").getBytes(Charsets.UTF_8)).toString())
                .functionName(authorizerFunctionName)
                .code(Code.fromAsset(options.getAuthorizerCodeZip()))
                .handler(Authorizer.class.getName() + "::handleRequest")
                .runtime(Runtime.JAVA_11)
                .architecture(Architecture.ARM_64)
                .memorySize(512)
                .timeout(Duration.seconds(30))
                .build();

        roleApiGatewayInvoke = Role.Builder.create(this, getSubConstructId("role"))
                .roleName(getSubConstructId("authorizer-role-invoke"))
                .assumedBy(ServicePrincipal.Builder.create("apigateway.amazonaws.com").build())
                .inlinePolicies(Map.of("allowInvoke", PolicyDocument.Builder.create().statements(List.of(
                        PolicyStatement.Builder.create()
                                .effect(Effect.ALLOW)
                                .actions(List.of("lambda:InvokeFunction", "lambda:InvokeAsync"))
                                .resources(List.of(authorizerFunction.getFunctionArn()))
                                .resources(List.of("arn:aws:lambda:" + getRegion() + ":" + getAccount() + ":function:" + authorizerFunctionName))
                                .build())).build())).build();

        Map<String, Object> openApiSpec = constructOpenApiForApiGateway();
        // Add Lambda endpoints to OpenAPI spec
        ImmutableSet<BaseLambdaWebServiceStack> usedWebServices = addApiGatewayExtensionsToOpenapiSpec(openApiSpec);

        // Set domain name for API Gateway
        String rootDomain = getOptions().getDnsStack().getDomainParam().getValueAsString();
        URL serverUrl = setServerUrlDomain(openApiSpec, rootDomain);
        String apiDomain = serverUrl.getHost();

        certificate = Certificate.Builder.create(this, getSubConstructId("cert"))
                .domainName(apiDomain)
                .validation(CertificateValidation.fromDns(getOptions().getDnsStack().getDnsZone()))
                .build();
        restApi = SpecRestApi.Builder.create(this, getSubConstructId("apigateway"))
                .apiDefinition(ApiDefinition.fromInline(openApiSpec))
                .domainName(DomainNameOptions.builder()
                        .certificate(certificate)
                        .endpointType(EndpointType.REGIONAL)
                        .domainName(apiDomain)
                        .build())
                .build();
        recordSet = RecordSet.Builder.create(this, getSubConstructId("recordset"))
                .zone(getOptions().getDnsStack().getDnsZone())
                .recordType(RecordType.A)
                .recordName(apiDomain)
                .target(RecordTarget.fromAlias(new ApiGateway(restApi)))
                .ttl(Duration.seconds(30))
                .deleteExisting(true)
                .build();

        // If changing, keep the old one as well as existing accounts may already point to it
        activeUsagePlan = createUsagePlan(1,
                QuotaSettings.builder()
                        .limit(1000)
                        .offset(0) // TODO What does this mean?? AWS is missing documentation
                        .period(Period.DAY).build(),
                ThrottleSettings.builder()
                        .rateLimit(10)
                        .burstLimit(10).build());

        usedWebServices.forEach(this::addFunctionToApiGatewayPermission);
    }

    public UsagePlan createUsagePlan(long usagePlanVersion, QuotaSettings quota, ThrottleSettings throttle) {
        return UsagePlan.Builder.create(this, getSubConstructId("usage-plan-" + usagePlanVersion))
                .name("usage-plan-" + usagePlanVersion)
                .apiStages(List.of(UsagePlanPerApiStage.builder()
                        .api(restApi)
                        .stage(restApi.getDeploymentStage()).build()))
                .quota(quota)
                .throttle(throttle)
                .build();
    }

    private void addFunctionToApiGatewayPermission(BaseLambdaWebServiceStack webService) {
        webService.getFunction().addPermission(getSubConstructId("gateway-to-lambda-permission"), Permission.builder()
                .sourceArn(restApi.arnForExecuteApi())
                .principal(ServicePrincipal.Builder
                        .create("apigateway.amazonaws.com").build())
                .action("lambda:InvokeFunction")
                .build());
    }

    @SneakyThrows
    private Map<String, Object> constructOpenApiForApiGateway() {
        return new ObjectMapper(new YAMLFactory()).readValue(new File(getOptions().getOpenapiYamlPath()), new TypeReference<Map<String, Object>>() {
        });
    }

    @SuppressWarnings("unchecked")
    @SneakyThrows
    private URL setServerUrlDomain(Map<String, Object> openApiSpec, String domain) {
        List<Map<String, Object>> servers = (List<Map<String, Object>>) openApiSpec.get("servers");
        Preconditions.checkState(servers != null && servers.size() == 1);
        Map<String, Object> server = servers.get(0);
        String serverUrlStr = (String) server.get("url");

        // Replace the domain in the server URL
        String newServerUrlStr = serverUrlStr.replace("dataspray.io", domain);

        server.put("url", newServerUrlStr);
        return new URL(newServerUrlStr);
    }

    /**
     * <a
     * href="https://docs.aws.amazon.com/apigateway/latest/developerguide/api-gateway-swagger-extensions.html">Documentation
     * on Amazon OpenAPI extensions</a>
     */
    @SuppressWarnings("unchecked")
    @SneakyThrows
    private ImmutableSet<BaseLambdaWebServiceStack> addApiGatewayExtensionsToOpenapiSpec(Map<String, Object> openApiSpec) {
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
                                        "authorizerCredentials", getRoleApiGatewayInvoke().getRoleArn(),
                                        "authorizerUri", "arn:aws:apigateway:" + getRegion() + ":lambda:path/2015-03-31/functions/arn:aws:lambda:" + getRegion() + ":" + getAccount() + ":function:" + getAuthorizerFunctionName() + "/invocations",

                                        "authorizerResultTtlInSeconds", TimeUnit.MINUTES.toSeconds(5))));
                    }
                }
            }
        }
        Set<BaseLambdaWebServiceStack> usedWebServices = Sets.newHashSet();
        Map<String, Object> paths = (Map<String, Object>) openApiSpec.get("paths");
        if (paths != null) {
            for (String path : ImmutableSet.copyOf(paths.keySet())) {
                Map<String, Object> methods = (Map<String, Object>) paths.get(path);
                if (methods != null) {
                    for (String method : ImmutableSet.copyOf(methods.keySet())) {
                        Map<String, Object> methodData = (Map<String, Object>) methods.get(method);

                        // Find the tag and corresponding function
                        BaseLambdaWebServiceStack webService;
                        try {
                            String tag = ((List<String>) methodData.get("tags")).get(0);
                            webService = getOptions().tagToWebService.get(tag);
                        } catch (NullPointerException | ClassCastException ex) {
                            throw new RuntimeException("Endpoint does not have a tag for path " + path + " and method " + method, ex);
                        }
                        if (webService == null) {
                            throw new RuntimeException("No function found for path " + path + " and method " + method);
                        }
                        usedWebServices.add(webService);

                        // Let Api Gateway know to use this particular lambda function
                        // Docs https://docs.aws.amazon.com/apigateway/latest/developerguide/api-gateway-swagger-extensions-integration.html
                        methodData.put("x-amazon-apigateway-integration", ImmutableMap.builder()
                                .put("httpMethod", "POST")
                                .put("uri", "arn:aws:apigateway:" + getRegion() + ":lambda:path/2015-03-31/functions/arn:aws:lambda:" + getRegion() + ":" + getAccount() + ":function:" + webService.getFunctionName() + "/invocations")
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

        return ImmutableSet.copyOf(usedWebServices);
    }

    @Value
    @lombok.Builder
    public static class Options {
        @NonNull
        String env;
        @NonNull
        String openapiYamlPath;
        /** Mapping of which function to use for which endpoint (its tag name specifically) */
        @NonNull
        ImmutableMap<String, ? extends BaseLambdaWebServiceStack> tagToWebService;
        @NonNull
        String authorizerCodeZip;
        @NonNull
        DnsStack dnsStack;
    }
}
