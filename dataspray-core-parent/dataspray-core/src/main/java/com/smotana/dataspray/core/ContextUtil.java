package com.smotana.dataspray.core;

import com.google.common.base.Strings;
import com.google.inject.Singleton;
import com.samskivert.mustache.Mustache.CustomContext;
import com.samskivert.mustache.Mustache.Lambda;
import com.smotana.dataspray.core.util.StringUtil;

import java.nio.file.Path;
import java.util.Optional;

@Singleton
public class ContextUtil implements CustomContext {

    @Override
    public Object get(String name) throws Exception {
        return switch (name) {
            case "templatesFolder" -> CodegenImpl.TEMPLATES_FOLDER;
            case "dataFormatsFolder" -> CodegenImpl.DATA_FORMATS_FOLDER;
            case "runnerVersion" -> Optional.ofNullable(Strings.emptyToNull(getClass().getPackage().getImplementationVersion()))
                    .or(() -> Optional.ofNullable(Strings.emptyToNull(System.getenv("PROJECT_VERSION"))))
                    .orElseThrow(() -> new RuntimeException("Cannot determine runner version"));
            case "lowerCamelCase" -> (Lambda) (frag, out) -> out.write(StringUtil.camelCase(frag.execute(), false));
            case "upperCamelCase" -> (Lambda) (frag, out) -> out.write(StringUtil.camelCase(frag.execute(), true));
            case "dataFormatFolderRelative" -> Path.of("..", CodegenImpl.DATA_FORMATS_FOLDER);
            default -> null;
        };
    }

}
