package io.dataspray.store.deploy;

import io.dataspray.backend.deploy.BaseStack;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awscdk.CfnCondition;
import software.amazon.awscdk.CfnParameter;
import software.amazon.awscdk.Duration;
import software.amazon.awscdk.Fn;
import software.amazon.awscdk.services.cognito.AccountRecovery;
import software.amazon.awscdk.services.cognito.AdvancedSecurityMode;
import software.amazon.awscdk.services.cognito.AutoVerifiedAttrs;
import software.amazon.awscdk.services.cognito.CfnUserPool;
import software.amazon.awscdk.services.cognito.DeviceTracking;
import software.amazon.awscdk.services.cognito.KeepOriginalAttrs;
import software.amazon.awscdk.services.cognito.Mfa;
import software.amazon.awscdk.services.cognito.MfaSecondFactor;
import software.amazon.awscdk.services.cognito.PasswordPolicy;
import software.amazon.awscdk.services.cognito.SignInAliases;
import software.amazon.awscdk.services.cognito.UserPool;
import software.constructs.Construct;

import java.util.List;
import java.util.Map;

import static java.util.Objects.requireNonNull;

@Slf4j
public class AuthNzStack extends BaseStack {

    public static final String USER_POOL_NAME = "dataspray-users";
    public static final String USER_POOL_ID_PROPERTY_NAME = "aws.cognito." + USER_POOL_NAME;

    @Getter
    CfnParameter emailWithParam;
    @Getter
    UserPool userPool;

    public AuthNzStack(Construct parent, String stackName) {
        super(parent, stackName);

        userPool = UserPool.Builder.create(this, stackName + "-authnz-userpool")
                .userPoolName(USER_POOL_NAME)
                .selfSignUpEnabled(true)
                .autoVerify(AutoVerifiedAttrs.builder()
                        .email(true).build())
                .mfa(Mfa.OPTIONAL)
                .mfaSecondFactor(MfaSecondFactor.builder()
                        .otp(true)
                        .sms(false).build())
                .passwordPolicy(PasswordPolicy.builder()
                        .minLength(8)
                        .requireLowercase(true)
                        .requireDigits(true)
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
        emailWithParam = CfnParameter.Builder.create(this, stackName + "authnz-sesDomain")
                .type("String")
                .defaultValue("")
                .description("Domain name of your SES to use. Leave blank to use Cognito Email.")
                .build();
        // https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/aws-properties-cognito-userpool-emailconfiguration.html
        userPoolCfn.addPropertyOverride("EmailConfiguration", Fn.conditionIf(
                CfnCondition.Builder.create(this, stackName + "-authnz-userpool-ses-or-cognito")
                        .expression(Fn.conditionEquals(emailWithParam, ""))
                        .build()
                        .getLogicalId(),
                // Use Cognito default email delivery with strict 50 emails per day limit
                Map.of("EmailSendingAccount", "COGNITO_DEFAULT"),
                // Use custom verified SES if provided
                Map.of("EmailSendingAccount", "DEVELOPER",
                        "SourceArn", Fn.join("", List.of(
                                "arn:aws:ses:" + getRegion() + ":" + getAccount() + ":identity/no-reply@",
                                emailWithParam.getValueAsString())),
                        "From", Fn.join("", List.of(
                                "DataSpray <no-reply@",
                                emailWithParam.getValueAsString(),
                                ">")))));

    }
}
