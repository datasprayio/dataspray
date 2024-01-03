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

package io.dataspray.store.impl;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import io.dataspray.store.UserStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminInitiateAuthRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminInitiateAuthResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminRespondToAuthChallengeRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminRespondToAuthChallengeResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AssociateSoftwareTokenRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AssociateSoftwareTokenResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AttributeType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AuthFlowType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ChallengeNameType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ConfirmSignUpRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ConfirmSignUpResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ResendConfirmationCodeRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ResendConfirmationCodeResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.SignUpRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.SignUpResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.VerifySoftwareTokenRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.VerifySoftwareTokenResponse;

@Slf4j
@ApplicationScoped
public class CognitoUserStore implements UserStore {

    public static final String USER_POOL_ID_PROP_NAME = "aws.cognito.user-pool.id";
    public static final String USER_POOL_APP_CLIENT_ID_PROP_NAME = "aws.cognito.user-pool.client.id";
    private static final String ACCOUNT_STREAM_NAMES_ATTRIBUTE = "streams";
    private static final String USER_ATTRIBUTE_EMAIL = "email";
    private static final String USER_ATTRIBUTE_TOS_AGREED = "custom:tos-agreed";
    private static final String USER_ATTRIBUTE_MARKETING_AGREED = "custom:marketing-agreed";

    @ConfigProperty(name = USER_POOL_ID_PROP_NAME)
    String userPoolId;
    @ConfigProperty(name = USER_POOL_APP_CLIENT_ID_PROP_NAME)
    String userPoolClientId;

    @Inject
    CognitoIdentityProviderClient cognitoClient;

    /**
     * Sign up flow.
     *
     * @link <a
     * href="https://docs.aws.amazon.com/cognito-user-identity-pools/latest/APIReference/API_SignUp.html">SignUp</a>
     */
    @Override
    public SignUpResponse signup(String username, String email, String password, boolean tosAgreed, boolean marketingAgreed) {
        ImmutableSet.Builder<AttributeType> attrsBuilder = ImmutableSet.<AttributeType>builder()
                .add(AttributeType.builder()
                        .name(USER_ATTRIBUTE_EMAIL)
                        .value(email)
                        .build());
        if (tosAgreed) {
            attrsBuilder.add(AttributeType.builder()
                    .name(USER_ATTRIBUTE_TOS_AGREED)
                    .value("true")
                    .build());
        }
        if (marketingAgreed) {
            attrsBuilder.add(AttributeType.builder()
                    .name(USER_ATTRIBUTE_MARKETING_AGREED)
                    .value("true")
                    .build());
        }
        return cognitoClient.signUp(SignUpRequest.builder()
                .clientId(userPoolClientId)
                .username(email)
                .userAttributes(attrsBuilder.build())
                .password(password)
                .build());
    }

    @Override
    public ConfirmSignUpResponse signupConfirmCode(String username, String code) {
        return cognitoClient.confirmSignUp(ConfirmSignUpRequest.builder()
                .clientId(userPoolClientId)
                .username(username)
                .confirmationCode(code)
                .build());
    }

    @Override
    public ResendConfirmationCodeResponse signupResendCode(String username) {
        return cognitoClient.resendConfirmationCode(ResendConfirmationCodeRequest.builder()
                .clientId(userPoolClientId)
                .username(username)
                .build());
    }

    /**
     * Start sign-in.
     *
     * @link <a
     * href="https://docs.aws.amazon.com/cognito-user-identity-pools/latest/APIReference/API_AdminInitiateAuth.html">AdminInitiateAuth</a>
     */
    @Override
    public AdminInitiateAuthResponse signin(String usernameOrEmail, String password) {
        ImmutableMap.Builder<String, String> authParametersBuilder = ImmutableMap.<String, String>builder()
                .put("PASSWORD", password);
        if (usernameOrEmail.contains("@")) {
            authParametersBuilder.put("EMAIL", usernameOrEmail);
        } else {
            authParametersBuilder.put("USERNAME", usernameOrEmail);
        }
        return cognitoClient.adminInitiateAuth(AdminInitiateAuthRequest.builder()
                .authFlow(AuthFlowType.ADMIN_USER_PASSWORD_AUTH)
                .userPoolId(userPoolId)
                .clientId(userPoolClientId)
                .authParameters(authParametersBuilder.build())
                .build());
    }

    /**
     * Associate a totp to your account as part of sign-in challenge.
     *
     * @link <a
     * href="https://docs.aws.amazon.com/cognito-user-identity-pools/latest/APIReference/API_AssociateSoftwareToken.html">AssociateSoftwareToken</a>
     */
    @Override
    public AssociateSoftwareTokenResponse associateSoftwareTokenGivenSession(String session) {
        return cognitoClient.associateSoftwareToken(AssociateSoftwareTokenRequest.builder()
                .session(session)
                .build());
    }

    /**
     * Associate a totp to your account while you are already signed in.
     *
     * @link <a
     * href="https://docs.aws.amazon.com/cognito-user-identity-pools/latest/APIReference/API_AssociateSoftwareToken.html">AssociateSoftwareToken</a>
     */
    @Override
    public AssociateSoftwareTokenResponse associateSoftwareTokenGivenAccessToken(String accessToken) {
        return cognitoClient.associateSoftwareToken(AssociateSoftwareTokenRequest.builder()
                .accessToken(accessToken)
                .build());
    }

    /**
     * Verify adding new totp device as aprt of sign-in challenge.
     *
     * @link <a
     * href="https://docs.aws.amazon.com/cognito-user-identity-pools/latest/APIReference/API_VerifySoftwareToken.html">VerifySoftwareToken</a>
     */
    @Override
    public VerifySoftwareTokenResponse verifySoftwareTokenGivenSession(String session, String friendlyDeviceName, String code) {
        return cognitoClient.verifySoftwareToken(VerifySoftwareTokenRequest.builder()
                .session(session)
                .friendlyDeviceName(friendlyDeviceName)
                .userCode(code)
                .build());
    }

    /**
     * Verify adding new totp device with a current code.
     *
     * @link <a
     * href="https://docs.aws.amazon.com/cognito-user-identity-pools/latest/APIReference/API_VerifySoftwareToken.html">VerifySoftwareToken</a>
     */
    @Override
    public VerifySoftwareTokenResponse verifySoftwareTokenGivenAccessToken(String accessToken, String friendlyDeviceName, String code) {
        return cognitoClient.verifySoftwareToken(VerifySoftwareTokenRequest.builder()
                .accessToken(accessToken)
                .friendlyDeviceName(friendlyDeviceName)
                .userCode(code)
                .build());
    }

    @Override
    public AdminRespondToAuthChallengeResponse signinChallengeNewPassword(String session, String username, String newPassword) {
        return signinChallenge(session, ChallengeNameType.NEW_PASSWORD_REQUIRED, ImmutableMap.of(
                "USERNAME", username,
                "NEW_PASSWORD", newPassword));
    }

    @Override
    public AdminRespondToAuthChallengeResponse signinChallengeTotpCode(String session, String username, String code) {
        return signinChallenge(session, ChallengeNameType.SOFTWARE_TOKEN_MFA, ImmutableMap.of(
                "USERNAME", username,
                "SOFTWARE_TOKEN_MFA_CODE", code));
    }

    @Override
    public AdminRespondToAuthChallengeResponse signinChallengeTotpSetup(String session, String username, String verifySoftwareTokenSession) {
        return signinChallenge(session, ChallengeNameType.MFA_SETUP, ImmutableMap.of(
                "USERNAME", username,
                "SESSION", verifySoftwareTokenSession));
    }

    @Override
    public void validateAccessToken(String accessToken) {
        // TODO
    }

    /**
     * Respond to challenge. Used for but not limited to:
     * <ul>
     *     <li>TOTP setup</li>
     *     <li>TOTP code</li>
     *     <li>Set new password</li>
     * </ul>
     *
     * @link <a
     * href="https://docs.aws.amazon.com/cognito-user-identity-pools/latest/APIReference/API_AdminRespondToAuthChallenge.html">AdminRespondToAuthChallenge</a>
     */
    private AdminRespondToAuthChallengeResponse signinChallenge(String session, ChallengeNameType challengeName, ImmutableMap<String, String> responses) {
        return cognitoClient.adminRespondToAuthChallenge(AdminRespondToAuthChallengeRequest.builder()
                .clientId(userPoolId)
                .userPoolId(userPoolId)
                .session(session)
                .challengeName(challengeName)
                .challengeResponses(responses)
                .build());
    }

    @Override
    public void setCognitoProperties(CognitoProperties cognitoProperties) {
        this.userPoolId = cognitoProperties.getUserPoolId();
        this.userPoolClientId = cognitoProperties.getUserPoolClientId();
    }
}
