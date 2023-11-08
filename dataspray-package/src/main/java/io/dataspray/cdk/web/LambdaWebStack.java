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

package io.dataspray.cdk.web;

import com.google.common.base.Charsets;
import io.dataspray.cdk.template.BaseStack;
import io.dataspray.common.DeployEnvironment;
import lombok.Getter;
import lombok.NonNull;
import lombok.Value;
import software.amazon.awscdk.Duration;
import software.amazon.awscdk.services.lambda.Architecture;
import software.amazon.awscdk.services.lambda.Code;
import software.amazon.awscdk.services.lambda.Runtime;
import software.amazon.awscdk.services.lambda.SingletonFunction;
import software.amazon.awscdk.services.lambda.eventsources.ApiEventSource;
import software.constructs.Construct;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Getter
public abstract class LambdaWebStack extends BaseStack {

    private static final boolean IS_NATIVE = true;
    private static final String QUARKUS_LAMBDA_HANDLER = "io.quarkus.amazon.lambda.runtime.QuarkusStreamHandler::handleRequest";

    private final String functionName;
    private final SingletonFunction function;

    public LambdaWebStack(Construct parent, Options options) {
        super(parent, "web-" + options.getFunctionName(), options.getDeployEnv());

        functionName = options.getFunctionName();
        function = getSingletonFunctionBuilder(
                this,
                getConstructId("lambda"),
                functionName,
                options.getCodeZip(),
                options.getMemorySize(),
                QUARKUS_LAMBDA_HANDLER);
    }

    /**
     * Hybrid SingletonFunction of native or JVM code
     */
    static SingletonFunction getSingletonFunctionBuilder(
            Construct scope,
            String constructId,
            String functionName,
            String codeZip,
            int memorySize,
            String jvmHandler) {
        SingletonFunction.Builder functionBuilder = SingletonFunction.Builder.create(scope, constructId)
                .uuid(UUID.nameUUIDFromBytes(constructId.getBytes(Charsets.UTF_8)).toString())
                .functionName(functionName)
                .code(Code.fromAsset(codeZip))
                .memorySize(memorySize)
                .timeout(Duration.seconds(30));
        if (IS_NATIVE) {
            functionBuilder
                    .architecture(detectNativeArch())
                    // PROVIDED does not support arm64
                    .runtime(Runtime.PROVIDED_AL2)
                    // Unused in native image
                    .handler("io.dataspray")
                    // Required for native image https://github.com/quarkusio/quarkus/issues/29331
                    .environment(Map.of("DISABLE_SIGNAL_HANDLERS", "true"))
                    // Taken from sam.native.yaml
                    .events(List.of(ApiEventSource.Builder.create("any", "/{proxy+}").build()));
        } else {
            functionBuilder
                    // For JVM default to ARM as it's cheaper
                    .architecture(Architecture.ARM_64)
                    .runtime(Runtime.JAVA_11)
                    .handler(jvmHandler);
        }
        return functionBuilder.build();
    }

    private static Architecture detectNativeArch() {
        // Good enough
        String osArch = System.getProperty("os.arch", "");
        if (osArch.equalsIgnoreCase("aarch64")
            || osArch.contains("arm")) {
            return Architecture.ARM_64;
        } else if (osArch.contains("amd")
                   || osArch.contains("x86")) {
            return Architecture.X86_64;
        } else {
            throw new RuntimeException("Unknown architecture: " + osArch);
        }
    }

    @Value
    @lombok.Builder
    public static class Options {
        @NonNull
        DeployEnvironment deployEnv;
        @NonNull
        String functionName;
        @NonNull
        String codeZip;
        @lombok.Builder.Default
        int memorySize = 512;
    }
}
