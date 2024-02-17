/*
 * Copyright 2024 Matus Faro
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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.io.Resources;
import io.dataspray.common.json.GsonUtil;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Given a template, we need to iterate over all template files and generate code. The template files may be on disk or
 * in resources. It is not easy to iterate all
 * resource files. This convenience wrapper allows us to iterate files wherever they are.
 * <p>
 * For more details: When resources are accessed from tests, the returned URL points to the disk location which is
 * easily walkable. Once bundled in a jar, a ZipFileSystem can be used but it doesn't work well with an executable
 * Jar file. Once we move to native executable, who knows what challenges lie ahead.
 * <p>
 * As a workaround, we generate the file structure for each template and read it during runtime to stream the files
 * as needed.
 */
@Slf4j
@Value
public class TemplateFiles {
    Template template;
    transient Optional<Path> templateDirOpt;
    transient ImmutableMap<TemplateType, ImmutableSet<TemplateFile>> filesByType;


    /**
     * Template files backed by bundled resources
     */
    TemplateFiles(Template template) {
        this(template, null);
    }

    /**
     * Template files backed by on-disk resources
     */
    TemplateFiles(Template template, Path templateDir) {
        this.template = template;
        this.templateDirOpt = Optional.ofNullable(templateDir);
        this.filesByType = walkFiles();
    }

    @SneakyThrows
    private ImmutableMap<TemplateType, ImmutableSet<TemplateFile>> walkFiles() {
        EnumMap<TemplateType, Set<TemplateFile>> filesByType = new EnumMap<>(TemplateType.class);

        ImmutableSet<TemplateFile> files;
        if (templateDirOpt.isEmpty()) {
            String treeFilePath = "template/" + getTreeFileName();
            try (Reader reader = new InputStreamReader(Resources.getResource(treeFilePath).openStream())) {
                files = GsonUtil.get()
                        .fromJson(reader, Tree.class)
                        .getPaths()
                        .stream()
                        .map(Path::of)
                        .map(TemplateFile::new)
                        .collect(ImmutableSet.toImmutableSet());
            }
        } else {
            try (var walk = Files.walk(templateDirOpt.get())) {
                files = walk
                        .skip(1) // Skip self
                        .filter(Files::isRegularFile)
                        .map(templateDirOpt.get()::relativize)
                        .map(TemplateFile::new)
                        .collect(ImmutableSet.toImmutableSet());
            }
        }

        files.forEach(templateFile -> filesByType.computeIfAbsent(templateFile.getType(), type -> Sets.newHashSet())
                .add(templateFile));

        return ImmutableMap.copyOf(Maps.transformValues(filesByType, ImmutableSet::copyOf));
    }

    /**
     * Returns a stream of template files sorted by the requesting type if provided.
     */
    public Stream<TemplateFile> stream(TemplateType... filterTypes) throws IOException {
        return filterTypes.length == 0
                // Return all as a stream
                ? filesByType.values().stream()
                .flatMap(Set::stream)
                // Return only requested types preserving the order it was requested in
                : Stream.of(filterTypes)
                .map(filesByType::get)
                .filter(Objects::nonNull)
                .flatMap(Set::stream);
    }

    private TemplateFile apply(Path pathStr) {
        return new TemplateFile(pathStr);
    }

    @Getter
    @AllArgsConstructor
    public enum TemplateType {
        INCLUDE(Optional.of(CodegenImpl.MUSTACHE_FILE_EXTENSION_INCLUDE)),
        SAMPLE(Optional.of(CodegenImpl.MUSTACHE_FILE_EXTENSION_SAMPLE)),
        REPLACE(Optional.of(CodegenImpl.MUSTACHE_FILE_EXTENSION_TEMPLATE)),
        MERGE(Optional.of(CodegenImpl.MUSTACHE_FILE_EXTENSION_MERGE)),
        UNKNOWN(Optional.empty());
        private final Optional<String> mustacheFileExtension;

        public static TemplateType classify(Path path) {
            String fileName = path.getFileName().toString();

            // Check for all extensions
            for (TemplateType templateType : EnumSet.allOf(TemplateType.class)) {
                if (templateType.getMustacheFileExtension().isEmpty()) {
                    // Except unknown
                    continue;
                }
                if (fileName.endsWith(templateType.getMustacheFileExtension().get())) {
                    return templateType;
                }
            }
            return UNKNOWN;
        }
    }

    @Value
    public class TemplateFile {
        Path relativePath;
        TemplateType type;

        public TemplateFile(Path relativePath) {
            this.relativePath = relativePath;
            this.type = TemplateType.classify(relativePath);
        }

        @SneakyThrows
        public InputStream openInputStream() {
            if (templateDirOpt.isEmpty()) {
                return Resources.getResource("template/" + getTemplate().getResourceName() + "/" + getRelativePath()).openStream();
            } else {
                return new FileInputStream(templateDirOpt.get().resolve(relativePath).toFile());
            }
        }

        public TemplateFiles getTemplateFiles() {
            return TemplateFiles.this;
        }
    }

    @Value
    @AllArgsConstructor
    public static class Tree {
        List<String> paths;
    }

    private void writeTree(Path templatesFolder, Path outputFolder) throws IOException {
        Tree tree = new Tree(stream()
                .map(TemplateFile::getRelativePath)
                .map(Path::toString)
                .collect(ImmutableList.toImmutableList()));
        try (FileWriter writer = new FileWriter(outputFolder.resolve(getTreeFileName()).toFile())) {
            GsonUtil.getPrettyPrint().toJson(tree, writer);
        }
    }

    private String getTreeFileName() {
        return getTemplate().getResourceName() + "-tree.json";
    }

    public static void main(String... args) throws Exception {
        if (args.length != 2) {
            throw new Exception("Invalid number of arguments");
        }

        Path templatesFolder = Path.of(args[0]);
        if (!templatesFolder.toFile().isDirectory()) {
            throw new Exception("Not a directory: " + templatesFolder);
        }
        Path outputFolder = Path.of(args[1]);
        outputFolder.toFile().mkdirs();

        for (Template template : Template.values()) {
            new TemplateFiles(template, templatesFolder.resolve(template.getResourceName()))
                    .writeTree(templatesFolder, outputFolder);
        }
    }
}

