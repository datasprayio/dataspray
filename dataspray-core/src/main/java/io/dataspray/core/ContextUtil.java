/*
 * Copyright 2023 Matus Faro
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package io.dataspray.core;

import com.google.common.base.Strings;
import com.samskivert.mustache.Mustache.CustomContext;
import com.samskivert.mustache.Mustache.Lambda;
import com.samskivert.mustache.Template;
import io.dataspray.common.StringUtil;
import io.dataspray.common.VersionUtil;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

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
