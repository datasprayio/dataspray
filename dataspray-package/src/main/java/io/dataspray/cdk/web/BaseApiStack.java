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
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import io.dataspray.authorizer.Authorizer;
import io.dataspray.cdk.dns.DnsStack;
import io.dataspray.cdk.template.BaseStack;
import io.dataspray.common.DeployEnvironment;
import lombok.Getter;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awscdk.Duration;
import software.amazon.awscdk.Fn;
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
import software.amazon.awscdk.services.lambda.Permission;
import software.amazon.awscdk.services.lambda.SingletonFunction;
import software.amazon.awscdk.services.route53.ARecord;
import software.amazon.awscdk.services.route53.AaaaRecord;
import software.amazon.awscdk.services.route53.IHostedZone;
import software.amazon.awscdk.services.route53.RecordSet;
import software.amazon.awscdk.services.route53.RecordTarget;
import software.amazon.awscdk.services.route53.targets.ApiGateway;
import software.constructs.Construct;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.Preconditions.checkState;

@Slf4j
@Getter
public class BaseApiStack extends BaseStack {

    private final String authorizerFunctionName;
    private final SingletonFunction authorizerFunction;
    private final Role roleApiGatewayInvoke;
    private final Certificate certificate;
    private final SpecRestApi restApi;
    private final RecordSet recordSetA;
    private final RecordSet recordSetAaaa;
    private final UsagePlan activeUsagePlan;
    private final Options options;

    public BaseApiStack(Construct parent, Options options) {
        super(parent, "api-gateway", options.getDeployEnv());
        this.options = options;

        authorizerFunctionName = "authorizer" + options.getDeployEnv().getSuffix();
        authorizerFunction = LambdaWebStack.getSingletonFunctionBuilder(
                this,
                getConstructId("lambda"),
                authorizerFunctionName,
                options.getAuthorizerCodeZip(),
                512,
                Authorizer.class.getName() + "::handleRequest");

        roleApiGatewayInvoke = Role.Builder.create(this, getConstructId("role"))
                .roleName(getConstructId("authorizer-role-invoke"))
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
        ImmutableSet<LambdaWebStack> usedWebServices = addApiGatewayExtensionsToOpenapiSpec(openApiSpec);

        // Set domain name for API Gateway
        String fqdn = DnsStack.createFqdn(this, getDeployEnv());
        String apiSubdomain = setServerUrlDomain(openApiSpec, fqdn);
        String apiFqdn = Fn.join(".", List.of(apiSubdomain, fqdn));
        IHostedZone dnsZone = getOptions().getDnsStack().getDnsZone(this, fqdn);

        certificate = Certificate.Builder.create(this, getConstructId("cert"))
                .domainName(apiFqdn)
                .validation(CertificateValidation.fromDns(dnsZone))
                .build();
        restApi = SpecRestApi.Builder.create(this, getConstructId("apigateway"))

                .apiDefinition(ApiDefinition.fromInline(openApiSpec))
                .domainName(DomainNameOptions.builder()
                        .certificate(certificate)
                        .endpointType(EndpointType.REGIONAL)
                        .domainName(apiFqdn)
                        .build())
                .build();
        recordSetA = ARecord.Builder.create(this, getConstructId("recordset-a"))
                .zone(dnsZone)
                .recordName(apiSubdomain)
                .target(RecordTarget.fromAlias(new ApiGateway(restApi)))
                .ttl(Duration.seconds(30))
                .deleteExisting(false)
                .build();
        recordSetAaaa = AaaaRecord.Builder.create(this, getConstructId("recordset-aaaa"))
                .zone(dnsZone)
                .recordName(apiSubdomain)
                .target(RecordTarget.fromAlias(new ApiGateway(restApi)))
                .ttl(Duration.seconds(30))
                .deleteExisting(false)
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
        return UsagePlan.Builder.create(this, getConstructId("usage-plan-" + usagePlanVersion))
                .name("usage-plan-" + usagePlanVersion)
                .apiStages(List.of(UsagePlanPerApiStage.builder()
                        .api(restApi)
                        .stage(restApi.getDeploymentStage()).build()))
                .quota(quota)
                .throttle(throttle)
                .build();
    }

    private void addFunctionToApiGatewayPermission(LambdaWebStack webService) {
        webService.getFunction().addPermission(getConstructId("gateway-to-lambda-permission"), Permission.builder()
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
    private String setServerUrlDomain(Map<String, Object> openApiSpec, String domain) {
        List<Map<String, Object>> servers = (List<Map<String, Object>>) openApiSpec.get("servers");
        checkState(servers != null && servers.size() == 1);
        Map<String, Object> server = servers.get(0);

        String serverUrlStr = (String) server.get("url");
        checkState(serverUrlStr.startsWith("https://"));
        checkState(serverUrlStr.endsWith(".dataspray.io"));

        // Replace the domain in the server URL
        String newServerUrlStr = serverUrlStr.replace("dataspray.io", domain);

        server.put("url", newServerUrlStr);

        String apiSubdomain = serverUrlStr.substring("https://".length(), serverUrlStr.length() - ".dataspray.io".length());
        return apiSubdomain;
    }

    /**
     * <a
     * href="https://docs.aws.amazon.com/apigateway/latest/developerguide/api-gateway-swagger-extensions.html">Documentation
     * on Amazon OpenAPI extensions</a>
     */
    @SuppressWarnings("unchecked")
    @SneakyThrows
    private ImmutableSet<LambdaWebStack> addApiGatewayExtensionsToOpenapiSpec(Map<String, Object> openApiSpec) {
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
        Set<LambdaWebStack> usedWebServices = Sets.newHashSet();
        Map<String, Object> paths = (Map<String, Object>) openApiSpec.get("paths");
        if (paths != null) {
            for (String path : ImmutableSet.copyOf(paths.keySet())) {
                Map<String, Object> methods = (Map<String, Object>) paths.get(path);
                if (methods != null) {
                    for (String method : ImmutableSet.copyOf(methods.keySet())) {
                        Map<String, Object> methodData = (Map<String, Object>) methods.get(method);

                        // Find the tag and corresponding function
                        LambdaWebStack webService;
                        try {
                            String tag = ((List<String>) methodData.get("tags")).getFirst();
                            webService = getOptions().tagToWebService.get(tag);
                        } catch (NullPointerException | ClassCastException ex) {
                            throw new IllegalStateException("Endpoint does not have a tag for path " + path + " and method " + method, ex);
                        }
                        if (webService == null) {
                            throw new IllegalStateException("No function found for path " + path + " and method " + method);
                        }
                        usedWebServices.add(webService);

                        // Add cors headers to responses
                        // Docs https://docs.aws.amazon.com/apigateway/latest/developerguide/enable-cors-for-resource-using-swagger-importer-tool.html
                        Map<String, Object> responses = (Map<String, Object>) methodData.get("responses");
                        if (responses == null) {
                            throw new IllegalStateException("Endpoint does not have a responses for path " + path + " and method " + method);
                        }
                        for (String responseCode : ImmutableSet.copyOf(responses.keySet())) {
                            Map<String, Object> response = (Map<String, Object>) responses.get(responseCode);
                            Map<String, Object> headers = (Map<String, Object>) response.get("headers");
                            if (headers == null) {
                                response.put("headers", headers = Maps.newHashMap());
                            }
                            headers.put("Access-Control-Allow-Origin", ImmutableMap.of(
                                    "schema", ImmutableMap.of(
                                            "type", "string")));
                            headers.put("Access-Control-Allow-Methods", ImmutableMap.of(
                                    "schema", ImmutableMap.of(
                                            "type", "string")));
                            headers.put("Access-Control-Allow-Headers", ImmutableMap.of(
                                    "schema", ImmutableMap.of(
                                            "type", "string")));
                        }

                        // Let Api Gateway know to use this particular lambda function
                        // Docs https://docs.aws.amazon.com/apigateway/latest/developerguide/api-gateway-swagger-extensions-integration.html
                        methodData.put("x-amazon-apigateway-integration", ImmutableMap.builder()
                                .put("httpMethod", "POST")
                                .put("uri", "arn:aws:apigateway:" + getRegion() + ":lambda:path/2015-03-31/functions/arn:aws:lambda:" + getRegion() + ":" + getAccount() + ":function:" + webService.getFunctionName() + "/invocations")
                                .put("responses", ImmutableMap.of(
                                        // Add cors support
                                        // Docs https://docs.aws.amazon.com/apigateway/latest/developerguide/enable-cors-for-resource-using-swagger-importer-tool.html
                                        "default", ImmutableMap.of(
                                                "statusCode", "200",
                                                "responseParameters", ImmutableMap.of(
                                                        "method.response.header.Access-Control-Allow-Headers", "'Content-Type,X-Amz-Date,Authorization,X-Api-Key'",
                                                        "method.response.header.Access-Control-Allow-Methods", "'*'",
                                                        "method.response.header.Access-Control-Allow-Origin", "'*'"),
                                                "responseTemplates", ImmutableMap.of(
                                                        "application/json", "{}"))))
                                .put("passthroughBehavior", "when_no_match")
                                .put("contentHandling", "CONVERT_TO_TEXT")
                                .put("type", "aws_proxy")
                                .build());
                    }
                    if (!methods.containsKey("options")) {
                        // Add CORS support
                        // https://docs.aws.amazon.com/apigateway/latest/developerguide/enable-cors-for-resource-using-swagger-importer-tool.html
                        methods.put("options", ImmutableMap.of(
                                "summary", "CORS support",
                                "description", "Enable CORS by returning correct headers",
                                "tags", ImmutableList.of("Cors"),
                                "security", ImmutableList.of(), // Remove any security from cors endpoints
                                "responses", ImmutableMap.of(
                                        "200", ImmutableMap.of(
                                                "description", "Default response for CORS method",
                                                "headers", ImmutableMap.of(
                                                        "Access-Control-Allow-Origin", ImmutableMap.of(
                                                                "schema", ImmutableMap.of(
                                                                        "type", "string")),
                                                        "Access-Control-Allow-Methods", ImmutableMap.of(
                                                                "schema", ImmutableMap.of(
                                                                        "type", "string")),
                                                        "Access-Control-Allow-Headers", ImmutableMap.of(
                                                                "schema", ImmutableMap.of(
                                                                        "type", "string"))),
                                                "content", ImmutableMap.of())),
                                "x-amazon-apigateway-integration", ImmutableMap.of(
                                        "type", "mock",
                                        // Fix binary data issues
                                        // https://stackoverflow.com/a/63880956
                                        "contentHandling", "CONVERT_TO_TEXT",
                                        "requestTemplates", ImmutableMap.of(
                                                "application/json", "{\"statusCode\":200}"),
                                        "responses", ImmutableMap.of(
                                                "default", ImmutableMap.of(
                                                        "statusCode", "200",
                                                        "responseParameters", ImmutableMap.of(
                                                                "method.response.header.Access-Control-Allow-Headers", "'Content-Type,X-Amz-Date,Authorization,X-Api-Key'",
                                                                "method.response.header.Access-Control-Allow-Methods", "'*'",
                                                                "method.response.header.Access-Control-Allow-Origin", "'*'"),
                                                        "responseTemplates", ImmutableMap.of(
                                                                "application/json", "{}"))))));
                    }
                }
            }
        }
        // Docs https://docs.aws.amazon.com/apigateway/latest/developerguide/api-gateway-swagger-extensions-endpoint-configuration.html
        openApiSpec.put("x-amazon-apigateway-endpoint-configuration", ImmutableMap.of(
                "disableExecuteApiEndpoint", true));
        // Docs https://docs.aws.amazon.com/apigateway/latest/developerguide/api-gateway-swagger-extensions-binary-media-types.html
        openApiSpec.put("x-amazon-apigateway-binary-media-types", ImmutableList.of("*/*"));
        // Docs https://docs.aws.amazon.com/apigateway/latest/developerguide/api-gateway-swagger-extensions-api-key-source.html
        openApiSpec.put("x-amazon-apigateway-api-key-source", "AUTHORIZER");

        return ImmutableSet.copyOf(usedWebServices);
    }

    @Value
    @lombok.Builder
    public static class Options {
        @NonNull
        DeployEnvironment deployEnv;
        @NonNull
        String openapiYamlPath;
        /** Mapping of which function to use for which endpoint (its tag name specifically) */
        @NonNull
        ImmutableMap<String, ? extends LambdaWebStack> tagToWebService;
        @NonNull
        String authorizerCodeZip;
        @NonNull
        DnsStack dnsStack;
    }
}
