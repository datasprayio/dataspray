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

package io.dataspray.stream.control;

import com.google.common.annotations.VisibleForTesting;
import io.dataspray.store.AccountStore;
import io.dataspray.store.AccountStore.CognitoProperties;
import io.dataspray.store.EmailValidator;
import io.dataspray.store.EmailValidator.EmailValidResult;
import io.dataspray.store.impl.ApiAccessStore;
import io.dataspray.stream.control.model.ApiKeyWithSecret;
import io.dataspray.stream.control.model.ApiKeys;
import io.dataspray.stream.control.model.AuthResult;
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

import java.util.Optional;

import static com.google.common.base.Preconditions.checkState;

@Slf4j
@ApplicationScoped
public class AuthNzResource extends AbstractResource implements AuthNzApi {

    @Inject
    ApiAccessStore apiAccessStore;
    @Inject
    AccountStore accountStore;
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
            response = accountStore.signup(request.getEmail(), request.getPassword());
        } catch (TooManyRequestsException ex) {
            throw new ClientErrorException(429, ex);
        } catch (NotAuthorizedException ex) {
            return SignUpResponse.builder()
                    .errorMsg("Email address appears to be a disposable email address, please contact support if this is a mistake.")
                    .build();
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
                    .codeRequired(true)
                    .build();
        }

        return SignUpResponse.builder()
                .confirmed(true)
                .build();
    }

    @Override
    public SignUpResponse signUpConfirmCode(SignUpConfirmCodeRequest request) {
        try {
            accountStore.signupConfirmCode(request.getEmail(), request.getCode());
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
                accountStore.signupResendCode(request.getEmail());
            } catch (Exception ex2) {
                log.error("During sign-up, failed to resend code for email {}", request.getEmail(), ex2);
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
        return SignUpResponse.builder()
                .confirmed(true)
                .build();
    }

    @Override
    public SignInResponse signIn(SignInRequest request) {
        AdminInitiateAuthResponse response;
        try {
            response = accountStore.signin(request.getEmail(), request.getPassword());
        } catch (NotAuthorizedException ex) {
            return SignInResponse.builder()
                    .errorMsg("You are not authorized.")
                    .build();
        } catch (TooManyRequestsException ex) {
            throw new ClientErrorException(429, ex);
        } catch (UserNotFoundException ex) {
            return SignInResponse.builder()
                    .errorMsg("Email not found.")
                    .build();
        } catch (UserNotConfirmedException ex) {
            try {
                accountStore.signupResendCode(request.getEmail());
            } catch (Exception ex2) {
                log.error("During sign-in, failed to resend code for email {}", request.getEmail(), ex2);
            }
            return SignInResponse.builder()
                    .codeRequired(true)
                    .build();
        }

        return signinResponseHandleChallengeAndResult(
                request.getEmail(),
                Optional.ofNullable(response.challengeName()),
                Optional.ofNullable(response.session()),
                Optional.ofNullable(response.authenticationResult()));

    }

    @Override
    public SignInResponse signInChallengeTotpCode(SignInChallengeTotpCodeRequest request) {
        AdminRespondToAuthChallengeResponse response;
        try {
            response = accountStore.signinChallengeTotpCode(request.getSession(), request.getEmail(), request.getCode());
        } catch (TooManyRequestsException ex) {
            throw new ClientErrorException(429, ex);
        }

        return signinResponseHandleChallengeAndResult(
                request.getEmail(),
                Optional.ofNullable(response.challengeName()),
                Optional.of(response.session()),
                Optional.ofNullable(response.authenticationResult()));
    }

    @Override
    public SignInResponse signInChallengePasswordChange(SignInChallengePasswordChangeRequest request) {
        AdminRespondToAuthChallengeResponse response;
        try {
            response = accountStore.signinChallengeNewPassword(
                    request.getSession(),
                    request.getEmail(),
                    request.getNewPassword());
        } catch (TooManyRequestsException ex) {
            throw new ClientErrorException(429, ex);
        }

        return signinResponseHandleChallengeAndResult(
                request.getEmail(),
                Optional.ofNullable(response.challengeName()),
                Optional.of(response.session()),
                Optional.ofNullable(response.authenticationResult()));
    }

    private SignInResponse signinResponseHandleChallengeAndResult(
            String email,
            Optional<ChallengeNameType> challengeNameOpt,
            Optional<String> sessionOpt,
            Optional<AuthenticationResultType> authenticationResultOpt) {

        if (challengeNameOpt.isPresent()) {
            ChallengeNameType challengeName = challengeNameOpt.get();
            checkState(sessionOpt.isPresent(), "Session is expected for challenge response: " + challengeName);
            String session = sessionOpt.get();

            switch (challengeName) {
                case SOFTWARE_TOKEN_MFA:
                    return SignInResponse.builder()
                            .challengeTotpCode(ChallengeTotpCode.builder()
                                    .session(session)
                                    .build())
                            .build();
                case NEW_PASSWORD_REQUIRED:
                    return SignInResponse.builder()
                            .challengePasswordChange(ChallengePasswordChange.builder()
                                    .session(session)
                                    .build())
                            .build();
                case MFA_SETUP:
                    log.error("MFA setup is not yet supported as part of sign-in flow. email {}", email);
                    return SignInResponse.builder()
                            .errorMsg("MFA setup is not yet supported as part of sign-in flow.")
                            .build();
                case SMS_MFA:
                case SELECT_MFA_TYPE:
                    log.error("SMS MFA setup is not yet supported. email {}", email);
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

        accountStore.setCognitoProperties(cognitoProperties);
    }
}
