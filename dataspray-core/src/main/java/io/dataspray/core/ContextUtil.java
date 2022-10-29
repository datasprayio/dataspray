package io.dataspray.core;

import com.google.common.base.Strings;
import com.samskivert.mustache.Mustache.CustomContext;
import com.samskivert.mustache.Mustache.Lambda;
import com.samskivert.mustache.Template;
import io.dataspray.common.StringUtil;
import io.dataspray.common.VersionUtil;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@ApplicationScoped
public class ContextUtil implements CustomContext {

    @Inject
    VersionUtil versionUtil;

    @Override
    public Object get(String name) throws Exception {
        switch (name) {
            case "templatesFolder":
                return CodegenImpl.TEMPLATES_FOLDER;
            case "dataFormatsFolder":
                return CodegenImpl.SCHEMAS_FOLDER;
            case "runnerVersion":
                return versionUtil.getVersion();
            case "lowerCamelCase":
                return (Lambda) (frag, out) -> out.write(StringUtil.camelCase(frag.execute(), false));
            case "upperCamelCase":
                return (Lambda) (frag, out) -> out.write(StringUtil.camelCase(frag.execute(), true));
            case "dataFormatFolderRelative":
                return Path.of("..", CodegenImpl.SCHEMAS_FOLDER);
            case "javaImportsFormat":
                return (Lambda) this::javaImportsFormat;
            case "trim":
                return (Lambda) (frag, out) -> out.write(frag.execute().strip());
            default:
                return null;
        }
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
