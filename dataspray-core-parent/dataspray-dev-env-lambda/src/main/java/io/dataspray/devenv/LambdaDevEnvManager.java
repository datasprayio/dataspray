package io.dataspray.devenv;

import java.util.Optional;

public class LambdaDevEnvManager implements DevEnvManager {
    @Override
    public DevEnv create(String id) {
        // TODO
        // - Create Lambda as container image
        // - EFS storage
        // - Function Endpoint
        // - Update CloudFront
        // TODO Create
        return null;
    }

    @Override
    public DevEnv get(String id) {
        return null;
    }

    @Override
    public Page list(String id, Optional<String> cursorOpt) {
        return null;
    }

    @Override
    public void update(String id) {

    }

    @Override
    public void recreate(String id) {

    }

    @Override
    public void teardown(String id) {

    }
}
