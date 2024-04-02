/*
 * Copyright 2024 Matus Faro
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

package io.dataspray.cdk.api;

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
import io.dataspray.cdk.store.SingleTableStack;
import io.dataspray.cdk.template.BaseStack;
import io.dataspray.cdk.template.FunctionStack;
import io.dataspray.common.DeployEnvironment;
import io.dataspray.store.ApiAccessStore.UsageKeyType;
import io.dataspray.store.impl.DynamoApiGatewayApiAccessStore;
import lombok.Getter;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awscdk.Duration;
import software.amazon.awscdk.Fn;
import software.amazon.awscdk.services.apigateway.ApiDefinition;
import software.amazon.awscdk.services.apigateway.ApiKey;
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
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.Preconditions.checkState;

@Slf4j
@Getter
public class ApiStack extends FunctionStack {

    private final String openApiServerUrl;
    private final FunctionAndAlias authorizerFunction;
    private final Role roleApiGatewayInvoke;
    private final Certificate certificate;
    private final SpecRestApi restApi;
    private final RecordSet recordSetA;
    private final RecordSet recordSetAaaa;
    private final UsagePlan usagePlanUnlimited;
    private final UsagePlan usagePlanGlobal;
    private final UsagePlan usagePlanOrganization;
    private final ApiKey apiKeyUnlimited;
    private final ApiKey apiKeyGlobal;
    private final Options options;

    public ApiStack(Construct parent, Options options) {
        super(parent, "api-gateway", options.getDeployEnv());
        this.options = options;

        authorizerFunction = addSingletonFunction(
                getConstructId("lambda"),
                "authorizer",
                options.getAuthorizerCodeZip(),
                512,
                128);
        authorizerFunction.getFunction().addToRolePolicy(PolicyStatement.Builder.create()
                .sid(getConstructIdCamelCase("SingleTable"))
                .effect(Effect.ALLOW)
                .actions(ImmutableList.of(
                        "dynamodb:GetItem",
                        "dynamodb:BatchGetItem",
                        "dynamodb:Query",
                        "dynamodb:PutItem",
                        "dynamodb:UpdateItem",
                        "dynamodb:BatchWriteItem",
                        "dynamodb:DeleteItem"))
                .resources(ImmutableList.of(
                        options.getSingleTableStack().getSingleTableTable().getTableArn()))
                .build());

        roleApiGatewayInvoke = Role.Builder.create(this, getConstructId("role"))
                .roleName(getConstructId("authorizer-role-invoke"))
                .assumedBy(ServicePrincipal.Builder.create("apigateway.amazonaws.com").build())
                .inlinePolicies(Map.of("allowInvoke", PolicyDocument.Builder.create().statements(List.of(
                        PolicyStatement.Builder.create()
                                .effect(Effect.ALLOW)
                                .actions(List.of("lambda:InvokeFunction", "lambda:InvokeAsync"))
                                .resources(List.of(authorizerFunction.getAlias().getFunctionArn()))
                                .resources(List.of("arn:aws:lambda:" + getRegion() + ":" + getAccount() + ":function:" + authorizerFunction.getFunctionName() + ":" + getAuthorizerFunction().getAliasName()))
                                .build())).build())).build();

        Map<String, Object> openApiSpec = constructOpenApiForApiGateway();
        // Add Lambda endpoints to OpenAPI spec
        ImmutableSet<ApiFunctionStack> usedWebServices = addApiGatewayExtensionsToOpenapiSpec(openApiSpec);

        // Fetch server url from spec
        Map<String, Object> serverObj = getServerObj(openApiSpec);
        openApiServerUrl = getServerUrl(serverObj);

        // Replace the domain in the server URL
        String fqdn = DnsStack.createFqdn(this, getDeployEnv());
        String serverUrl = openApiServerUrl.replace("dataspray.io", fqdn);
        serverObj.put("url", serverUrl);

        // Construct API subdomain and fqdn
        String apiFqdn = Fn.join(".", List.of(getApiSubdomain(openApiServerUrl), fqdn));
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
                // Trailing dot to fix https://github.com/aws/aws-cdk/issues/26572
                .recordName(apiFqdn + ".")
                .target(RecordTarget.fromAlias(new ApiGateway(restApi)))
                .ttl(Duration.seconds(30))
                .deleteExisting(false)
                .build();
        recordSetAaaa = AaaaRecord.Builder.create(this, getConstructId("recordset-aaaa"))
                .zone(dnsZone)
                // Trailing dot to fix https://github.com/aws/aws-cdk/issues/26572
                .recordName(apiFqdn + ".")
                .target(RecordTarget.fromAlias(new ApiGateway(restApi)))
                .ttl(Duration.seconds(30))
                .deleteExisting(false)
                .build();

        // If changing, keep the old one as well as existing accounts may already point to it
        usagePlanUnlimited = createUsagePlan(restApi, UsageKeyType.UNLIMITED, 1,
                Optional.empty(),
                Optional.empty());
        usagePlanGlobal = createUsagePlan(restApi, UsageKeyType.GLOBAL, 1,
                Optional.empty(),
                Optional.of(ThrottleSettings.builder()
                        .rateLimit(100)
                        .burstLimit(10).build()));
        usagePlanOrganization = createUsagePlan(restApi, UsageKeyType.ORGANIZATION, 1,
                Optional.of(QuotaSettings.builder()
                        .limit(1000)
                        .offset(0)
                        .period(Period.DAY).build()),
                Optional.of(ThrottleSettings.builder()
                        .rateLimit(10)
                        .burstLimit(10).build()));


        String usageKeyApiKeyUnlimited = DynamoApiGatewayApiAccessStore.getUsageKeyApiKey(getDeployEnv(), UsageKeyType.UNLIMITED, Optional.empty(), ImmutableSet.of());
        apiKeyUnlimited = ApiKey.Builder.create(this, getConstructId(usageKeyApiKeyUnlimited))
                .apiKeyName(usageKeyApiKeyUnlimited)
                .value(usageKeyApiKeyUnlimited)
                .enabled(true)
                .build();
        usagePlanUnlimited.addApiKey(apiKeyUnlimited);

        String usageKeyApiKeyGlobal = DynamoApiGatewayApiAccessStore.getUsageKeyApiKey(getDeployEnv(), UsageKeyType.GLOBAL, Optional.empty(), ImmutableSet.of());
        apiKeyGlobal = ApiKey.Builder.create(this, getConstructId(usageKeyApiKeyGlobal))
                .apiKeyName(usageKeyApiKeyGlobal)
                .value(usageKeyApiKeyGlobal)
                .enabled(true)
                .build();
        usagePlanGlobal.addApiKey(apiKeyGlobal);

        usedWebServices.forEach(webService -> addFunctionToApiGatewayPermission(restApi, webService));
    }

    public UsagePlan createUsagePlan(SpecRestApi restApi, UsageKeyType type, long version, Optional<QuotaSettings> quotaOpt, Optional<ThrottleSettings> throttleOpt) {
        String name = "usage-plan-" + type.name() + "-v" + version + getDeployEnv().getSuffix();
        UsagePlan.Builder builder = UsagePlan.Builder.create(this, getConstructId("usage-plan-" + name))
                .name("usage-plan-" + name)
                .apiStages(List.of(UsagePlanPerApiStage.builder()
                        // TODO Add method-level throttling here: .throttle(...)
                        .api(restApi)
                        .stage(restApi.getDeploymentStage()).build()));
        quotaOpt.ifPresent(builder::quota);
        throttleOpt.ifPresent(builder::throttle);
        return builder.build();
    }

    private void addFunctionToApiGatewayPermission(SpecRestApi restApi, ApiFunctionStack webService) {
        webService.getApiFunction().getFunction().addPermission(getConstructId("gateway-to-lambda-permission"), Permission.builder()
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
    private Map<String, Object> getServerObj(Map<String, Object> openApiSpec) {
        List<Map<String, Object>> servers = (List<Map<String, Object>>) openApiSpec.get("servers");
        checkState(servers != null && servers.size() == 1);
        return servers.getFirst();
    }

    private String getServerUrl(Map<String, Object> serverObj) {
        String serverUrl = (String) serverObj.get("url");
        checkState(serverUrl.startsWith("https://"));
        checkState(serverUrl.endsWith(".dataspray.io"));
        return serverUrl;
    }

    private String getApiSubdomain(String serverUrl) {
        return serverUrl.substring("https://".length(), serverUrl.length() - ".dataspray.io".length());
    }

    private String getApiFqdn(String apiSubdomain, String fqdn) {
        return Fn.join(".", List.of(apiSubdomain, fqdn));
    }

    /**
     * The public getter has to re-construct the API fqdn as cross-stack usage of Conditions has a bug in CDK.
     * See note on {@link DnsStack#createFqdn(BaseStack, DeployEnvironment)}.
     */
    public String getApiFqdn(final BaseStack stack) {
        String fqdn = DnsStack.createFqdn(stack, getDeployEnv());
        String apiSubdomain = getApiSubdomain(openApiServerUrl);
        return getApiFqdn(apiSubdomain, fqdn);
    }

    /**
     * <a
     * href="https://docs.aws.amazon.com/apigateway/latest/developerguide/api-gateway-swagger-extensions.html">Documentation
     * on Amazon OpenAPI extensions</a>
     */
    @SuppressWarnings("unchecked")
    @SneakyThrows
    private ImmutableSet<ApiFunctionStack> addApiGatewayExtensionsToOpenapiSpec(Map<String, Object> openApiSpec) {
        boolean authorizerSecuritySchemeFound = false;
        Map<String, Object> components = (Map<String, Object>) openApiSpec.get("components");
        if (components != null) {
            Map<String, Object> securitySchemes = (Map<String, Object>) components.get("securitySchemes");
            if (securitySchemes != null) {
                for (String securitySchemeName : ImmutableSet.copyOf(securitySchemes.keySet())) {
                    Map<String, Object> securityScheme = (Map<String, Object>) securitySchemes.get(securitySchemeName);
                    if (securityScheme != null
                        && "Authorizer".equals(securitySchemeName)
                        && "apiKey".equals(securityScheme.get("type"))
                        && "header".equals(securityScheme.get("in"))
                        && securityScheme.containsKey("name")
                        && Authorizer.AUTHORIZATION_HEADER.equalsIgnoreCase((String) securityScheme.get("name"))) {
                        authorizerSecuritySchemeFound = true;
                        // Cognito Authorization
                        securityScheme.putAll(Map.of(
                                // Docs https://docs.aws.amazon.com/apigateway/latest/developerguide/api-gateway-swagger-extensions-authtype.html
                                "x-amazon-apigateway-authtype", "custom",
                                // Docs https://docs.aws.amazon.com/apigateway/latest/developerguide/api-gateway-swagger-extensions-authorizer.html
                                "x-amazon-apigateway-authorizer", Map.of(
                                        // Difference between "request" and "token": https://docs.aws.amazon.com/apigateway/latest/developerguide/api-gateway-lambda-authorizer-input.html
                                        "type", "request",
                                        // If this source is not present, AG returns 401 without even calling our Authorizer, see: https://docs.aws.amazon.com/apigateway/latest/developerguide/http-api-lambda-authorizer.html#http-api-lambda-authorizer.identity-sources
                                        "identitySource", "method.request.header." + Authorizer.AUTHORIZATION_HEADER.toLowerCase(),
                                        "authorizerCredentials", getRoleApiGatewayInvoke().getRoleArn(),
                                        "authorizerUri", "arn:aws:apigateway:" + getRegion() + ":lambda:path/2015-03-31/functions/arn:aws:lambda:" + getRegion() + ":" + getAccount() + ":function:" + getAuthorizerFunction().getFunctionName() + ":" + getAuthorizerFunction().getAliasName() + "/invocations",
                                        "authorizerResultTtlInSeconds", TimeUnit.MINUTES.toSeconds(5))));
                    }
                }
            }
        }
        if (!authorizerSecuritySchemeFound) {
            throw new IllegalStateException("OpenAPI spec does not contain a valid security scheme for the Authorizer");
        }
        Set<ApiFunctionStack> usedWebServices = Sets.newHashSet();
        Map<String, Object> paths = (Map<String, Object>) openApiSpec.get("paths");
        if (paths != null) {
            for (String path : ImmutableSet.copyOf(paths.keySet())) {
                Map<String, Object> methods = (Map<String, Object>) paths.get(path);
                if (methods != null) {
                    Optional<ApiFunctionStack> endpointFunctionOpt = Optional.empty();
                    for (String method : ImmutableSet.copyOf(methods.keySet())) {
                        Map<String, Object> methodData = (Map<String, Object>) methods.get(method);

                        // Find tag for endpoint
                        String tag;
                        try {
                            tag = ((List<String>) methodData.get("tags")).getFirst();
                        } catch (NoSuchElementException | NullPointerException | ClassCastException ex) {
                            throw new IllegalStateException("Endpoint does not have a tag for path " + path + " and method " + method, ex);
                        }

                        // Find the corresponding function (by tag)
                        final ApiFunctionStack endpointFunction;
                        if (endpointFunctionOpt.isEmpty()) {
                            endpointFunction = getOptions().getApiFunctions().stream()
                                    .filter(f -> f.getApiTags().contains(tag))
                                    .findAny()
                                    .orElseThrow(() -> new IllegalStateException("No function found for path " + path + " and method " + method));
                            endpointFunctionOpt = Optional.of(endpointFunction);
                            usedWebServices.add(endpointFunction);
                        } else {
                            endpointFunction = endpointFunctionOpt.get();
                            if (!endpointFunction.getApiTags().contains(tag)) {
                                throw new IllegalStateException("Having different tags under same path " + path + " is not supported since we have a single Cors OPTIONS method");
                            }
                        }

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
                                .put("uri", "arn:aws:apigateway:" + getRegion() + ":lambda:path/2015-03-31/functions/arn:aws:lambda:" + getRegion() + ":" + getAccount() + ":function:" + endpointFunction.getApiFunction().getFunctionName() + ":" + getAuthorizerFunction().getAliasName() + "/invocations")
                                .put("responses", ImmutableMap.of(
                                        // Add cors support
                                        // Docs https://docs.aws.amazon.com/apigateway/latest/developerguide/enable-cors-for-resource-using-swagger-importer-tool.html
                                        "default", ImmutableMap.of(
                                                "statusCode", "200",
                                                "responseParameters", ImmutableMap.of(
                                                        "method.response.header.Access-Control-Allow-Headers", "'" + ApiFunctionStack.CORS_ALLOW_HEADERS + "'",
                                                        "method.response.header.Access-Control-Allow-Methods", "'" + ApiFunctionStack.CORS_ALLOW_METHODS + "'",
                                                        "method.response.header.Access-Control-Allow-Origin", "'" + endpointFunction.getCorsAllowOrigins(this) + "'"),
                                                "responseTemplates", ImmutableMap.of(
                                                        "application/json", "{}"))))
                                .put("passthroughBehavior", "when_no_match")
                                .put("contentHandling", "CONVERT_TO_TEXT")
                                .put("type", "aws_proxy")
                                .build());
                    }
                    if (!methods.containsKey("options") && endpointFunctionOpt.isPresent()) {
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
                                        "contentHandling", "CONVERT_TO_TEXT", // Fix binary data issues https://stackoverflow.com/a/63880956
                                        "requestTemplates", ImmutableMap.of(
                                                "application/json", "{\"statusCode\":200}"),
                                        "responses", ImmutableMap.of(
                                                "default", ImmutableMap.of(
                                                        "statusCode", "200",
                                                        "contentHandling", "CONVERT_TO_TEXT", // Fix binary data issues https://stackoverflow.com/a/63880956
                                                        "responseParameters", ImmutableMap.of(
                                                                "method.response.header.Access-Control-Allow-Headers", "'" + ApiFunctionStack.CORS_ALLOW_HEADERS + "'",
                                                                "method.response.header.Access-Control-Allow-Methods", "'" + ApiFunctionStack.CORS_ALLOW_METHODS + "'",
                                                                "method.response.header.Access-Control-Allow-Origin", "'" + endpointFunctionOpt.get().getCorsAllowOrigins(this) + "'"),
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
        openApiSpec.put("x-amazon-apigateway-binary-media-types", ImmutableList.of("application/octet-stream"));
        // Docs https://docs.aws.amazon.com/apigateway/latest/developerguide/api-gateway-swagger-extensions-api-key-source.html
        openApiSpec.put("x-amazon-apigateway-api-key-source", "AUTHORIZER");

        // Docs https://docs.aws.amazon.com/apigateway/latest/developerguide/api-gateway-request-validation-sample-api-swagger.html
        openApiSpec.put("x-amazon-apigateway-request-validators", ImmutableMap.of(
                "all", ImmutableMap.of(
                        "validateRequestBody", true,
                        "validateRequestParameters", true)));
        openApiSpec.put("x-amazon-apigateway-request-validator", "all");

        return ImmutableSet.copyOf(usedWebServices);
    }

    @Value
    @lombok.Builder
    public static class Options {
        @NonNull
        DeployEnvironment deployEnv;
        @NonNull
        String openapiYamlPath;
        /** Functions to include in the API, linked by the OpenAPI endpoint tag and the ApiFunctionStack.apiTags */
        @NonNull
        ImmutableSet<ApiFunctionStack> apiFunctions;
        @NonNull
        String authorizerCodeZip;
        @NonNull
        DnsStack dnsStack;
        @NonNull
        SingleTableStack singleTableStack;
    }
}
