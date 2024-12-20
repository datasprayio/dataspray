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

package io.dataspray.cdk.template;

import com.google.common.collect.Maps;
import io.dataspray.common.DeployEnvironment;
import lombok.Getter;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.Value;
import software.amazon.awscdk.Duration;
import software.amazon.awscdk.services.lambda.Alias;
import software.amazon.awscdk.services.lambda.Architecture;
import software.amazon.awscdk.services.lambda.Code;
import software.amazon.awscdk.services.lambda.Function;
import software.amazon.awscdk.services.lambda.Runtime;
import software.amazon.awscdk.services.lambda.SnapStartConf;
import software.amazon.awscdk.services.lambda.eventsources.ApiEventSource;
import software.constructs.Construct;

import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

@Getter
public abstract class FunctionStack extends BaseStack {

    public static final String QUARKUS_LAMBDA_HANDLER = "io.quarkus.amazon.lambda.runtime.QuarkusStreamHandler::handleRequest";
    /**
     * For custom runtime lambda, filename that is expected to be inside the uploaded zip file
     *
     * @see <a
     * href="https://docs.aws.amazon.com/lambda/latest/dg/runtimes-walkthrough.html#runtimes-walkthrough-function">Create
     * a function</a>
     */
    private static final String CUSTOM_RUNTIME_BOOTSTRAP_FILENAME = "bootstrap";

    /**
     * Exposing all function names and functions.
     * <p>
     * Function names are exposed separately to allow another stack to use the function name without having to depend on
     * the function stack.
     */
    private final Map<String, FunctionAndAlias> functions = Maps.newHashMap();

    public FunctionStack(Construct parent, String constructIdSuffix, DeployEnvironment deployEnv) {
        super(parent, constructIdSuffix, deployEnv);
    }

    /**
     * Hybrid SingletonFunction of native or JVM code
     */
    protected FunctionAndAlias addSingletonFunction(
            String constructId,
            String baseFunctionName,
            String codeZip,
            long memorySize,
            long memorySizeNative) {
        String functionName = "dataspray-" + baseFunctionName + getDeployEnv().getSuffix();

        // Create the function builder
        Function.Builder functionBuilder = Function.Builder.create(this, constructId)
                .functionName(functionName)
                .code(Code.fromAsset(codeZip))
                .timeout(Duration.minutes(5));

        // Lambda differences between a native image and JVM
        if (detectIsNative(codeZip)) {
            functionBuilder
                    .memorySize(memorySizeNative)
                    .architecture(detectNativeArch())
                    // PROVIDED does not support arm64
                    .runtime(Runtime.PROVIDED_AL2023)
                    // Unused in native image
                    .handler("io.dataspray")
                    // Required for native image https://github.com/quarkusio/quarkus/issues/29331
                    .environment(Map.of("DISABLE_SIGNAL_HANDLERS", "true"))
                    // Taken from sam.native.yaml
                    .events(List.of(ApiEventSource.Builder.create("any", "/{proxy+}").build()));
        } else {
            functionBuilder
                    .memorySize(memorySize)
                    // Snap start is only available for x86_64, otherwise use ARM as it's cheaper
                    // https://docs.aws.amazon.com/lambda/latest/dg/snapstart.html#snapstart-runtimes
                    .architecture(Architecture.X86_64)
                    .snapStart(SnapStartConf.ON_PUBLISHED_VERSIONS)
                    .runtime(Runtime.JAVA_21)
                    .handler(QUARKUS_LAMBDA_HANDLER);
        }

        // Construct the function
        Function function = functionBuilder.build();

        // Update LIVE alias to point to latest version
        String aliasName = "live";
        function.getLatestVersion();
        Alias alias = function.addAlias(aliasName);

        FunctionAndAlias functionAndAlias = new FunctionAndAlias(functionName, aliasName, function, alias);
        this.functions.put(functionName, functionAndAlias);
        return functionAndAlias;
    }

    /**
     * Detects whether the provided zip file is a native image or JAR file.
     */
    @SneakyThrows
    private static boolean detectIsNative(String codeZip) {
        try (ZipFile zipFile = new ZipFile(codeZip)) {
            Enumeration<? extends ZipEntry> zipEntries = zipFile.entries();
            while (zipEntries.hasMoreElements()) {
                String fileName = zipEntries.nextElement().getName();
                if (CUSTOM_RUNTIME_BOOTSTRAP_FILENAME.equals(fileName)) {
                    return true;
                }
            }
        }
        return false;
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

    @Value
    public static class FunctionAndAlias {
        @NonNull
        String functionName;
        @NonNull
        String aliasName;
        @NonNull
        Function function;
        @NonNull
        Alias alias;
    }
}
