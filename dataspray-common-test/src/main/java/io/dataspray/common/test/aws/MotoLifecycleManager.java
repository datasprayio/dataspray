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

package io.dataspray.common.test.aws;

import com.google.common.collect.ImmutableMap;
import io.dataspray.common.NetworkUtil;
import io.dataspray.common.test.TestResourceUtil;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.Extension;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.testcontainers.containers.FixedHostPortGenericContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.InternetProtocol;
import org.testcontainers.containers.wait.strategy.LogMessageWaitStrategy;
import software.amazon.awssdk.core.SdkSystemSetting;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AliasAttributeType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AttributeDataType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.CreateUserPoolClientRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.CreateUserPoolRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.PasswordPolicyType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.SchemaAttributeType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserPoolClientType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserPoolMfaType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserPoolPolicyType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserPoolType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.VerifiedAttributeType;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static com.google.common.base.Preconditions.checkState;

/**
 * Moto container with dual-mode support for running:
 * 1/ as a Quarkus test resource using `@QuarkusTestResource(MotoLifecycleManager.class)`
 * 2/ as an extension to Jupiter tests using `@ExtendWith(MotoLifecycleManager.class)`
 */
@Slf4j
public class MotoLifecycleManager implements QuarkusTestResourceLifecycleManager, Extension, BeforeAllCallback, AfterAllCallback, BeforeEachCallback {

    public static final String CREATE_COGNITO_PARAM = "create-cognito";
    private static final String MOTO_VERSION = "5.0.26";

    private Optional<MotoInstance> instanceOpt = Optional.empty();
    private boolean createCognito = false;

    @Override
    public void init(Map<String, String> initArgs) {
        createCognito = initArgs.getOrDefault(CREATE_COGNITO_PARAM, "false").equals("true");
    }

    /** Jupiter wrapper when used with {@link ExtendWith} */
    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        start();
    }

    /** Jupiter wrapper when used with {@link ExtendWith} */
    @Override
    public void beforeEach(ExtensionContext context) throws Exception {
        inject(context.getRequiredTestInstance());
    }

    /** Jupiter wrapper when used with {@link ExtendWith} */
    @Override
    public void afterAll(ExtensionContext context) throws Exception {
        stop();
    }

    /** Quarkus wrapper when used with {@link QuarkusTestResource} */
    @Override
    public Map<String, String> start() {
        String region = "us-east-1";
        long awsAccountId = 100000000001L;
        String awsAccessKey = UUID.randomUUID().toString();
        String awsSecretKey = UUID.randomUUID().toString();
        // Setup Moto container
        int port = NetworkUtil.get().findFreePort();
        String endpoint = "http://localhost:" + port;
        GenericContainer<?> motoContainer = new FixedHostPortGenericContainer<>(
                "motoserver/moto:" + MOTO_VERSION)
                .withFixedExposedPort(port, port, InternetProtocol.TCP)
                .withEnv("AWS_ACCESS_KEY_ID", awsAccessKey)
                .withEnv("AWS_SECRET_ACCESS_KEY", awsSecretKey)
                .withEnv("AWS_DEFAULT_REGION", region)
                .withEnv("MOTO_PORT", String.valueOf(port))
                .withEnv("MOTO_ACCOUNT_ID", String.valueOf(awsAccountId))
                .withLogConsumer(frame -> log.info("{}", frame.getUtf8StringWithoutLineEnding()));
        instanceOpt = Optional.of(new MotoInstance(
                awsAccountId,
                region,
                awsAccessKey,
                awsSecretKey,
                port,
                endpoint,
                motoContainer));
        motoContainer.waitingFor(new LogMessageWaitStrategy()
                .withRegEx(".*Running on all addresses.*")
                .withStartupTimeout(Duration.ofMinutes(1)));

        // Start container
        motoContainer.start();

        // Properties via Java
        System.setProperty("aws.endpointUrl", endpoint);
        System.setProperty(SdkSystemSetting.AWS_ACCOUNT_ID.property(), String.valueOf(awsAccountId));
        System.setProperty(SdkSystemSetting.AWS_REGION.property(), region);
        System.setProperty(SdkSystemSetting.AWS_ACCESS_KEY_ID.property(), awsAccessKey);
        System.setProperty(SdkSystemSetting.AWS_SECRET_ACCESS_KEY.property(), awsSecretKey);

        // Common properties
        ImmutableMap.Builder<String, String> propsBuilder = ImmutableMap.builder();
        propsBuilder.put("startupWaitUntilDeps", "true");
        propsBuilder.put("aws.endpointUrl", endpoint);
        propsBuilder.put("aws.accountId", String.valueOf(awsAccountId));
        propsBuilder.put("aws.region", region);
        propsBuilder.put("aws.credentials.accessKey", awsAccessKey);
        propsBuilder.put("aws.credentials.secretKey", awsSecretKey);

        // Dynamo properties
        propsBuilder.put("aws.dynamo.serviceEndpoint", endpoint);
        propsBuilder.put("aws.dynamo.productionRegion", region);
        propsBuilder.put("singletable.createTableOnStartup", "true");

        // S3 properties
        propsBuilder.put("aws.s3.serviceEndpoint", endpoint);
        propsBuilder.put("aws.s3.productionRegion", region);
        propsBuilder.put("aws.s3.pathStyleEnabled", "true");

        // API Gateway properties
        propsBuilder.put("aws.apigateway.serviceEndpoint", endpoint);
        propsBuilder.put("aws.apigateway.productionRegion", region);

        // Athena properties
        propsBuilder.put("aws.athena.serviceEndpoint", endpoint);
        propsBuilder.put("aws.athena.productionRegion", region);

        // Cognito properties
        propsBuilder.put("aws.cognito.serviceEndpoint", endpoint);
        propsBuilder.put("aws.cognito.productionRegion", region);

        // Firehose properties
        propsBuilder.put("aws.firehose.serviceEndpoint", endpoint);
        propsBuilder.put("aws.firehose.productionRegion", region);

        // Glue properties
        propsBuilder.put("aws.glue.serviceEndpoint", endpoint);
        propsBuilder.put("aws.glue.productionRegion", region);

        // Lambda properties
        propsBuilder.put("aws.lambda.serviceEndpoint", endpoint);
        propsBuilder.put("aws.lambda.productionRegion", region);

        // SQS properties
        propsBuilder.put("aws.sqs.serviceEndpoint", endpoint);
        propsBuilder.put("aws.sqs.productionRegion", region);

        // IAM properties
        propsBuilder.put("aws.iam.serviceEndpoint", endpoint);
        propsBuilder.put("aws.iam.productionRegion", region);

        if (createCognito) {
            propsBuilder.putAll(setupCognito(instanceOpt.get().getCognitoClient()));
        }

        return propsBuilder.build();
    }

    /** Quarkus wrapper when used with {@link QuarkusTestResource} */
    @Override
    public void stop() {
        instanceOpt
                .map(MotoInstance::getMotoContainer)
                .ifPresent(GenericContainer::stop);
    }

    /**
     * Injects an instance of {@link MotoInstance} into test
     * Quarkus wrapper when used with {@link QuarkusTestResource}
     */
    @Override
    public void inject(Object testInstance) {
        checkState(instanceOpt.isPresent());
        TestResourceUtil.injectSelf(testInstance, instanceOpt.get());
    }

    private Map<String, String> setupCognito(CognitoIdentityProviderClient cognitoClient) {
        // Should match the same configuration as in AuthNzStack
        UserPoolType userPool = cognitoClient.createUserPool(CreateUserPoolRequest.builder()
                        .poolName("userpool-" + UUID.randomUUID())
                        .mfaConfiguration(UserPoolMfaType.OPTIONAL)
                        .autoVerifiedAttributes(VerifiedAttributeType.EMAIL)
                        .aliasAttributes(AliasAttributeType.EMAIL, AliasAttributeType.PREFERRED_USERNAME)
                        .schema(
                                SchemaAttributeType.builder()
                                        .attributeDataType(AttributeDataType.BOOLEAN)
                                        .name(/* CognitoUserStore.USER_ATTRIBUTE_TOS_AGREED */ "tos-agreed")
                                        .mutable(true).build(),
                                SchemaAttributeType.builder()
                                        .attributeDataType(AttributeDataType.BOOLEAN)
                                        .name(/* CognitoUserStore.USER_ATTRIBUTE_MARKETING_AGREED */ "marketing-agreed")
                                        .mutable(true).build(),
                                SchemaAttributeType.builder()
                                        .name(/* CognitoUserStore.USER_ATTRIBUTE_EMAIL */ "email")
                                        .required(true).build())
                        .policies(UserPoolPolicyType.builder()
                                .passwordPolicy(PasswordPolicyType.builder()
                                        .minimumLength(8)
                                        .requireLowercase(true)
                                        .requireUppercase(true)
                                        .requireNumbers(true)
                                        .requireSymbols(true)
                                        .temporaryPasswordValidityDays(1).build())
                                .build())
                        .build())
                .userPool();
        UserPoolClientType userPoolClient = cognitoClient.createUserPoolClient(CreateUserPoolClientRequest.builder()
                        .clientName("userpoolclient-" + UUID.randomUUID())
                        .userPoolId(userPool.id())
                        .generateSecret(false)
                        .build())
                .userPoolClient();
        log.info("Created Cognito user pool {} ({}) and client {} ({})", userPool.name(), userPool.id(), userPoolClient.clientName(), userPoolClient.clientId());
        return Map.of(
                /* CognitoUserStore.USER_POOL_ID_PROP_NAME */ "aws.cognito.user-pool.id", userPool.id(),
                /* CognitoUserStore.USER_POOL_APP_CLIENT_ID_PROP_NAME */ "aws.cognito.user-pool.client.id", userPoolClient.clientId());
    }
}
