package io.dataspray.backend.deploy;

import lombok.extern.slf4j.Slf4j;
import software.amazon.awscdk.App;

@Slf4j
public class BackendDeploy {
    public static void main(String[] args) {
        App app = new App();
        new DnsStack(app);
        app.synth();
    }

    private BackendDeploy() {
        // disalow ctor
    }
}
