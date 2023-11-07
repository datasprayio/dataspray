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
        SingletonFunction.Builder functionBuilder = SingletonFunction.Builder.create(this, getConstructId("lambda"))
                .uuid(UUID.nameUUIDFromBytes(getConstructId("lambda").getBytes(Charsets.UTF_8)).toString())
                .functionName(functionName)
                .code(Code.fromAsset(options.getCodeZip()))
                .architecture(Architecture.ARM_64)
                .memorySize(options.getMemorySize())
                .timeout(Duration.seconds(30));
        if (IS_NATIVE) {
            functionBuilder
                    .runtime(Runtime.PROVIDED)
                    // Unused in native image
                    .handler("io.dataspray")
                    // Required for native image https://github.com/quarkusio/quarkus/issues/29331
                    .environment(Map.of("DISABLE_SIGNAL_HANDLERS", "true"))
                    // Taken from sam.native.yaml
                    .events(List.of(ApiEventSource.Builder.create("any", "/{proxy+}").build()));
        } else {
            functionBuilder
                    .runtime(Runtime.JAVA_11)
                    .handler(QUARKUS_LAMBDA_HANDLER);
        }
        function = functionBuilder.build();
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
