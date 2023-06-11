package io.dataspray.common.aws.test;

import com.google.common.collect.ImmutableMap;
import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;

public class SingleTenantAccountStoreProfile implements QuarkusTestProfile {

    public Map<String, String> getConfigOverrides() {
        return ImmutableMap.of("accountstore.singletenant.enable", "true");
    }
}
