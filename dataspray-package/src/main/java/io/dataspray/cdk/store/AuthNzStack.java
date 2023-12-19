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

package io.dataspray.cdk.store;

import io.dataspray.cdk.template.BaseStack;
import io.dataspray.common.DeployEnvironment;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awscdk.CfnCondition;
import software.amazon.awscdk.CfnParameter;
import software.amazon.awscdk.Duration;
import software.amazon.awscdk.Fn;
import software.amazon.awscdk.services.cognito.AccountRecovery;
import software.amazon.awscdk.services.cognito.AdvancedSecurityMode;
import software.amazon.awscdk.services.cognito.AuthFlow;
import software.amazon.awscdk.services.cognito.AutoVerifiedAttrs;
import software.amazon.awscdk.services.cognito.CfnUserPool;
import software.amazon.awscdk.services.cognito.DeviceTracking;
import software.amazon.awscdk.services.cognito.KeepOriginalAttrs;
import software.amazon.awscdk.services.cognito.Mfa;
import software.amazon.awscdk.services.cognito.MfaSecondFactor;
import software.amazon.awscdk.services.cognito.PasswordPolicy;
import software.amazon.awscdk.services.cognito.SignInAliases;
import software.amazon.awscdk.services.cognito.UserPool;
import software.amazon.awscdk.services.cognito.UserPoolClient;
import software.constructs.Construct;

import java.util.List;
import java.util.Map;

import static java.util.Objects.requireNonNull;

@Slf4j
@Getter
public class AuthNzStack extends BaseStack {

    private final CfnParameter emailWithParam;
    private final UserPool userPool;
    private final UserPoolClient userPoolClient;

    public AuthNzStack(Construct parent, DeployEnvironment deployEnv) {
        super(parent, "authnz", deployEnv);

        userPool = UserPool.Builder.create(this, getConstructId("userpool"))
                .userPoolName(getConstructId("userpool"))
                .selfSignUpEnabled(false)
                .autoVerify(AutoVerifiedAttrs.builder()
                        .email(true).build())
                .mfa(Mfa.OPTIONAL)
                .mfaSecondFactor(MfaSecondFactor.builder()
                        .otp(true)
                        .sms(false).build())
                .passwordPolicy(PasswordPolicy.builder()
                        // If changed, also change the error message in method AuthNzResource.signUp
                        // when catching InvalidPasswordException
                        .minLength(8)
                        .requireLowercase(true)
                        .requireUppercase(true)
                        .requireDigits(true)
                        .requireSymbols(true)
                        .tempPasswordValidity(Duration.days(1)).build())
                .signInCaseSensitive(false)
                .signInAliases(SignInAliases.builder()
                        .preferredUsername(true)
                        .username(true)
                        .email(true).build())
                .deviceTracking(DeviceTracking.builder()
                        .deviceOnlyRememberedOnUserPrompt(true)
                        .challengeRequiredOnNewDevice(true).build())
                .accountRecovery(AccountRecovery.EMAIL_ONLY)
                .keepOriginal(KeepOriginalAttrs.builder()
                        .email(true).build())
                .advancedSecurityMode(AdvancedSecurityMode.OFF)
                .build();
        CfnUserPool userPoolCfn = (CfnUserPool) requireNonNull(userPool.getNode().getDefaultChild());
        // Decide to send email from Cognito or SES
        emailWithParam = CfnParameter.Builder.create(this, "sesEmail")
                .description("Email of your verified SES identity to use for sending and receiving emails. (e.g. support@example.com) Leave blank to use Cognito Email.")
                .type("String")
                .defaultValue("")
                .build();
        // https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/aws-properties-cognito-userpool-emailconfiguration.html
        userPoolCfn.addPropertyOverride("EmailConfiguration", Fn.conditionIf(
                CfnCondition.Builder.create(this, getConstructId("authnz-userpool-ses-or-cognito"))
                        .expression(Fn.conditionEquals(emailWithParam, ""))
                        .build()
                        .getLogicalId(),
                // Use Cognito default email delivery with strict 50 emails per day limit
                Map.of("EmailSendingAccount", "COGNITO_DEFAULT"),
                // Use custom verified SES if provided
                Map.of("EmailSendingAccount", "DEVELOPER",
                        "SourceArn", Fn.join("", List.of(
                                "arn:aws:ses:" + getRegion() + ":" + getAccount() + ":identity/",
                                emailWithParam.getValueAsString())),
                        "From", Fn.join("", List.of(
                                "DataSpray <",
                                emailWithParam.getValueAsString(),
                                ">")))));

        userPoolClient = UserPoolClient.Builder.create(this, getConstructId("userpoolclient"))
                .authFlows(AuthFlow.builder()
                        .adminUserPassword(true)
                        .build())
                .userPool(userPool)
                .userPoolClientName("authnz")
                .generateSecret(false)
                .build();
    }
}
