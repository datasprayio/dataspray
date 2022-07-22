package io.dataspray.devenv;

import com.google.common.collect.ImmutableList;
import lombok.Value;

import java.util.Optional;

public interface DevEnvManager {

    DevEnv create(String id);

    DevEnv get(String id);

    Page list(String id, Optional<String> cursorOpt);

    void update(String id);

    void recreate(String id);

    void teardown(String id);

    @Value
    class DevEnv {
        String id;
    }

    @Value
    class Page {
        ImmutableList<DevEnv> devEnvs;
        Optional<String> cursorOpt;
    }
}
