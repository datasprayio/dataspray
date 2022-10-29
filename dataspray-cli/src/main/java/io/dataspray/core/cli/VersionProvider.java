package io.dataspray.core.cli;

import io.dataspray.common.VersionUtil;
import picocli.CommandLine.IVersionProvider;

public class VersionProvider implements IVersionProvider {

    private final VersionUtil versionUtil = new VersionUtil();

    @Override
    public String[] getVersion() throws Exception {
        return new String[]{versionUtil.getVersion()};
    }
}
