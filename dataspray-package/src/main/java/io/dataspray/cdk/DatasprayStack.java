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

package io.dataspray.cdk;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import io.dataspray.cdk.api.ApiStack;
import io.dataspray.cdk.dns.DnsStack;
import io.dataspray.cdk.site.SsgNextSiteStack;
import io.dataspray.cdk.store.AuthNzStack;
import io.dataspray.cdk.store.SingleTableStack;
import io.dataspray.cdk.stream.control.ControlFunctionStack;
import io.dataspray.cdk.stream.ingest.IngestFunctionStack;
import io.dataspray.cdk.template.FunctionStack;
import io.dataspray.common.DeployEnvironment;
import io.dataspray.store.SingleTableProvider;
import io.dataspray.store.impl.CognitoUserStore;
import io.dataspray.store.impl.DynamoApiGatewayApiAccessStore;
import io.dataspray.store.impl.FirehoseS3AthenaBatchStore;
import io.dataspray.store.impl.LambdaDeployerImpl;
import io.dataspray.stream.control.ControlResource;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awscdk.App;
import software.amazon.awscdk.cxapi.CloudAssembly;
import software.amazon.awscdk.services.lambda.SingletonFunction;

import java.util.Optional;
import java.util.Set;

import static io.dataspray.common.DeployEnvironment.SELFHOST;

@Slf4j
public class DatasprayStack {

    public static void main(String[] args) {
        synth(args);
    }

    public static CloudAssembly synth(String... args) {
        App app = new App();

        if (args.length != 7) {
            log.error("Usage: DatasprayStack <deployEnv> <authorizerCodeZip> <controlCodeZip> <ingestCodeZip> <siteLandingDir> <siteDocsDir> <siteDashboardDir>");
            System.exit(1);
        }
        DeployEnvironment deployEnv = DeployEnvironment.valueOf(args[0]);
        String authorizerCodeZip = args[1];
        String controlCodeZip = args[2];
        String ingestCodeZip = args[3];
        String siteLandingDir = args[4];
        String siteDocsDir = args[5];
        String siteDashboardDir = args[6];

        // Keep track of all Lambdas in order to pass config properties to them
        Set<FunctionStack> functionStacks = Sets.newHashSet();

        // Frontend
        DnsStack dnsStack = new DnsStack(app, deployEnv);
        if (!SELFHOST.equals(deployEnv)) {
            new SsgNextSiteStack(app, deployEnv, SsgNextSiteStack.Options.builder()
                    .identifier("site-landing")
                    .dnsStack(dnsStack)
                    .staticSiteDir(siteLandingDir)
                    .build());
        }
        new SsgNextSiteStack(app, deployEnv, SsgNextSiteStack.Options.builder()
                .identifier("site-docs")
                .subdomain(Optional.of("docs"))
                .dnsStack(dnsStack)
                .staticSiteDir(siteDocsDir)
                .build());
        new SsgNextSiteStack(app, deployEnv, SsgNextSiteStack.Options.builder()
                .identifier("site-dashboard")
                .subdomain(SELFHOST.equals(deployEnv)
                        ? Optional.empty()
                        : Optional.of("dashboard"))
                .dnsStack(dnsStack)
                .staticSiteDir(siteDashboardDir)
                .build());

        SingleTableStack singleTableStack = new SingleTableStack(app, deployEnv);
        AuthNzStack authNzStack = new AuthNzStack(app, deployEnv);

        IngestFunctionStack ingestStack = new IngestFunctionStack(app, deployEnv, ingestCodeZip);
        functionStacks.add(ingestStack);

        ControlFunctionStack controlStack = new ControlFunctionStack(app, deployEnv, controlCodeZip, authNzStack);
        functionStacks.add(controlStack);

        ApiStack apiStack = new ApiStack(app, ApiStack.Options.builder()
                .deployEnv(deployEnv)
                .openapiYamlPath("target/openapi/api.yaml")
                .apiFunctions(ImmutableSet.of(ingestStack, controlStack))
                .authorizerCodeZip(authorizerCodeZip)
                .dnsStack(dnsStack)
                .build());
        functionStacks.add(apiStack);

        // For dynamically-named resources such as S3 bucket names, pass the name as deployEnv vars directly to the lambdas
        // which will be picked up by Quarkus' @ConfigProperty
        for (FunctionStack functionStack : functionStacks) {
            for (SingletonFunction function : functionStack.getFunctions().values()) {
                setConfigProperty(function, DeployEnvironment.DEPLOY_ENVIRONMENT_PROP_NAME, deployEnv.name());
                setConfigProperty(function, ControlResource.DATASPRAY_API_ENDPOINT_PROP_NAME, apiStack.getApiFqdn(functionStack));
                setConfigProperty(function, CognitoUserStore.USER_POOL_ID_PROP_NAME, authNzStack.getUserPool().getUserPoolId());
                setConfigProperty(function, CognitoUserStore.USER_POOL_APP_CLIENT_ID_PROP_NAME, authNzStack.getUserPoolClient().getUserPoolClientId());
                setConfigProperty(function, SingleTableProvider.TABLE_PREFIX_PROP_NAME, singleTableStack.getSingleTableTable().getTableName());
                setConfigProperty(function, FirehoseS3AthenaBatchStore.ETL_BUCKET_PROP_NAME, ingestStack.getBucketEtlName());
                setConfigProperty(function, FirehoseS3AthenaBatchStore.FIREHOSE_STREAM_NAME_PROP_NAME, ingestStack.getFirehoseName());
                setConfigProperty(function, LambdaDeployerImpl.CUSTOMER_FUNCTION_PERMISSION_BOUNDARY_NAME_PROP_NAME, controlStack.getCustomerFunctionPermissionBoundaryManagedPolicyName());
                setConfigProperty(function, LambdaDeployerImpl.CODE_BUCKET_NAME_PROP_NAME, controlStack.getBucketCodeName());
                setConfigProperty(function, DynamoApiGatewayApiAccessStore.USAGE_PLAN_ID_PROP_NAME, apiStack.getActiveUsagePlan().getUsagePlanId());
            }
        }

        return app.synth();
    }

    public static void setConfigProperty(SingletonFunction function, String prop, String value) {
        // Adjust a quarkus property to the environment variable format
        // https://quarkus.io/guides/config-reference#environment-variablespom.xml
        String propAsEnvVar = prop.replaceAll("[^a-zA-Z0-9]", "_").toUpperCase();

        // Attach onto the function
        function.addEnvironment(propAsEnvVar, value);
    }

    private DatasprayStack() {
        // disallow ctor
    }
}
