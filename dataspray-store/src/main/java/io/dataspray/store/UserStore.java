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

package io.dataspray.store;


import com.google.common.annotations.VisibleForTesting;
import lombok.NonNull;
import lombok.Value;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminGetUserResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminInitiateAuthResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminRespondToAuthChallengeResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AssociateSoftwareTokenResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ConfirmSignUpResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ResendConfirmationCodeResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.SignUpResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.VerifySoftwareTokenResponse;

public interface UserStore {

    /**
     * @see <a
     * href="https://docs.aws.amazon.com/cognito-user-identity-pools/latest/APIReference/API_SignUp.html#CognitoUserPools-SignUp-request-Username">SignUp:username</a>
     */
    String USERNAME_VALIDATION = "^[\\p{L}\\p{M}\\p{S}\\p{N}\\p{P}]{1,128}$";

    SignUpResponse signup(String username, String email, String password, boolean tosAgreed, boolean marketingAgreed);

    ConfirmSignUpResponse signupConfirmCode(String username, String code);

    ResendConfirmationCodeResponse signupResendCode(String username);

    AdminGetUserResponse getUser(String usernameOrEmail);

    AdminInitiateAuthResponse signin(String usernameOrEmail, String password);

    AssociateSoftwareTokenResponse associateSoftwareTokenGivenSession(String session);

    AssociateSoftwareTokenResponse associateSoftwareTokenGivenAccessToken(String accessToken);

    VerifySoftwareTokenResponse verifySoftwareTokenGivenSession(String session, String friendlyDeviceName, String code);

    VerifySoftwareTokenResponse verifySoftwareTokenGivenAccessToken(String accessToken, String friendlyDeviceName, String code);

    AdminRespondToAuthChallengeResponse signinChallengeNewPassword(String session, String username, String newPassword);

    AdminRespondToAuthChallengeResponse signinChallengeTotpCode(String session, String username, String code);

    AdminRespondToAuthChallengeResponse signinChallengeTotpSetup(String session, String username, String verifySoftwareTokenSession);

    AdminInitiateAuthResponse refreshToken(String refreshToken);

    void signout(String refreshToken);

    /**
     * Since Quarkus has no easy way to set properties on runtime from ta test, we need this endpoint to set them.
     */
    @VisibleForTesting
    void setCognitoProperties(CognitoProperties cognitoProperties);

    @VisibleForTesting
    @Value
    class CognitoProperties {
        @NonNull
        String userPoolId;
        @NonNull
        String userPoolClientId;
    }
}
