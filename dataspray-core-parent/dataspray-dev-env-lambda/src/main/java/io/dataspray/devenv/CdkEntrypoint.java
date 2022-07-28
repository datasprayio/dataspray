package io.dataspray.devenv;

import com.google.common.base.Strings;
import io.dataspray.devenv.DevEnvManager.DevEnv;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CdkEntrypoint {
    /** CDK entrypoint */
    public static void main(String[] args) {
        if (args.length != 2 || Strings.isNullOrEmpty(args[0])) {
            log.error("Usage: <stack_id> <ecr_image_tag>");
            System.exit(1);
        }
        String stackId = args[0];
        String imageTag = args[1];

        DevEnvManager devEnvManager = new DevEnvManagerImpl();
        DevEnv devEnv = devEnvManager.create(stackId, imageTag);
        log.info("Created dev env: {}", devEnv);
    }
}
