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

package io.dataspray.devenv;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import software.amazon.awscdk.Duration;
import software.amazon.awscdk.RemovalPolicy;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.TagProps;
import software.amazon.awscdk.Tags;
import software.amazon.awscdk.services.ec2.IVpc;
import software.amazon.awscdk.services.ec2.SecurityGroup;
import software.amazon.awscdk.services.ec2.Vpc;
import software.amazon.awscdk.services.ec2.VpcLookupOptions;
import software.amazon.awscdk.services.efs.FileSystem;
import software.amazon.awscdk.services.efs.LifecyclePolicy;
import software.amazon.awscdk.services.efs.OutOfInfrequentAccessPolicy;
import software.amazon.awscdk.services.efs.PerformanceMode;
import software.amazon.awscdk.services.lambda.Architecture;
import software.amazon.awscdk.services.lambda.Code;
import software.amazon.awscdk.services.lambda.FunctionUrl;
import software.amazon.awscdk.services.lambda.FunctionUrlAuthType;
import software.amazon.awscdk.services.lambda.FunctionUrlCorsOptions;
import software.amazon.awscdk.services.lambda.FunctionUrlOptions;
import software.amazon.awscdk.services.lambda.Handler;
import software.amazon.awscdk.services.lambda.Runtime;
import software.amazon.awscdk.services.lambda.SingletonFunction;
import software.constructs.Construct;

import java.util.UUID;

public class DevEnvRunnerStack extends Stack {
    public static final String STACK_ID_TAG_NAME = "dataspray-stack-id";
    private static final String STACK_ID_TAG_VALUE = "dev-env-runner";

    public DevEnvRunnerStack(Construct parent, String stackId, DevEnvImageRepoStack repoStack, String imageTag, StackProps props) {
        super(parent, stackId, props);

        IVpc vpcDefault = Vpc.fromLookup(this, stackId + "-vpc", VpcLookupOptions.builder()
                .isDefault(true).build());

        SecurityGroup sgFilesystem = SecurityGroup.Builder.create(this, stackId + "-efs-sg")
                .securityGroupName(stackId + "-efs-sg")
                .allowAllOutbound(true)
                .vpc(vpcDefault)
                .build();
        FileSystem fileSystem = FileSystem.Builder.create(this, stackId + "-efs")
                .fileSystemName(stackId + "-efs")
                .vpc(vpcDefault)
                .securityGroup(sgFilesystem)
                .encrypted(true)
                .performanceMode(PerformanceMode.GENERAL_PURPOSE)
                .lifecyclePolicy(LifecyclePolicy.AFTER_7_DAYS)
                .outOfInfrequentAccessPolicy(OutOfInfrequentAccessPolicy.AFTER_1_ACCESS)
                .enableAutomaticBackups(false)
                .removalPolicy(RemovalPolicy.RETAIN)
                .build();

        SingletonFunction function = SingletonFunction.Builder.create(this, stackId + "-lambda")
                .uuid(UUID.nameUUIDFromBytes(stackId.getBytes(Charsets.UTF_8)).toString())
                .functionName(stackId)
                .code(Code.fromEcrImage(repoStack.getRepo()))
                .handler(Handler.FROM_IMAGE)
                .runtime(Runtime.FROM_IMAGE)
                .vpc(vpcDefault)
                .allowAllOutbound(true)
                .architecture(Architecture.ARM_64)
                .memorySize(128)
                .timeout(Duration.minutes(15))
                .filesystem(software.amazon.awscdk.services.lambda.FileSystem
                        .fromEfsAccessPoint(fileSystem.addAccessPoint(stackId + "-efs-ap"), "/"))
                .build();

        FunctionUrl functionUrl = function.addFunctionUrl(FunctionUrlOptions.builder()
                .authType(FunctionUrlAuthType.NONE)
                .cors(FunctionUrlCorsOptions.builder()
                        .allowedOrigins(ImmutableList.of("console.dataspray.io")).build())
                .build());

        Tags.of(this).add(STACK_ID_TAG_NAME, STACK_ID_TAG_VALUE, TagProps.builder()
                .applyToLaunchedInstances(true)
                .priority(1000)
                .build());
    }
}
