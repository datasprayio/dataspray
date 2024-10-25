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

package io.dataspray.stream.control;

import io.dataspray.common.test.aws.AbstractLambdaTest;
import io.dataspray.common.test.aws.MotoLifecycleManager;
import io.dataspray.store.UserStore.CognitoProperties;
import io.dataspray.store.impl.CognitoUserStore;
import io.dataspray.stream.control.model.ChallengeConfirmCode;
import io.dataspray.stream.control.model.SignInRequest;
import io.dataspray.stream.control.model.SignInResponse;
import io.dataspray.stream.control.model.SignUpConfirmCodeRequest;
import io.dataspray.stream.control.model.SignUpRequest;
import io.dataspray.stream.control.model.SignUpResponse;
import io.quarkus.test.common.QuarkusTestResource;
import jakarta.ws.rs.HttpMethod;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import software.amazon.awssdk.services.apigateway.ApiGatewayClient;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AliasAttributeType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AssociateSoftwareTokenRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AssociateSoftwareTokenResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AttributeDataType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.CreateUserPoolClientRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.CreateUserPoolRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.PasswordPolicyType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.SchemaAttributeType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.SetUserMfaPreferenceRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.SoftwareTokenMfaSettingsType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserPoolClientType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserPoolMfaType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserPoolPolicyType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserPoolType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.VerifiedAttributeType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.VerifySoftwareTokenRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.VerifySoftwareTokenResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.VerifySoftwareTokenResponseType;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.util.Optional;
import java.util.UUID;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON_TYPE;
import static org.junit.jupiter.api.Assertions.*;

@Slf4j
@QuarkusTestResource(MotoLifecycleManager.class)
public abstract class AuthNzBase extends AbstractLambdaTest {

    protected abstract CognitoIdentityProviderClient getCognitoClient();

    protected abstract DynamoDbClient getDynamoClient();

    protected abstract ApiGatewayClient apiGatewayClient();

    private UserPoolType userPool;
    private UserPoolClientType userPoolClient;

    @BeforeEach
    public void beforeEach() {
        // Setup Cognito
        // Should match the same configuration as in AuthNzStack
        userPool = getCognitoClient().createUserPool(CreateUserPoolRequest.builder()
                        .poolName("userpool-" + UUID.randomUUID())
                        .mfaConfiguration(UserPoolMfaType.OPTIONAL)
                        .autoVerifiedAttributes(VerifiedAttributeType.EMAIL)
                        .aliasAttributes(AliasAttributeType.EMAIL, AliasAttributeType.PREFERRED_USERNAME)
                        .schema(
                                SchemaAttributeType.builder()
                                        .attributeDataType(AttributeDataType.BOOLEAN)
                                        .name(CognitoUserStore.USER_ATTRIBUTE_TOS_AGREED)
                                        .mutable(true).build(),
                                SchemaAttributeType.builder()
                                        .attributeDataType(AttributeDataType.BOOLEAN)
                                        .name(CognitoUserStore.USER_ATTRIBUTE_MARKETING_AGREED)
                                        .mutable(true).build(),
                                SchemaAttributeType.builder()
                                        .name(CognitoUserStore.USER_ATTRIBUTE_EMAIL)
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
        userPoolClient = getCognitoClient().createUserPoolClient(CreateUserPoolClientRequest.builder()
                        .clientName("userpoolclient-" + UUID.randomUUID())
                        .userPoolId(userPool.id())
                        .generateSecret(false)
                        .build())
                .userPoolClient();
        log.info("Created Cognito user pool {} ({}) and client {} ({})", userPool.name(), userPool.id(), userPoolClient.clientName(), userPoolClient.clientId());

        // Populate account store properties
        request(Given.builder()
                .method(HttpMethod.POST)
                .path("/test/set-account-store-cognito-properties")
                .contentType(APPLICATION_JSON_TYPE)
                .body(new CognitoProperties(userPool.id(), userPoolClient.clientId()))
                .build())
                .assertStatusCode(Response.Status.NO_CONTENT.getStatusCode());
    }


    public enum TestType {
        SIMPLE,
        PASSWORD_POLICY,
        MFA_AFTER_SIGN_UP,
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(TestType.class)
    public void test(TestType testType) throws Exception {
        String username = UUID.randomUUID().toString();
        String email = username + "@example.com";
        String password;
        switch (testType) {
            case PASSWORD_POLICY -> password = "hunter2";
            default -> password = "j23jf#09d$fJF%DSj^klew@328@90483";
        }

        // Sign up
        SignUpResponse signUpResponse = request(SignUpResponse.class, Given.builder()
                .method(HttpMethod.PUT)
                .path("/v1/auth/sign-up")
                .body(SignUpRequest.builder()
                        .username(username)
                        .email(email)
                        .password(password)
                        .tosAgreed(true)
                        .marketingAgreed(true)
                        .build())
                .build())
                .assertStatusCode(Response.Status.OK.getStatusCode())
                .getBody();
        switch (testType) {
            case PASSWORD_POLICY -> {
                assertEquals(Optional.of("Password must be at least 8 characters long, contain at least one uppercase letter, one lowercase letter, one number, and one special character. Password cannot be a common password or have common patterns."), Optional.ofNullable(signUpResponse.getErrorMsg()));
                assertNotEquals(Boolean.TRUE, signUpResponse.getCodeRequired());
                assertNotEquals(Boolean.TRUE, signUpResponse.getConfirmed());
                return;
            }
            default -> {
                assertEquals(Optional.empty(), Optional.ofNullable(signUpResponse.getErrorMsg()));
                assertNotEquals(Boolean.TRUE, signUpResponse.getConfirmed());
                assertEquals(ChallengeConfirmCode.builder()
                        .username(username)
                        .build(), signUpResponse.getCodeRequired());
            }
        }

        // Need to confirm email
        signUpResponse = request(SignUpResponse.class, Given.builder()
                .method(HttpMethod.PUT)
                .path("/v1/auth/sign-up/code")
                .body(SignUpConfirmCodeRequest.builder()
                        .username(username)
                        .code("123")
                        .build())
                .build())
                .assertStatusCode(Response.Status.OK.getStatusCode())
                .getBody();
        assertEquals(Optional.empty(), Optional.ofNullable(signUpResponse.getErrorMsg()));
        assertNotEquals(Boolean.TRUE, signUpResponse.getCodeRequired());
        assertEquals(Boolean.TRUE, signUpResponse.getConfirmed());

        // Sign in using username
        SignInResponse signInResponse = request(SignInResponse.class, Given.builder()
                .method(HttpMethod.POST)
                .path("/v1/auth/sign-in")
                .body(SignInRequest.builder()
                        .usernameOrEmail(username)
                        .password(password)
                        .build())
                .build())
                .assertStatusCode(Response.Status.OK.getStatusCode())
                .getBody();
        assertEquals(Optional.empty(), Optional.ofNullable(signInResponse.getErrorMsg()));
        assertNull(signInResponse.getChallengeTotpCode());
        assertNotNull(signInResponse.getResult());

        if (testType == TestType.MFA_AFTER_SIGN_UP) {

            // Setup MFA directly with Cognito bypassing lambda
            // Request to add software token
            AssociateSoftwareTokenResponse associateSoftwareTokenResponse = getCognitoClient().associateSoftwareToken(AssociateSoftwareTokenRequest.builder()
                    .accessToken(signInResponse.getResult().getAccessToken())
                    .build());
            // Confirm token
            VerifySoftwareTokenResponse verifySoftwareTokenResponse = getCognitoClient().verifySoftwareToken(VerifySoftwareTokenRequest.builder()
                    .accessToken(signInResponse.getResult().getAccessToken())
                    .friendlyDeviceName("mydevice")
                    .userCode("123456")
                    .build());
            assertEquals(VerifySoftwareTokenResponseType.SUCCESS, verifySoftwareTokenResponse.status());
            // Enable MFA as preferred
            getCognitoClient().setUserMFAPreference(SetUserMfaPreferenceRequest.builder()
                    .accessToken(signInResponse.getResult().getAccessToken())
                    .softwareTokenMfaSettings(SoftwareTokenMfaSettingsType.builder()
                            .enabled(true)
                            .preferredMfa(true)
                            .build())
                    .build());

            // Sign in again and expect MFA
            // TODO Disabled since Moto doesn't currently send USERNAME as a ChallengeParameters for SOFTWARE_TOKEN_MFA challenge
            /* signInResponse = request(SignInResponse.class, Given.builder()
                    .method(HttpMethod.POST)
                    .path("/v1/auth/sign-in")
                    .body(SignInRequest.builder()
                            // TODO Try using email here instead of username; unfortunately Moto currently doesn't support AliasAttributes
                            .usernameOrEmail(username)
                            .password(password)
                            .build())
                    .build())
                    .assertStatusCode(Response.Status.OK.getStatusCode())
                    .getBody();
            assertEquals(Optional.empty(), Optional.ofNullable(signInResponse.getErrorMsg()));
            assertNull(signInResponse.getResult());
            assertNotNull(signInResponse.getChallengeTotpCode()); */

            // Submit TOTP code
            // TODO Disabled until MOTO releases bug to support the underlying endpoint https://github.com/getmoto/moto/pull/7136/files in 4.2.13
            /* signInResponse = request(SignInResponse.class, Given.builder()
                    .method(HttpMethod.POST)
                    .path("/v1/auth/sign-in/totp")
                    .body(SignInChallengeTotpCodeRequest.builder()
                            .email(email)
                            .session(signInResponse.getChallengeTotpCode().getSession())
                            .code("123456")
                            .build())
                    .build())
                    .assertStatusCode(Response.Status.OK.getStatusCode())
                    .getBody();
            assertEquals(Optional.empty(), Optional.ofNullable(signInResponse.getErrorMsg()));
            assertNull(signInResponse.getChallengeTotpCode());
            assertNotNull(signInResponse.getResult()); */
        }
    }
}
