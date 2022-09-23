package io.dataspray.common.aws.test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.dataspray.common.NetworkUtil;
import io.findify.s3mock.S3Mock;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import io.quarkus.test.junit.QuarkusTestProfile;
import lombok.SneakyThrows;
import software.amazon.awssdk.regions.Region;

import java.lang.reflect.Field;
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
        Class<?> c = testInstance.getClass();
        while (c != Object.class) {
            for (Field f : c.getDeclaredFields()) {
                if (S3Mock.class.isAssignableFrom(f.getType())) {
                    f.setAccessible(true);
                    f.set(testInstance, s3Mock);
                    return;
                }
            }
            c = c.getSuperclass();
        }
    }

    public static class TestProfile implements QuarkusTestProfile {

        @Override
        public List<TestResourceEntry> testResources() {
            return ImmutableList.of(new TestResourceEntry(
                    MockS3Client.class,
                    ImmutableMap.of(),
                    true));
        }
    }
}
