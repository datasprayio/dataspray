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

package io.dataspray.common.test.aws;

import com.google.common.collect.ImmutableMap;
import io.dataspray.common.NetworkUtil;
import io.dataspray.common.test.TestResourceUtil;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.testcontainers.containers.FixedHostPortGenericContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.InternetProtocol;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static com.google.common.base.Preconditions.checkState;

public class MotoLifecycleManager implements QuarkusTestResourceLifecycleManager {

    private static final String MOTO_VERSION = "4.2.6";

    private Optional<MotoInstance> instanceOpt;

    @Override
    public final Map<String, String> start() {
        String region = "us-east-1";
        String awsAccessKey = UUID.randomUUID().toString();
        String awsSecretKey = UUID.randomUUID().toString();
        // Setup Moto container
        int port = NetworkUtil.get().findFreePort();
        String endpoint = "http://localhost:" + port;
        @SuppressWarnings("rawtypes")
        GenericContainer motoContainer = new FixedHostPortGenericContainer(
                "motoserver/moto:" + MOTO_VERSION)
                .withFixedExposedPort(port, port, InternetProtocol.TCP)
                .withCommand("ec2")
                .withEnv("AWS_ACCESS_KEY_ID", awsAccessKey)
                .withEnv("AWS_SECRET_ACCESS_KEY", awsSecretKey)
                .withEnv("AWS_DEFAULT_REGION", region)
                .withEnv("MOTO_PORT", String.valueOf(port))
                .withEnv("MOTO_PRETTIFY_RESPONSES", "True");
        instanceOpt = Optional.of(new MotoInstance(
                region,
                awsAccessKey,
                awsSecretKey,
                port,
                endpoint,
                motoContainer));

        // Start container
        motoContainer.start();

        // Common properties
        ImmutableMap.Builder<String, String> propsBuilder = ImmutableMap.builder();
        propsBuilder.put("startupWaitUntilDeps", "true");
        propsBuilder.put("aws.region", region);
        propsBuilder.put("aws.credentials.accessKey", awsAccessKey);
        propsBuilder.put("aws.credentials.secretKey", awsSecretKey);

        // Dynamo properties
        propsBuilder.put("aws.dynamo.serviceEndpoint", endpoint);
        propsBuilder.put("aws.dynamo.productionRegion", region);
        propsBuilder.put("singletable.createTableOnStartup", "true");

        // S3 properties
        propsBuilder.put("aws.s3.serviceEndpoint", endpoint);
        propsBuilder.put("aws.s3.productionRegion", region);
        propsBuilder.put("aws.s3.pathStyleEnabled", "true");

        // API Gateway properties
        propsBuilder.put("aws.apigateway.serviceEndpoint", endpoint);
        propsBuilder.put("aws.apigateway.productionRegion", region);

        // Athena properties
        propsBuilder.put("aws.athena.serviceEndpoint", endpoint);
        propsBuilder.put("aws.athena.productionRegion", region);

        // Cognito properties
        propsBuilder.put("aws.cognito.serviceEndpoint", endpoint);
        propsBuilder.put("aws.cognito.productionRegion", region);

        // Firehose properties
        propsBuilder.put("aws.firehose.serviceEndpoint", endpoint);
        propsBuilder.put("aws.firehose.productionRegion", region);

        // Glue properties
        propsBuilder.put("aws.glue.serviceEndpoint", endpoint);
        propsBuilder.put("aws.glue.productionRegion", region);

        // Lambda properties
        propsBuilder.put("aws.lambda.serviceEndpoint", endpoint);
        propsBuilder.put("aws.lambda.productionRegion", region);

        // SQS properties
        propsBuilder.put("aws.sqs.serviceEndpoint", endpoint);
        propsBuilder.put("aws.sqs.productionRegion", region);

        // IAM properties
        propsBuilder.put("aws.iam.serviceEndpoint", endpoint);
        propsBuilder.put("aws.iam.productionRegion", region);

        return propsBuilder.build();
    }

    @Override
    public final void stop() {
        instanceOpt
                .map(MotoInstance::getMotoContainer)
                .ifPresent(GenericContainer::stop);
    }

    /**
     * Injects an instance of {@link MotoInstance} into test
     */
    @Override
    public void inject(Object testInstance) {
        checkState(instanceOpt.isPresent());
        TestResourceUtil.injectSelf(testInstance, instanceOpt.get());
    }
}
