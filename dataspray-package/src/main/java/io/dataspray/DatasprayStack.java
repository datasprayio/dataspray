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

package io.dataspray;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import io.dataspray.dns.DnsStack;
import io.dataspray.site.OpenNextStack;
import io.dataspray.store.AuthNzStack;
import io.dataspray.store.CognitoAccountStore;
import io.dataspray.store.DynamoApiGatewayApiAccessStore;
import io.dataspray.store.FirehoseS3AthenaEtlStore;
import io.dataspray.store.LambdaDeployerImpl;
import io.dataspray.store.SingleTableProvider;
import io.dataspray.store.SingleTableStack;
import io.dataspray.stream.control.ControlStack;
import io.dataspray.stream.ingest.IngestStack;
import io.dataspray.web.AuthorizerStack;
import io.dataspray.web.BaseApiStack;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awscdk.App;
import software.amazon.awscdk.services.lambda.SingletonFunction;

import java.util.Set;

@Slf4j
public class DatasprayStack {

    public static void main(String[] args) {
        App app = new App();

        if (args.length != 2) {
            log.error("Usage: DatasprayStack <env> <codeDir>");
            System.exit(1);
        }
        String env = args[0];
        String codeDir = args[1];

        // Keep track of all Lambdas in order to pass config properties to them
        Set<SingletonFunction> functions = Sets.newHashSet();

        DnsStack dnsStack = new DnsStack(app, env);
        new OpenNextStack(app, env, OpenNextStack.Options.builder()
                .domain(dnsStack.getDomainParam().getValueAsString())
                .build());

        SingleTableStack singleTableStack = new SingleTableStack(app, env);
        AuthNzStack authNzStack = new AuthNzStack(app, env);

        AuthorizerStack authorizerStack = new AuthorizerStack(app, env, codeDir);
        functions.add(authorizerStack.getFunction());

        IngestStack ingestStack = new IngestStack(app, env, codeDir);
        functions.add(ingestStack.getFunction());

        ControlStack controlStack = new ControlStack(app, env, codeDir);
        functions.add(controlStack.getFunction());

        BaseApiStack baseApiStack = new BaseApiStack(app, BaseApiStack.Options.builder()
                .openapiYamlPath("target/openapi/api.yaml")
                .dnsStack(dnsStack)
                .authorizerStack(authorizerStack)
                .tagToFunction(ImmutableMap.of(
                        "Ingest", ingestStack.getFunction(),
                        "AuthNZ", controlStack.getFunction(),
                        "Control", controlStack.getFunction(),
                        "Health", controlStack.getFunction()))
                .build());

        // For dynamically-named resources such as S3 bucket names, pass the name as env vars directly to the lambdas
        // which will be picked up by Quarkus' @ConfigProperty
        for (SingletonFunction function : functions) {
            function.addEnvironment(CognitoAccountStore.USER_POOL_ID_PROP_NAME, authNzStack.getUserPool().getUserPoolId());
            function.addEnvironment(SingleTableProvider.TABLE_PREFIX_PROP_NAME, singleTableStack.getSingleTableTable().getTableName());
            function.addEnvironment(FirehoseS3AthenaEtlStore.ETL_BUCKET_PROP_NAME, ingestStack.getBucketEtl().getBucketName());
            function.addEnvironment(FirehoseS3AthenaEtlStore.FIREHOSE_STREAM_NAME_PROP_NAME, ingestStack.getFirehose().getDeliveryStreamName());
            function.addEnvironment(LambdaDeployerImpl.CUSTOMER_FUNCTION_PERMISSION_BOUNDARY_NAME_PROP_NAME, controlStack.getCustomerFunctionPermissionBoundaryManagedPolicy().getManagedPolicyName());
            function.addEnvironment(LambdaDeployerImpl.CODE_BUCKET_NAME_PROP_NAME, controlStack.getBucketCode().getBucketName());
            function.addEnvironment(DynamoApiGatewayApiAccessStore.USAGE_PLAN_ID_PROP_NAME, baseApiStack.getActiveUsagePlan().getUsagePlanId());
        }

        app.synth();
    }

    private DatasprayStack() {
        // disallow ctor
    }
}
