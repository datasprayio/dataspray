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
import com.google.common.collect.ImmutableSet;
import io.dataspray.common.test.TestResourceUtil;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.containers.localstack.LocalStackContainer.Service;
import org.testcontainers.utility.DockerImageName;

import java.util.Map;
import java.util.Optional;

import static com.google.common.base.Preconditions.checkState;

public abstract class AbstractLocalstackLifecycleManager implements QuarkusTestResourceLifecycleManager {

    private static final String LOCALSTACK_VERSION = "2.3.0";

    private Optional<LocalStackContainer> localStackContainerOpt;

    /**
     * Choose which services to enable. Ensure that the service is configured in
     * {@link AbstractLocalstackLifecycleManager#configureProperties}.
     *
     * @return the services to enable
     */
    protected abstract ImmutableSet<Service> enabledServices();

    @Override
    public final Map<String, String> start() {
        // Start Localstack container
        ImmutableSet<Service> services = enabledServices();
        LocalStackContainer container = new LocalStackContainer(DockerImageName
                .parse("localstack/localstack:" + LOCALSTACK_VERSION))
                .withServices(services.toArray(Service[]::new));
        localStackContainerOpt = Optional.of(container);
        container.start();

        // Common properties
        ImmutableMap.Builder<String, String> propsBuilder = ImmutableMap.builder();
        propsBuilder.put("startupWaitUntilDeps", "true");
        propsBuilder.put("aws.region", container.getRegion());
        propsBuilder.put("aws.credentials.accessKey", container.getAccessKey());
        propsBuilder.put("aws.credentials.secretKey", container.getSecretKey());

        // Service specific properties
        for (Service enabledService : enabledServices()) {
            propsBuilder.putAll(configureProperties(enabledService, container));
        }

        return propsBuilder.build();
    }

    @Override
    public final void stop() {
        localStackContainerOpt.ifPresent(GenericContainer::stop);
    }

    @Override
    public void inject(Object testInstance) {
        checkState(localStackContainerOpt.isPresent());
        TestResourceUtil.injectSelf(testInstance, localStackContainerOpt.get());
    }

    private ImmutableMap<String, String> configureProperties(Service service, LocalStackContainer container) {
        ImmutableMap.Builder<String, String> propsBuilder = ImmutableMap.builder();
        switch (service) {
            case DYNAMODB:
                propsBuilder.put("aws.dynamo.serviceEndpoint", container.getEndpointOverride(Service.DYNAMODB).toString());
                propsBuilder.put("aws.dynamo.productionRegion", container.getRegion());
                propsBuilder.put("singletable.createTableOnStartup", "true");
                break;
            case S3:
                propsBuilder.put("aws.s3.serviceEndpoint", container.getEndpointOverride(Service.S3).toString());
                propsBuilder.put("aws.s3.productionRegion", container.getRegion());
                propsBuilder.put("aws.s3.pathStyleEnabled", "true");
                break;
            case API_GATEWAY:
                propsBuilder.put("aws.apigateway.serviceEndpoint", container.getEndpointOverride(Service.API_GATEWAY).toString());
                propsBuilder.put("aws.apigateway.productionRegion", container.getRegion());
                break;
            default:
                throw new RuntimeException("Service " + service + " not supported");
        }
        return propsBuilder.build();
    }
}
