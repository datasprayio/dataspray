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

package io.dataspray.authorizer;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.auth.InstanceProfileCredentialsProvider;
import com.amazonaws.retry.PredefinedBackoffStrategies;
import com.amazonaws.retry.PredefinedRetryPolicies;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.impl.RuntimeClassInitializationSupport;

public class NativeConfig implements Feature {

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        RuntimeClassInitializationSupport rci = ImageSingletons.lookup(RuntimeClassInitializationSupport.class);
        rci.rerunInitialization(PredefinedBackoffStrategies.class, "Contains Random instance in STANDARD_BACKOFF_STRATEGY");
        rci.rerunInitialization(PredefinedRetryPolicies.class, "Contains Random instance in DEFAULT_BACKOFF_STRATEGY -> EqualJitterBackoffStrategy");
        rci.rerunInitialization(ClientConfiguration.class, "Contains Random instance in DEFAULT_RETRY_POLICY");

        rci.rerunInitialization("com.amazonaws.auth.BaseCredentialsFetcher", "Contains Random instance in JITTER");
        rci.initializeAtRunTime("com.amazonaws.auth.ContainerCredentialsFetcher", "Concrete class of BaseCredentialsFetcher");
        rci.initializeAtRunTime("com.amazonaws.auth.InstanceMetadataServiceCredentialsFetcher", "Concrete class of BaseCredentialsFetcher");
        rci.initializeAtRunTime(DefaultAWSCredentialsProviderChain.class, "Otherwise causes build time of InstanceMetadataServiceCredentialsFetcher");
        rci.initializeAtRunTime(InstanceProfileCredentialsProvider.class, "Otherwise causes build time of InstanceMetadataServiceCredentialsFetcher");
    }

}
