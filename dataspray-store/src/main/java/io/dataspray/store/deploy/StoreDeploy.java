package io.dataspray.store.deploy;

import lombok.extern.slf4j.Slf4j;
import software.amazon.awscdk.App;

@Slf4j
public class StoreDeploy {
    public static final String STACK_NAME = "store";

    public static void main(String[] args) {
        App app = new App();
        new SingleTableStack(app, STACK_NAME);
        new AuthNzStack(app, STACK_NAME);
        app.synth();
    }

    private StoreDeploy() {
        // disalow ctor
    }
}
