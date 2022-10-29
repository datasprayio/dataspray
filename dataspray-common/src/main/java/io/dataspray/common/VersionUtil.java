package io.dataspray.common;

import com.google.common.base.Strings;

import javax.enterprise.context.ApplicationScoped;
import java.util.Optional;

@ApplicationScoped
public class VersionUtil {

    public String getVersion() {
        return Optional.ofNullable(Strings.emptyToNull(getClass().getPackage().getImplementationVersion()))
                .or(() -> Optional.ofNullable(Strings.emptyToNull(System.getenv("PROJECT_VERSION"))))
                .orElse("UNKNOWN");
    }
}
