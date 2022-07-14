package com.smotana.dataspray.core;

import com.google.common.base.Strings;
import com.google.inject.Singleton;
import com.samskivert.mustache.Mustache.CustomContext;
import com.samskivert.mustache.Mustache.Lambda;
import com.samskivert.mustache.Template;
import com.smotana.dataspray.core.util.StringUtil;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Singleton
public class ContextUtil implements CustomContext {

    @Override
    public Object get(String name) throws Exception {
        return switch (name) {
            case "templatesFolder" -> CodegenImpl.TEMPLATES_FOLDER;
            case "dataFormatsFolder" -> CodegenImpl.SCHEMAS_FOLDER;
            case "runnerVersion" -> Optional.ofNullable(Strings.emptyToNull(getClass().getPackage().getImplementationVersion()))
                    .or(() -> Optional.ofNullable(Strings.emptyToNull(System.getenv("PROJECT_VERSION"))))
                    .orElseThrow(() -> new RuntimeException("Cannot determine runner version"));
            case "lowerCamelCase" -> (Lambda) (frag, out) -> out.write(StringUtil.camelCase(frag.execute(), false));
            case "upperCamelCase" -> (Lambda) (frag, out) -> out.write(StringUtil.camelCase(frag.execute(), true));
            case "dataFormatFolderRelative" -> Path.of("..", CodegenImpl.SCHEMAS_FOLDER);
            case "javaImportsFormat" -> (Lambda) this::javaImportsFormat;
            case "trim" -> (Lambda) (frag, out) -> out.write(frag.execute().strip());
            default -> null;
        };
    }

    private void javaImportsFormat(Template.Fragment frag, Writer out) throws IOException {
        String importsStr = frag.execute();
        if (Strings.isNullOrEmpty(importsStr)) {
            return;
        }
        String importsSortedUnique = Arrays.stream(importsStr.split("\\n"))
                .filter(Predicate.not(Strings::isNullOrEmpty))
                .sorted()
                .distinct()
                .collect(Collectors.joining("\n"));
        out.write(importsSortedUnique);
        out.write("\n");
    }
}
