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

import com.google.common.annotations.VisibleForTesting;
import io.dataspray.store.ApiAccessStore;
import io.dataspray.store.EmailValidator;
import io.dataspray.store.EmailValidator.EmailValidResult;
import io.dataspray.store.OrganizationStore;
import io.dataspray.store.UserStore;
import io.dataspray.store.UserStore.CognitoProperties;
import io.dataspray.stream.control.model.ApiKeyWithSecret;
import io.dataspray.stream.control.model.ApiKeys;
import io.dataspray.stream.control.model.AuthResult;
import io.dataspray.stream.control.model.ChallengeConfirmCode;
import io.dataspray.stream.control.model.ChallengePasswordChange;
import io.dataspray.stream.control.model.ChallengeTotpCode;
import io.dataspray.stream.control.model.SignInChallengePasswordChangeRequest;
import io.dataspray.stream.control.model.SignInChallengeTotpCodeRequest;
import io.dataspray.stream.control.model.SignInRequest;
import io.dataspray.stream.control.model.SignInResponse;
import io.dataspray.stream.control.model.SignUpConfirmCodeRequest;
import io.dataspray.stream.control.model.SignUpRequest;
import io.dataspray.stream.control.model.SignUpResponse;
import io.dataspray.web.resource.AbstractResource;
import io.quarkus.runtime.configuration.ConfigUtils;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.ClientErrorException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.NotImplementedException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminInitiateAuthResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminRespondToAuthChallengeResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AuthenticationResultType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ChallengeNameType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.CodeDeliveryFailureException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.CodeMismatchException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ExpiredCodeException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.InvalidPasswordException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.NotAuthorizedException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.TooManyFailedAttemptsException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.TooManyRequestsException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserNotConfirmedException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserNotFoundException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UsernameExistsException;

import java.util.Optional;
import java.util.regex.Pattern;

@Slf4j
@ApplicationScoped
public class AuthNzResource extends AbstractResource implements AuthNzApi {

    @Inject
    ApiAccessStore apiAccessStore;
    @Inject
    UserStore userStore;
    @Inject
    OrganizationStore organizationStore;
    @Inject
    EmailValidator emailValidator;

    @Override
    public ApiKeyWithSecret createApiKey() {
        throw new NotImplementedException();
    }

    @Override
    public ApiKeyWithSecret getApiKeySecret(String apiKeyId) {
        throw new NotImplementedException();
    }

    @Override
    public ApiKeys listApiKeys() {
        throw new NotImplementedException();
    }

    @Override
    public void revokeApiKey(String apiKeyId) {
        throw new NotImplementedException();
    }

    @Override
    public SignUpResponse signUp(SignUpRequest request) {

        // Validate TOS agreed
        if (!request.getTosAgreed()) {
            return SignUpResponse.builder()
                    .errorMsg("You must agree to the Terms of Service.")
                    .build();
        }

        // Validate username
        if (!Pattern.matches(UserStore.USERNAME_VALIDATION, request.getUsername())) {
            return SignUpResponse.builder()
                    .errorMsg("Username contains invalid characters.")
                    .build();
        }

        // Pre-validate password as a parameter to Cognito, password policy will be evaluated by Cognito itself
        // Password must match regex otherwise Cognito throws InvalidParameterException instead of InvalidPasswordException
        if (!request.getPassword().matches("^[\\S]+.*[\\S]+$")) {
            return SignUpResponse.builder()
                    .errorMsg("Password is too short or contains spaces at the beginning or end.")
                    .build();

        // Validate email address
        EmailValidResult emailValidationResult = emailValidator.check(request.getEmail());
        switch (emailValidationResult) {
            case INVALID:
                return SignUpResponse.builder()
                        .errorMsg("Email address you supplied is invalid, please contact support if this is a mistake.")
                        .build();
            case DISPOSABLE:
                return SignUpResponse.builder()
                        .errorMsg("Email address appears to be a disposable email address, please contact support if this is a mistake.")
                        .build();
            default:
                log.warn("Unexpected email validation result {}, returning as valid", emailValidationResult);
            case VALID:
                break;
        }

        // Sign up
        software.amazon.awssdk.services.cognitoidentityprovider.model.SignUpResponse response;
        try {
            response = userStore.signup(
                    request.getUsername(),
                    request.getEmail(),
                    request.getPassword(),
                    request.getTosAgreed(),
                    request.getMarketingAgreed());
        } catch (UsernameExistsException ex) {
            return SignUpResponse.builder()
                    .errorMsg("Username already exists.")
                    .build();
        } catch (TooManyRequestsException ex) {
            throw new ClientErrorException(429, ex);
        } catch (InvalidPasswordException ex) {
            return SignUpResponse.builder()
                    .errorMsg("Password must be at least 8 characters long, contain at least one uppercase letter, one lowercase letter, one number, and one special character. Password cannot be a common password or have common patterns.")
                    .build();
        } catch (CodeDeliveryFailureException ex) {
            return SignUpResponse.builder()
                    .errorMsg("Verification code failed to deliver successfully.")
                    .build();
        }

        // We are grouping code confirmation and user confirmation together here, but there are two cases:
        // - User was sent a confirmation code and needs to confirm themselves
        // - User was not sent a confirmation, an admin needs to confirm this account
        // Since we don't use the second option, assume we are only dealing with the first case.
        // Moto doesn't set code delivery details, so we can't test the second case.
        if (!Boolean.TRUE.equals(response.userConfirmed())
            || response.codeDeliveryDetails() != null) {
            return SignUpResponse.builder()
                    .codeRequired(ChallengeConfirmCode.builder()
                            .username(request.getUsername())
                            .build())
                    .build();
        }

        return signUpConfirmed();
    }

    @Override
    public SignUpResponse signUpConfirmCode(SignUpConfirmCodeRequest request) {
        try {
            userStore.signupConfirmCode(request.getUsername(), request.getCode());
        } catch (TooManyRequestsException ex) {
            throw new ClientErrorException(429, ex);
        } catch (NotAuthorizedException ex) {
            return SignUpResponse.builder()
                    .confirmed(false)
                    .errorMsg("you are not authorized.")
                    .build();
        } catch (TooManyFailedAttemptsException ex) {
            return SignUpResponse.builder()
                    .confirmed(false)
                    .errorMsg("You have too many failed attempts.")
                    .build();
        } catch (CodeMismatchException ex) {
            return SignUpResponse.builder()
                    .confirmed(false)
                    .errorMsg("Code is not correct.")
                    .build();
        } catch (ExpiredCodeException ex) {
            try {
                userStore.signupResendCode(request.getUsername());
            } catch (Exception ex2) {
                log.error("During sign-up, failed to resend code for email {}", request.getUsername(), ex2);
                return SignUpResponse.builder()
                        .confirmed(false)
                        .errorMsg("Code has already expired, failed to send another code.")
                        .build();
            }
            return SignUpResponse.builder()
                    .confirmed(false)
                    .errorMsg("Code has already expired, sent another one.")
                    .build();
        }

        return signUpConfirmed();
    }

    /**
     * Once a user is confirmed, we can trigger other events here.
     */
    private SignUpResponse signUpConfirmed() {

        return SignUpResponse.builder()
                .confirmed(true)
                .build();
    }

    @Override
    public SignInResponse signIn(SignInRequest request) {
        AdminInitiateAuthResponse response;
        try {
            response = userStore.signin(request.getUsernameOrEmail(), request.getPassword());
        } catch (NotAuthorizedException ex) {
            return SignInResponse.builder()
                    .errorMsg("You are not authorized.")
                    .build();
        } catch (TooManyRequestsException ex) {
            throw new ClientErrorException(429, ex);
        } catch (UserNotFoundException ex) {
            return SignInResponse.builder()
                    .errorMsg("Account was not found")
                    .build();
        } catch (UserNotConfirmedException ex) {

            // Fetch username since we don't know what it is
            String username;
            try {
                username = userStore.getUser(request.getUsernameOrEmail())
                        .username();
            } catch (Exception ex2) {
                log.error("During sign-in, got user not confirmed, but when tried to fetch user failed to get user by {}", request.getUsernameOrEmail(), ex2);
                return SignInResponse.builder()
                        .errorMsg("Failed to retrieve account. Try signing-up again")
                        .build();
            }

            // Try to re-send code
            try {
                userStore.signupResendCode(username);
            } catch (Exception ex2) {
                log.error("During sign-in, got user not confirmed, but failed to resend code for username {}", request.getUsernameOrEmail(), ex2);
            }

            // Ask user to confirm code
            return SignInResponse.builder()
                    .codeRequired(ChallengeConfirmCode.builder()
                            .username(username)
                            .build())
                    .build();
        }

        return signinResponseHandleChallengeAndResult(
                Optional.ofNullable(response.challengeName()),
                Optional.ofNullable(response.session()),
                Optional.ofNullable(response.authenticationResult()),
                Optional.ofNullable(response.challengeParameters())
                        .flatMap(challengeParameters -> Optional.ofNullable(challengeParameters.get("USER_ID_FOR_SRP"))));

    }

    @Override
    public SignInResponse signInChallengeTotpCode(SignInChallengeTotpCodeRequest request) {
        AdminRespondToAuthChallengeResponse response;
        try {
            response = userStore.signinChallengeTotpCode(request.getSession(), request.getUsername(), request.getCode());
        } catch (TooManyRequestsException ex) {
            throw new ClientErrorException(429, ex);
        }

        return signinResponseHandleChallengeAndResult(
                Optional.ofNullable(response.challengeName()),
                Optional.of(response.session()),
                Optional.ofNullable(response.authenticationResult()),
                Optional.ofNullable(response.challengeParameters())
                        .flatMap(challengeParameters -> Optional.ofNullable(challengeParameters.get("USER_ID_FOR_SRP"))));
    }

    @Override
    public SignInResponse signInChallengePasswordChange(SignInChallengePasswordChangeRequest request) {
        AdminRespondToAuthChallengeResponse response;
        try {
            response = userStore.signinChallengeNewPassword(
                    request.getSession(),
                    request.getUsername(),
                    request.getNewPassword());
        } catch (TooManyRequestsException ex) {
            throw new ClientErrorException(429, ex);
        }

        return signinResponseHandleChallengeAndResult(
                Optional.ofNullable(response.challengeName()),
                Optional.of(response.session()),
                Optional.ofNullable(response.authenticationResult()),
                Optional.ofNullable(response.challengeParameters())
                        .flatMap(challengeParameters -> Optional.ofNullable(challengeParameters.get("USER_ID_FOR_SRP"))));
    }

    private SignInResponse signinResponseHandleChallengeAndResult(
            Optional<ChallengeNameType> challengeNameOpt,
            Optional<String> sessionOpt,
            Optional<AuthenticationResultType> authenticationResultOpt,
            Optional<String> challengeUsernameOpt) {

        if (challengeNameOpt.isPresent()) {
            ChallengeNameType challengeName = challengeNameOpt.get();
            String session = sessionOpt.orElseThrow(() -> new IllegalStateException("Session is expected for challenge response: " + challengeName));
            String username = challengeUsernameOpt.orElseThrow(() -> new IllegalStateException("Username is expected for challenge response: " + challengeName));

            switch (challengeName) {
                case SOFTWARE_TOKEN_MFA:
                    return SignInResponse.builder()
                            .challengeTotpCode(ChallengeTotpCode.builder()
                                    .session(session)
                                    .username(username)
                                    .build())
                            .build();
                case NEW_PASSWORD_REQUIRED:
                    return SignInResponse.builder()
                            .challengePasswordChange(ChallengePasswordChange.builder()
                                    .session(session)
                                    .username(username)
                                    .build())
                            .build();
                case MFA_SETUP:
                    log.error("MFA setup is not yet supported as part of sign-in flow. username {}", username);
                    return SignInResponse.builder()
                            .errorMsg("MFA setup is not yet supported as part of sign-in flow.")
                            .build();
                case SMS_MFA:
                case SELECT_MFA_TYPE:
                    log.error("SMS MFA setup is not yet supported. username {}", username);
                    return SignInResponse.builder()
                            .errorMsg("SMS MFA not supported yet.")
                            .build();
                default:
                    throw new IllegalStateException("Unexpected value: " + challengeName);
            }
        }

        if (authenticationResultOpt.isEmpty()) {
            return SignInResponse.builder()
                    .errorMsg("Authentication failed.")
                    .build();
        }
        AuthenticationResultType authenticationResult = authenticationResultOpt.get();

        return SignInResponse.builder()
                .result(AuthResult.builder()
                        .accessToken(authenticationResult.accessToken())
                        .refreshToken(authenticationResult.refreshToken())
                        .idToken(authenticationResult.idToken())
                        .build())
                .build();
    }

    @VisibleForTesting
    @POST
    @Path("/test/set-account-store-cognito-properties")
    @Consumes("application/json")
    public void setAccountStoreCognitoProperties(CognitoProperties cognitoProperties) {
        if (!ConfigUtils.getProfiles().contains("test")) {
            log.info("This endpoint is only for testing. active profiles {}", ConfigUtils.getProfiles());
            throw new ForbiddenException();
        }

        userStore.setCognitoProperties(cognitoProperties);
    }
}
