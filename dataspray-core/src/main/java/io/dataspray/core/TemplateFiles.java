package io.dataspray.core;

import com.google.common.collect.ImmutableList;
import com.google.common.io.Resources;
import io.dataspray.core.common.json.GsonUtil;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.SneakyThrows;

import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Given a template, we need to iterate over all template files and generate code. The template files may be on disk or
 * in resources. It is not easy to iterate all
 * resource files. This convenience wrapper allows us to iterate files wherever they are.
 *
 * For more details: When resources are accessed from tests, the returned URL points to the disk location which is
 * easily walkable. Once bundled in a jar, a ZipFileSystem can be used but it doesn't work well with an executable
 * Jar file. Once we move to native executable, who knows what challenges lie ahead.
 *
 * As a workaround, we generate the file structure for each template and read it during runtime to stream the files
 * as needed.
 */
@Data
public class TemplateFiles {
    Template template;
    transient Optional<Path> templateDirOpt;

    /**
     * Template files backed by bundled resources
     */
    TemplateFiles(Template template) {
        this.template = template;
        this.templateDirOpt = Optional.empty();
    }

    /**
     * Template files backed by on-disk resources
     */
    TemplateFiles(Template template, Path templateDir) {
        this.template = template;
        this.templateDirOpt = Optional.of(templateDir);
    }

    public Stream<TemplateFile> stream() throws IOException {
        if (templateDirOpt.isEmpty()) {
            String treeFilePath = "template/" + getTreeFileName();
            try (Reader reader = new InputStreamReader(Resources.getResource(treeFilePath).openStream())) {
                return GsonUtil.get()
                        .fromJson(reader, Tree.class)
                        .getPaths()
                        .stream()
                        .map(Path::of)
                        .map(TemplateFile::new);
            }
        } else {
            return Files.walk(templateDirOpt.get())
                    .skip(1) // Skip self
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(CodegenImpl.MUSTACHE_FILE_EXTENSION))
                    .map(templateDirOpt.get()::relativize)
                    .map(TemplateFile::new);
        }
    }

    private TemplateFile apply(Path pathStr) {
        return new TemplateFile(pathStr);
    }

    @Data
    @AllArgsConstructor
    public class TemplateFile {
        Path relativePath;

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

    @Data
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
            GsonUtil.get()
                    .newBuilder()
                    .setPrettyPrinting()
                    .create()
                    .toJson(tree, writer);
        }
    }

    private String getTreeFileName() {
        return getTemplate().getResourceName() + "-tree.json";
    }

    public static void main(String[] args) throws Exception {
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

