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

package io.dataspray.common.aws.test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.dataspray.common.NetworkUtil;
import io.dataspray.common.TestResourceUtil;
import io.findify.s3mock.S3Mock;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import io.quarkus.test.junit.QuarkusTestProfile;
import lombok.SneakyThrows;
import software.amazon.awssdk.regions.Region;

import java.util.List;
import java.util.Map;

public class MockS3Client implements QuarkusTestResourceLifecycleManager {
    public static final Region REGION = Region.US_EAST_1;

    private S3Mock s3Mock;

    @Override
    public Map<String, String> start() {
        int port = NetworkUtil.get().findFreePort();
        s3Mock = new S3Mock.Builder().withPort(port).withInMemoryBackend().build();
        s3Mock.start();
        return ImmutableMap.of(
                "aws.s3.serviceEndpoint", "http://localhost:" + port,
                "aws.s3.productionRegion", REGION.id(),
                "aws.s3.dnsResolverTo", "localhost");
    }

    @Override
    public void stop() {
        s3Mock.stop();
    }

    @Override
    @SneakyThrows
    public void inject(Object testInstance) {
        TestResourceUtil.injectSelf(testInstance, s3Mock);
    }

    public static class Profile implements QuarkusTestProfile {

        @Override
        public List<TestResourceEntry> testResources() {
            return ImmutableList.of(new TestResourceEntry(
                    MockS3Client.class,
                    ImmutableMap.of(),
                    true));
        }
    }
}
