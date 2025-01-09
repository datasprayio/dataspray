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
import io.dataspray.aws.cdk.AwsCdk;
import io.dataspray.aws.cdk.CloudFormationClientProvider;
import io.dataspray.aws.cdk.EnvironmentResolver;
import io.dataspray.aws.cdk.ResolvedEnvironment;
import io.dataspray.aws.cdk.Stacks;
import io.dataspray.common.DeployEnvironment;
import io.dataspray.common.test.AbstractTest;
import io.dataspray.common.test.aws.MotoInstance;
import io.dataspray.common.test.aws.MotoLifecycleManager;
import io.dataspray.store.util.IdUtil;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.testcontainers.shaded.com.google.common.collect.ImmutableMap;
import software.amazon.awscdk.cxapi.CloudAssembly;
import software.amazon.awscdk.cxapi.CloudFormationStackArtifact;
import software.amazon.awssdk.services.cloudformation.CloudFormationClient;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Slf4j
@ExtendWith(MotoLifecycleManager.class)
class DatasprayStackTest extends AbstractTest {

    MotoInstance motoInstance;
    @TempDir
    public Path tempDir;
    IdUtil idUtil = new IdUtil();

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = DeployEnvironment.class, mode = EnumSource.Mode.EXCLUDE, names = {"TEST"})
    void test(DeployEnvironment deployEnv) throws Exception {
        boolean isNative = switch (deployEnv) {
            case PRODUCTION, STAGING -> true;
            case SELFHOST -> false;
            default -> throw new IllegalStateException("Unexpected value: " + deployEnv);
        };
        CloudAssembly cloudAssembly = DatasprayStack.synth(
                deployEnv.name(),
                mockFunctionZip("authorizer", isNative).toString(),
                mockFunctionZip("control", isNative).toString(),
                mockFunctionZip("ingest", isNative).toString(),
                mockSsgSite("landing").toString(),
                mockSsgSite("docs").toString(),
                mockSsgSite("dashboard").toString());

        // Assert expected stack names
        Set<String> expectedStackBaseNames = Sets.newHashSet(
                "dataspray-authnz",
                "dataspray-dns",
                "dataspray-singletable",
                "dataspray-api-gateway",
                "dataspray-site-dashboard",
                "dataspray-site-docs",
                "dataspray-web-control",
                "dataspray-web-ingest");
        if (deployEnv != DeployEnvironment.SELFHOST) {
            expectedStackBaseNames.add("dataspray-site-landing");
        }
        Set<String> expectedStackNames = expectedStackBaseNames.stream()
                .map(stackName -> stackName + deployEnv.getSuffix())
                .collect(ImmutableSet.toImmutableSet());
        assertEquals(expectedStackNames, cloudAssembly.getStacks()
                .stream()
                .map(CloudFormationStackArtifact::getStackName)
                .collect(ImmutableSet.toImmutableSet()));

        // AWS CDK doesn't like resolving localhost
        String endpoint = motoInstance.getEndpoint().replace("localhost", "127.0.0.1");
        CloudFormationClient cloudFormationClient = getCloudFormationClient(endpoint, "aws://unknown-account/unknown-region");

        // Bootstrap
        AwsCdk.bootstrap().execute(cloudAssembly, AwsCdk.DEFAULT_TOOLKIT_STACK_NAME, expectedStackNames, null, null, Optional.of("moto"), Optional.of(endpoint));
        log.info("Stack {}: {}", AwsCdk.DEFAULT_TOOLKIT_STACK_NAME, Stacks.findStack(cloudFormationClient, AwsCdk.DEFAULT_TOOLKIT_STACK_NAME));

        // Deploy
        Set<String> deployStackBaseNames = expectedStackBaseNames.stream()
                // TODO Missing Moto support
                .filter(Predicate.not("dataspray-authnz"::equals))
                // TODO Missing Moto support
                .filter(Predicate.not("dataspray-api-gateway"::equals))
                // TODO Dependency of dataspray-api-gateway above
                .filter(Predicate.not("dataspray-site-dashboard"::equals))
                .filter(Predicate.not("dataspray-site-docs"::equals))
                .filter(Predicate.not("dataspray-site-landing"::equals))
                .filter(Predicate.not("dataspray-web-control"::equals))
                .filter(Predicate.not("dataspray-web-ingest"::equals))
                .collect(ImmutableSet.toImmutableSet());
        Set<String> deployStackNames = deployStackBaseNames.stream()
                .map(stackName -> stackName + deployEnv.getSuffix())
                .collect(Collectors.toSet());
        AwsCdk.deploy().execute(cloudAssembly, AwsCdk.DEFAULT_TOOLKIT_STACK_NAME, deployStackNames, Map.of("dnsDomain", "example.dataspray.io"), null, ImmutableSet.of(), Optional.of("moto"), Optional.of(endpoint));
        deployStackNames.forEach(stackName -> log.info("Stack {}: {}",
                AwsCdk.DEFAULT_TOOLKIT_STACK_NAME, Stacks.findStack(cloudFormationClient, stackName)));

        // Destroy toolkit
        AwsCdk.destroy().execute(cloudAssembly, Set.of(AwsCdk.DEFAULT_TOOLKIT_STACK_NAME), Optional.of("moto"), Optional.of(endpoint));

        // Destroy
        Set<String> destroyStackNames = expectedStackBaseNames.stream()
                // TODO Moto seems to have created these stacks in some failed state and cannot destroy them
                .filter(Predicate.not("dataspray-singletable"::equals))
                .filter(Predicate.not("dataspray-dns"::equals))
                .map(stackName -> stackName + deployEnv.getSuffix())
                .collect(Collectors.toSet());
        AwsCdk.destroy().execute(cloudAssembly, destroyStackNames, Optional.of("moto"), Optional.of(endpoint));
    }

    private CloudFormationClient getCloudFormationClient(String endpoint, String environment) {
        ResolvedEnvironment resolvedEnvironment = EnvironmentResolver.create(null, Optional.of(endpoint))
                .resolve(environment);
        return CloudFormationClientProvider.get(resolvedEnvironment);
    }

    private File mockFunctionZip(String name, boolean isNative) throws Exception {
        return isNative
                ? mockNativeFunctionZip(name)
                : mockJavaFunctionZip(name);
    }

    private File mockNativeFunctionZip(String name) throws Exception {
        return mockZipFile(
                name,
                ImmutableMap.of(Path.of("bootstrap"), ""));
    }

    private File mockJavaFunctionZip(String name) throws Exception {
        return mockZipFile(
                name,
                ImmutableMap.of(
                        Path.of("META-INF", "MANIFEST.MF"), "Manifest-Version: 1.0",
                        Path.of("io", "dataspray", "main.class"), ""));
    }

    Path mockSsgSite(String name) throws Exception {
        return mockDir(
                name,
                ImmutableMap.of(
                        Path.of(".next", "routes-manifest.json"), getTestResource("routes-manifest.json"),
                        Path.of("out", "index.html"), "<html><body><h1>Hello World!</h1></body></html>")
        );
    }

    Path mockDir(String name, ImmutableMap<Path, String> filenameToContent) throws Exception {
        Path dir = tempDir.resolve(name);
        for (Map.Entry<Path, String> entry : filenameToContent.entrySet()) {
            Path file = dir.resolve(entry.getKey());
            file.getParent().toFile().mkdirs();
            Files.writeString(file, entry.getValue(), StandardOpenOption.CREATE, StandardOpenOption.CREATE);
        }
        return dir;
    }

    File mockZipFile(String name, ImmutableMap<Path, String> filenameToContent) throws Exception {
        File zipFile = tempDir.resolve(name + ".zip").toFile();
        try (FileOutputStream fout = new FileOutputStream(zipFile);
             ZipOutputStream zout = new ZipOutputStream(fout)) {
            for (Map.Entry<Path, String> entry : filenameToContent.entrySet()) {
                ZipEntry ze = new ZipEntry(entry.getKey().toString());
                zout.putNextEntry(ze);
                zout.write(entry.getValue().getBytes());
                zout.closeEntry();
            }
        }
        return zipFile;
    }
}