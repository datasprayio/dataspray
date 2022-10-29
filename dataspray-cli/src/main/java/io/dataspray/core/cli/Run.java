package io.dataspray.core.cli;

import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;

@Slf4j
@Command(name = "run", description = "deploy and manage running task(s)", subcommandsRepeatable = true, subcommands = {
        RunDeploy.class,
        RunUpload.class,
        RunPublish.class,
        RunActivate.class,
        RunPause.class,
        RunResume.class,
        RunDelete.class,
        RunList.class,
        RunStatus.class
})
public class Run {
    @Mixin
    LoggingMixin loggingMixin;
}
