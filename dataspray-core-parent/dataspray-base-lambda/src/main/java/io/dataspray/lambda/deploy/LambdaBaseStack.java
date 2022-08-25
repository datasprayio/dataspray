package io.dataspray.lambda.deploy;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.net.InternetDomainName;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.Value;
import software.amazon.awscdk.services.apigateway.ApiDefinition;
import software.amazon.awscdk.services.apigateway.DomainNameOptions;
import software.amazon.awscdk.services.apigateway.EndpointType;
import software.amazon.awscdk.services.apigateway.SpecRestApi;
import software.amazon.awscdk.services.certificatemanager.Certificate;
import software.amazon.awscdk.services.certificatemanager.CertificateValidation;
import software.amazon.awscdk.services.lambda.Architecture;
import software.amazon.awscdk.services.lambda.Code;
import software.amazon.awscdk.services.lambda.Handler;
import software.amazon.awscdk.services.lambda.Runtime;
import software.amazon.awscdk.services.lambda.SingletonFunction;
import software.amazon.awscdk.services.route53.HostedZone;
import software.constructs.Construct;

import java.io.File;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.google.common.base.Preconditions.checkArgument;

public class LambdaBaseStack extends BaseStack {
    private static final String QUARKUS_FUNCTION_PATH = "target/function.zip";
    protected final SingletonFunction function;
    protected final HostedZone dnsZone;
    protected final Certificate certificate;
    protected final SpecRestApi gateway;

    public LambdaBaseStack(Options options) {
        this(createApp(), options);
    }

    public LambdaBaseStack(Construct parent, Options options) {
        this(parent, options, constructOpenApiForApiGateway(options));
    }

    private LambdaBaseStack(Construct parent, Options options, Map<String, Object> openApiSpec) {
        this(parent, options, openApiSpec, getFunctionName(openApiSpec));
    }

    private LambdaBaseStack(Construct parent, Options options, Map<String, Object> openApiSpec, String functionName) {
        super(parent, functionName);
        addApiGatewayExtensionsToOpenapiSpec(openApiSpec, functionName);

        checkArgument(new File(QUARKUS_FUNCTION_PATH).isFile(), "Asset file doesn't exist: " + QUARKUS_FUNCTION_PATH);

        function = SingletonFunction.Builder.create(this, functionName + "-lambda")
                .uuid(UUID.nameUUIDFromBytes(functionName.getBytes(Charsets.UTF_8)).toString())
                .functionName(functionName)
                .code(Code.fromAsset(QUARKUS_FUNCTION_PATH))
                .handler(Handler.FROM_IMAGE)
                .runtime(Runtime.FROM_IMAGE)
                .allowAllOutbound(true)
                .architecture(Architecture.ARM_64)
                .memorySize(options.getMemorySize())
                .build();

        URL serverUrl = getServerUrl(openApiSpec);
        String domain = serverUrl.getHost();
        String baseDomain = InternetDomainName.from(domain).topPrivateDomain().toString();

        dnsZone = HostedZone.Builder.create(this, getStackId() + "-zone")
                .zoneName(baseDomain)
                .build();

        certificate = Certificate.Builder.create(this, getStackId() + "-cert")
                .domainName(domain)
                .validation(CertificateValidation.fromDns(dnsZone))
                .build();
        gateway = SpecRestApi.Builder.create(this, getStackId() + "-apigateway")
                .apiDefinition(ApiDefinition.fromInline(openApiSpec))
                .domainName(DomainNameOptions.builder()
                        .certificate(certificate)
                        .endpointType(EndpointType.REGIONAL)
                        .domainName(domain)
                        .build())
                .build();
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

    @SneakyThrows
    private void addApiGatewayExtensionsToOpenapiSpec(Map<String, Object> openApiSpec, String functionName) {
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
        openApiSpec.put("x-amazon-apigateway-binary-media-types", ImmutableList.of("application/zip"));
    }

    @Value
    @lombok.Builder
    public static class Options {
        @NonNull
        String openapiYamlPath;
        @NonNull
        @lombok.Builder.Default
        int memorySize = 128;
    }
}
