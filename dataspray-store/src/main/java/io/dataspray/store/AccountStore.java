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

package io.dataspray.store;


import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;
import jakarta.ws.rs.ClientErrorException;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.Value;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminInitiateAuthResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminRespondToAuthChallengeResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AssociateSoftwareTokenResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ConfirmSignUpResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ResendConfirmationCodeResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.SignUpResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.VerifySoftwareTokenResponse;

import java.util.Optional;

public interface AccountStore {

    SignUpResponse signup(String email, String password);

    ConfirmSignUpResponse signupConfirmCode(String email, String code);

    ResendConfirmationCodeResponse signupResendCode(String email);

    AdminInitiateAuthResponse signin(String email, String password);

    AssociateSoftwareTokenResponse associateSoftwareTokenGivenSession(String session);

    AssociateSoftwareTokenResponse associateSoftwareTokenGivenAccessToken(String accessToken);

    VerifySoftwareTokenResponse verifySoftwareTokenGivenSession(String session, String friendlyDeviceName, String code);

    VerifySoftwareTokenResponse verifySoftwareTokenGivenAccessToken(String accessToken, String friendlyDeviceName, String code);

    AdminRespondToAuthChallengeResponse signinChallengeNewPassword(String session, String email, String newPassword);

    AdminRespondToAuthChallengeResponse signinChallengeTotpCode(String session, String email, String code);

    AdminRespondToAuthChallengeResponse signinChallengeTotpSetup(String session, String email, String verifySoftwareTokenSession);

    Optional<Account> getAccount(String accountId);

    StreamMetadata authorizeStreamPut(
            String accountId,
            String targetId,
            Optional<String> authKeyOpt) throws ClientErrorException;

    StreamMetadata getStream(
            String accountId,
            String targetId) throws ClientErrorException;

    /**
     * Since Quarkus has no easy way to set properties on runtime from ta test, we need this endpoint to set them.
     */
    @VisibleForTesting
    void setCognitoProperties(CognitoProperties cognitoProperties);

    @Value
    class StreamMetadata {
        @NonNull
        Optional<EtlRetention> retentionOpt;
    }

    @Value
    @Builder(toBuilder = true)
    @AllArgsConstructor
    class Account {
        @NonNull
        String accountId;

        @NonNull
        String email;

        @NonNull
        ImmutableSet<String> enabledStreamNames;
    }

    @Getter
    @AllArgsConstructor
    enum EtlRetention {
        DAY(1),
        WEEK(7),
        THREE_MONTHS(3 * 30),
        YEAR(366),
        THREE_YEARS(3 * 366);
        public static final EtlRetention DEFAULT = THREE_MONTHS;
        int expirationInDays;
    }


    @VisibleForTesting
    @Value
    class CognitoProperties {
        @NonNull
        String userPoolId;
        @NonNull
        String userPoolClientId;
    }
}
