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

import lombok.extern.slf4j.Slf4j;
import software.amazon.awscdk.App;
import software.amazon.awscdk.Environment;
import software.amazon.awscdk.StackProps;

import java.util.Optional;

@Slf4j
public class DevEnvManagerImpl implements DevEnvManager {
    @Override
    public DevEnv create(String stackId, String imageTag) {
        // TODO
        // - Create Lambda as container image
        // - EFS storage
        // - Function Endpoint
        // - Update CloudFront
        // TODO Create
        // Creation via SDK as oppose to CDK
        //        CreateFunctionResponse function = LambdaClientProvider.get().createFunction(CreateFunctionRequest.builder()
        //                .functionName("")
        //                .description("")
        //                .packageType(PackageType.IMAGE)
        //                .architectures(Architecture.ARM64)
        //                .runtime(Runtime.PYTHON3_9)
        //                .memorySize(128)
        //                .timeout(Duration.seconds(30))
        //                .fileSystemConfigs(FileSystemConfig.builder()
        //                        .arn("")
        //                        .localMountPath("")
        //                        .build())
        //                .build());

        String defaultAccount = System.getenv("CDK_DEFAULT_ACCOUNT");
        log.info("Creating dev env {} on account {}", stackId, defaultAccount);
        StackProps stackProps = StackProps.builder()
                .env(Environment.builder()
                        .account(defaultAccount)
                        .region("us-east-1")
                        .build())
                .build();
        App app = new App();
        DevEnvImageRepoStack imageRepoStack = new DevEnvImageRepoStack(app, "dev-env-image-repo", stackProps);
        DevEnvRunnerStack runnerStack = new DevEnvRunnerStack(app, stackId, imageRepoStack, imageTag, stackProps);
        app.synth();

        return new DevEnv(stackId);
    }

    @Override
    public DevEnv get(String id) {
        return null;
    }

    @Override
    public Page list(String id, Optional<String> cursorOpt) {
        return null;
    }

    @Override
    public void update(String id) {

    }

    @Override
    public void recreate(String id) {

    }

    @Override
    public void teardown(String id) {

    }
}
