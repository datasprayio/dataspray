package io.dataspray.common.aws.test;

import com.google.common.collect.ImmutableMap;
import io.dataspray.common.NetworkUtil;
import io.findify.s3mock.S3Mock;
import io.quarkus.test.junit.QuarkusTestProfile;
import software.amazon.awssdk.regions.Region;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Instance;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.util.Map;

@ApplicationScoped
public class S3ClientTestProfile implements QuarkusTestProfile {
    public static final String S3_MOCK_PORT = "S3_MOCK_PORT";

    @Inject
    NetworkUtil networkUtil;
    @Inject
    @Named(S3_MOCK_PORT)
    Instance<Integer> port;

    @Override
    public Map<String, String> getConfigOverrides() {
        return ImmutableMap.of(
                "aws.s3.serviceEndpoint", "http://localhost:" + port.get(),
                "aws.s3.productionRegion", Region.US_EAST_1.id(),
                "aws.s3.signingRegion", Region.US_EAST_1.id(),
                "aws.s3.dnsResolverTo", "localhost");
    }

    @Singleton
    @Named(S3_MOCK_PORT)
    public Integer get() {
        int port = networkUtil.findFreePort();
        S3Mock s3Mock = new S3Mock.Builder().withPort(port).withInMemoryBackend().build();
        s3Mock.start();
        return port;
    }
}
