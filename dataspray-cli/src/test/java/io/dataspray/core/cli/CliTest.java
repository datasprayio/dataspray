package io.dataspray.core.cli;

import io.quarkus.test.junit.main.Launch;
import io.quarkus.test.junit.main.QuarkusMainTest;
import org.junit.jupiter.api.Test;

@QuarkusMainTest
public class CliTest {
    @Test
    @Launch(value = {}, exitCode = 2)
    public void test() throws Exception {
    }

    // TODO https://quarkus.io/guides/command-mode-reference#testing-command-mode-applications
}
