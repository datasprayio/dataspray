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

import com.google.common.base.Ascii;
import com.google.common.base.Charsets;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonIOException;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.samskivert.mustache.Mustache;
import com.samskivert.mustache.Mustache.TemplateLoader;
import com.samskivert.mustache.MustacheException;
import io.dataspray.common.json.GsonMergeUtil;
import io.dataspray.core.TemplateFiles.TemplateFile;
import io.dataspray.core.definition.model.DataFormat;
import io.dataspray.core.definition.model.Definition;
import io.dataspray.core.definition.model.Item;
import io.dataspray.core.definition.model.JavaProcessor;
import io.dataspray.core.definition.model.Processor;
import io.dataspray.core.definition.model.TypescriptProcessor;
import io.dataspray.core.definition.parser.DefinitionLoader;
import io.dataspray.core.sample.SampleProject;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.SneakyThrows;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import static com.google.common.base.Preconditions.checkArgument;

@Slf4j
@ApplicationScoped
public class CodegenImpl implements Codegen {
    public static final String PROJECT_FILENAME = "ds_project.yml";
    public static final String SCHEMAS_FOLDER = ".schema";
    public static final String TEMPLATES_FOLDER = ".template";
    public static final String MUSTACHE_FILE_EXTENSION = ".mustache";
    /** Template files, always overwritten unless overriden by user */
    public static final String MUSTACHE_FILE_EXTENSION_TEMPLATE = ".template" + MUSTACHE_FILE_EXTENSION;
    /** Sample files, only written if they don't already exist, intended to be edited by user */
    public static final String MUSTACHE_FILE_EXTENSION_SAMPLE = ".sample" + MUSTACHE_FILE_EXTENSION;
    /** Sub-templates intended to be included by other templates/samples. */
    public static final String MUSTACHE_FILE_EXTENSION_INCLUDE = ".include" + MUSTACHE_FILE_EXTENSION;
    /**
     * Templates that are merged into existing files. Such as applying fields into a JSON file.
     * <p>
     * Can be used with sample template to set initial file.
     */
    public static final String MUSTACHE_FILE_EXTENSION_MERGE = ".merge" + MUSTACHE_FILE_EXTENSION;

    @Inject
    DefinitionLoader definitionLoader;
    @Inject
    FileTracker fileTracker;
    @Inject
    ContextBuilder contextBuilder;
    @Inject
    Gson gson;
    @Inject
    GsonMergeUtil gsonMergeUtil;

    private boolean schemasFolderGenerated = false;

    @Override
    @SneakyThrows
    public Project initProject(String basePath, String projectName, SampleProject sample) {
        checkArgument(projectName.matches("^[a-zA-Z0-9-_. ]+$"), "Project name can only contain alphanumeric, spaces and - _ .");
        Definition definition = sample.getDefinitionForName(projectName);
        Path projectPath = Path.of(basePath, definition.getNameDir());
        File projectDir = projectPath.toFile();

        // Create project folder
        if (!projectDir.mkdirs()) {
            throw new IOException("Failed to create folder already exists: " + projectPath);
        }
        log.info("Created project folder " + projectPath);

        // Initialize Git
        Git git;
        try {
            git = Git.init().setDirectory(projectDir).call();
        } catch (GitAPIException ex) {
            throw new IOException("Could not initialize git", ex);
        }

        // Create project definition
        File dsProjectFile = projectPath.resolve(PROJECT_FILENAME).toFile();
        if (!dsProjectFile.createNewFile()) {
            throw new IOException("File already exists: " + dsProjectFile.getPath());
        }
        try (FileOutputStream fos = new FileOutputStream(dsProjectFile)) {
            fos.write(definitionLoader.toYaml(definition).getBytes(StandardCharsets.UTF_8));
        }

        return new Project(projectPath, git, definition, Optional.empty());
    }

    @Override
    public Project loadProject() {
        Path cwd = Path.of("").toAbsolutePath();
        Optional<String> activeSubDirNameOpt = Optional.empty();
        do {
            if (cwd.resolve(PROJECT_FILENAME).toFile().exists()) {
                return loadProject(cwd, activeSubDirNameOpt);
            }

            activeSubDirNameOpt = Optional.ofNullable(cwd.getFileName()).map(Path::toString);
            cwd = cwd.getParent();
        } while (cwd != null);
        throw new RuntimeException("No project found in current working directory or any parent directories");
    }

    @Override
    public Project loadProject(String projectPathStr) {
        return loadProject(Path.of(projectPathStr), Optional.empty());
    }

    @SneakyThrows
    private Project loadProject(Path projectPath, Optional<String> activeSubDirNameOpt) {
        Definition definition;
        try (FileReader reader = new FileReader(projectPath.resolve(PROJECT_FILENAME).toFile())) {
            definition = definitionLoader.fromYaml(reader);
        }
        Git git = Git.open(projectPath.toFile());
        Optional<String> activeProcessorNameOpt = activeSubDirNameOpt.flatMap(activeSubDirName -> definition.getProcessors().stream()
                .filter(p -> activeSubDirName.equals(p.getNameDir()))
                .findAny()
                .map(Item::getName));
        return new Project(projectPath, git, definition, activeProcessorNameOpt);
    }

    @Override
    public void generateAll(Project project) {
        generateRoot(project);
        project.getDefinition().getDataFormats()
                .forEach(dataFormat -> generateDataFormat(project, dataFormat));
        project.getDefinition().getProcessors()
                .forEach(processor -> generateProcessor(project, processor));
    }

    private void generateRoot(Project project) {
        codegen(project, project.getPath(), Template.ROOT, contextBuilder.createForRoot(project), Optional.of(0L));
    }

    private void generateDataFormat(Project project, DataFormat dataFormat) {
        switch (dataFormat.getSerde()) {/* Format is schemaless, Nothing to do*/
            case BINARY:
            case STRING:
                break;
            case JSON:
                codegen(project, createDataFormatDir(project, dataFormat), Template.DATA_FORMAT_JSON, contextBuilder.createForDataFormat(project, dataFormat));
                break;
            case PROTOBUF:
                codegen(project, createDataFormatDir(project, dataFormat), Template.DATA_FORMAT_PROTOBUF, contextBuilder.createForDataFormat(project, dataFormat));
                break;
            case AVRO:
                codegen(project, createDataFormatDir(project, dataFormat), Template.DATA_FORMAT_AVRO, contextBuilder.createForDataFormat(project, dataFormat));
                break;
        }
    }

    @Override
    public void generateProcessor(Project project, String processorName) {
        generateProcessor(project, Optional.ofNullable(project.getDefinition().getProcessors()).stream()
                .flatMap(Collection::stream)
                .filter(p -> p.getName().equals(processorName))
                .findAny()
                .orElseThrow(() -> new RuntimeException("Cannot find processor with name " + processorName)));
    }

    public void generateProcessor(Project project, Processor processor) {
        Template template;
        if (processor instanceof JavaProcessor) {
            template = Template.JAVA;
        } else if (processor instanceof TypescriptProcessor) {
            template = Template.TYPESCRIPT;
        } else {
            throw new RuntimeException("Generation for processor " + processor.getName() + " type " + processor.getClass().getCanonicalName() + "not supported");
        }

        Path processorPath = project.getProcessorDir(processor);
        createDir(processorPath);
        codegen(project, processorPath, template, contextBuilder.createForProcessor(project, processor));
    }

    private Path getDataFormatDir(Project project, DataFormat dataFormat) {
        return project.getPath().resolve(SCHEMAS_FOLDER).resolve(dataFormat.getNameDir());
    }

    private Path createDataFormatDir(Project project, DataFormat dataFormat) {
        if (!schemasFolderGenerated) {
            schemasFolderGenerated = true;

            // Create schemas folder
            Path schemasPath = project.getPath().resolve(SCHEMAS_FOLDER);
            if (schemasPath.toFile().mkdir()) {
                log.info("Created schemas folder " + schemasPath);
            }

            // Initialize templates folder
            applyTemplate(project, Template.SCHEMAS.getFilesFromResources(), schemasPath, Optional.of(0L), contextBuilder.createForTemplates(project));
        }
        return createDir(getDataFormatDir(project, dataFormat));
    }

    private Path createDir(Path dir) {
        if (dir.toFile().mkdirs()) {
            log.info("Created folder " + dir);
        }
        return dir;
    }

    @SneakyThrows
    private void codegen(Project project, Path processorPath, Template template, DatasprayContext processorContext) {
        codegen(project, processorPath, template, processorContext, Optional.empty());
    }

    @SneakyThrows
    private void codegen(Project project, Path processorPath, Template template, DatasprayContext processorContext, Optional<Long> maxDepthOpt) {
        // Create templates folder
        Path templatesPath = project.getPath().resolve(TEMPLATES_FOLDER);
        if (templatesPath.toFile().mkdir()) {
            log.info("Created templates folder " + templatesPath);
        }

        // Initialize templates folder
        applyTemplate(project, Template.TEMPLATES.getFilesFromResources(), templatesPath, Optional.of(0L), contextBuilder.createForTemplates(project));

        // Copy our template from resources to repo (Skipping over overriden files)
        Path templateInRepoDir = templatesPath.resolve(template.getResourceName());
        Set<Path> trackedFiles = Sets.newHashSet(fileTracker.getTrackedFiles(project, Optional.of(templateInRepoDir), Optional.empty()));
        if (templateInRepoDir.toFile().mkdir()) {
            log.info("Created {} template folder {}", template.getResourceName(), templateInRepoDir);
        }
        log.info("Walking over template {}", template);
        template.getFilesFromResources().stream().forEach(source -> {
            String sourceFileName = source.getRelativePath().getFileName().toString();
            Path destination = templateInRepoDir.resolve(source.getRelativePath());
            Path projectRelativePath = project.getPath().relativize(destination);
            // Check if file was previously tracked
            if (!trackedFiles.remove(projectRelativePath)) {
                // If not, attempt to track it now
                if (!fileTracker.trackFile(project, projectRelativePath)) {
                    log.debug("Skipping file overriden by user {}", projectRelativePath);
                    return;
                } else {
                    log.debug("Creating generated file {}", projectRelativePath);
                }
            } else {
                log.debug("Overwriting generated file {}", projectRelativePath);
            }
            Optional.ofNullable(destination.toFile().getParentFile())
                    .ifPresent(File::mkdirs);

            log.info("Copying template file {} to {}", sourceFileName, destination);
            try (InputStream sourceInputStream = source.openInputStream()) {
                Files.copy(sourceInputStream, destination, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        });
        // Remove files that were not generated this round but previously most likely by older template
        fileTracker.unlinkUntrackFiles(project, trackedFiles);

        // Finally, apply our template
        applyTemplate(project, template.getFilesFromDisk(templateInRepoDir), processorPath, maxDepthOpt, processorContext);
    }

    @SneakyThrows
    private void applyTemplate(Project project, TemplateFiles files, Path absoluteTemplatePath, Optional<Long> maxDepthOpt, DatasprayContext context) {
        Set<Path> trackedFiles = Sets.newHashSet(fileTracker.getTrackedFiles(project, Optional.of(absoluteTemplatePath), maxDepthOpt));
        files.stream(
                // First process samples if files don't exist
                TemplateFiles.TemplateType.SAMPLE,
                // Replace any files that need replacing
                TemplateFiles.TemplateType.REPLACE,
                // Finally merge existing files; may be combined with samples
                TemplateFiles.TemplateType.MERGE
        ).forEach(item -> {
            if (maxDepthOpt.isPresent() && (item.getRelativePath().getNameCount() - 1) > maxDepthOpt.get()) {
                return;
            }

            if (item.getType() == TemplateFiles.TemplateType.MERGE
                && !item.getRelativePath().getFileName().toString().endsWith(".json" + CodegenImpl.MUSTACHE_FILE_EXTENSION_MERGE)) {
                throw new RuntimeException("Cannot merge template file, only json is supported: " + item.getRelativePath());
            }

            Optional<ExpandedFile> expandedFileOpt = expandPath(project, item.getRelativePath(), context);

            // Don't create file if expanding template evaluates to empty filename
            if (!expandedFileOpt.isPresent()) {
                log.trace("Skipping creating template as the template indicated file should not be created {}", item.getRelativePath());
                return;
            }

            // Retrieve final filename by stripping mustache suffix
            String filenameWithoutSuffix = expandedFileOpt.get().getFilename()
                    .substring(0, expandedFileOpt.get().getFilename().length()
                                  - item.getType().getMustacheFileExtension().map(String::length).orElse(0));

            // Retrieve final location of file of project
            Path absoluteFilePath = absoluteTemplatePath
                    .resolve(expandedFileOpt.get().getPath())
                    .resolve(filenameWithoutSuffix);
            Path projectToFilePath = project.getPath().relativize(absoluteFilePath);

            // Throw if its a link, directory or something else
            boolean fileExists = Files.exists(absoluteFilePath, LinkOption.NOFOLLOW_LINKS);
            if (fileExists && !Files.isRegularFile(absoluteFilePath, LinkOption.NOFOLLOW_LINKS)) {
                throw new RuntimeException("Cannot create template file, not a regular file in its place: " + projectToFilePath);
            }

            switch (item.getType()) {
                case REPLACE:
                    // Non-sample files are overwritten every time unless user explicitly excluded the file from gitignore
                    // Check if file was previously tracked
                    if (!trackedFiles.remove(projectToFilePath)) {
                        // If not, attempt to track it now
                        if (!fileTracker.trackFile(project, projectToFilePath)) {
                            log.trace("Skipping creating template file overriden by user {}", projectToFilePath);
                            return;
                        } else {
                            log.debug("Creating template file {}", projectToFilePath);
                        }
                    } else {
                        log.debug("Overwriting previously generated file {}", projectToFilePath);
                    }
                    break;
                case SAMPLE:
                    // Sample files are not tracked using Git tracking, they are simply created if they don't exist.
                    // If they exist, it's assumed the user may have modified it for their own purposes.
                    if (fileExists) {
                        log.trace("Skipping creating sample file which already exists {}", projectToFilePath);
                        return;
                    }
                    break;
            }

            // Execute the template
            Optional<String> resultFileOpt = runMustache(item, absoluteFilePath, context);

            // If the template evaluates to empty, skip the file
            if (resultFileOpt.isEmpty()) {
                return;
            }

            if (item.getType() == TemplateFiles.TemplateType.REPLACE
                || item.getType() == TemplateFiles.TemplateType.SAMPLE
                // If merging, but file doesn't exist, replace it
                || (item.getType() == TemplateFiles.TemplateType.MERGE && !fileExists)) {

                // Create parent directories if needed
                Optional.ofNullable(absoluteFilePath.getParent())
                        .map(Path::toFile)
                        .ifPresent(File::mkdirs);

                // Write out the file
                try {
                    Files.writeString(absoluteFilePath, resultFileOpt.get(), Charsets.UTF_8);
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                }

                // If file is tracked, remove the write permission
                // This is an extra warning for the user that the file will be re-generated and should not be modified
                if (item.getType() == TemplateFiles.TemplateType.REPLACE) {
                    if (!absoluteFilePath.toFile().setWritable(false, false)) {
                        throw new RuntimeException("Cannot modify write permission on file: " + absoluteFilePath);
                    }
                }
            } else if (item.getType() == TemplateFiles.TemplateType.MERGE) {
                // Perform a merge
                // First: read the original file
                JsonObject originalFileJson;
                try (var reader = Files.newBufferedReader(absoluteFilePath)) {
                    originalFileJson = gson.fromJson(reader, JsonObject.class);
                } catch (IOException | JsonIOException ex) {
                    throw new RuntimeException("Failed to read file: " + absoluteFilePath, ex);
                } catch (JsonSyntaxException ex) {
                    throw new RuntimeException("Failed to parse file as JSON: " + absoluteFilePath, ex);
                }

                // Second: Then parse the mustache template result file
                JsonObject resultFileJson;
                try {
                    resultFileJson = gson.fromJson(resultFileOpt.get(), JsonObject.class);
                } catch (JsonSyntaxException ex) {
                    throw new RuntimeException("Failed to parse generated template as JSON: " + absoluteFilePath, ex);
                }

                // Merge the two Jsons with a custom conflict resolution
                gsonMergeUtil.merge((String key, JsonObject leftObj, JsonElement leftVal, JsonElement rightVal) -> {
                            // Handle merge conflicts
                            if (rightVal.isJsonNull()) {
                                // A null in template signals original value needs to be removed
                                leftObj.remove(key);
                            } else {
                                // Otherwise template always replaces original
                                leftObj.add(key, rightVal);
                            }
                        },
                        originalFileJson,
                        resultFileJson);

                // Finally write out the merged Json back to the file
                try (var writer = Files.newBufferedWriter(absoluteFilePath)) {
                    gson.toJson(originalFileJson, writer);
                } catch (IOException | JsonIOException ex) {
                    throw new RuntimeException("Failed to read file: " + absoluteFilePath, ex);
                }
            }
        });
        // Remove files that were not generated this round but previously most likely by older template
        fileTracker.unlinkUntrackFiles(project, trackedFiles);
    }

    @SneakyThrows
    private Optional<String> runMustache(TemplateFile mustacheFile, Path destinationFile, DatasprayContext context) {
        String mustacheStr;
        try (InputStream is = mustacheFile.openInputStream()) {
            mustacheStr = IOUtils.toString(is, Charsets.UTF_8);
        }
        String content = runMustache(mustacheStr, context, Optional.of(requestedFilename -> {
            Path requestedPath = mustacheFile.getRelativePath()
                    .getParent()
                    .resolve(requestedFilename + MUSTACHE_FILE_EXTENSION_INCLUDE)
                    .normalize();
            return mustacheFile.getTemplateFiles().stream(TemplateFiles.TemplateType.INCLUDE)
                    .filter(f -> f.getRelativePath().equals(requestedPath))
                    .map(f -> new InputStreamReader(f.openInputStream(), Charsets.UTF_8))
                    .findAny()
                    .orElseThrow(() -> new FileNotFoundException("Template file not found: " + requestedPath));
        }));

        return content.isBlank() ? Optional.empty() : Optional.of(content);
    }

    @SneakyThrows
    private String runMustache(String mustacheStr, DatasprayContext context, Optional<TemplateLoader> templateLoaderOpt) {
        Mustache.Compiler c = Mustache.compiler()
                .escapeHTML(false)
                .standardsMode(false);
        if (templateLoaderOpt.isPresent()) {
            c = c.withLoader(templateLoaderOpt.get());
        }
        try {
            return c.compile(mustacheStr).execute(context);
        } catch (MustacheException ex) {
            throw new RuntimeException(ex.getMessage() + "; For template '" + Ascii.truncate(mustacheStr, 100, "...") + "'", ex);
        }
    }

    /**
     * Folders and files can have mustache templates within. This expands those templates to generate a final path and
     * filename of the given file.
     *
     * A file may also expand to empty in some mustache conditions which indicates we should not create this file.
     */
    private Optional<ExpandedFile> expandPath(Project project, Path templatePath, DatasprayContext parentContext) {
        Path expandedPath = Path.of(".");
        for (Path pathName : templatePath.normalize()) {
            Optional<Path> expandedName = expandName(project, pathName, parentContext);
            if (expandedName.isEmpty()) {
                return Optional.empty();
            }
            expandedPath = expandedPath.resolve(expandedName.get());
        }
        return Optional.of(expandedPath)
                .map(Path::normalize)
                .filter(Predicate.not(Path.of("")::equals))
                .map(p -> new ExpandedFile(
                        Optional.ofNullable(p.getParent()).orElse(Path.of("")),
                        p.getFileName().toString()));
    }

    /**
     * Expand a single file/directory using templates. This may result in a path, filename or nothing.
     */
    private Optional<Path> expandName(Project project, Path path, DatasprayContext parentContext) {
        // Since a filename cannot contain '/', use underscores '_' instead
        String pathName = path.toString().replaceAll("\\{\\{_", "{{/");
        String generatedPath = runMustache(pathName, contextBuilder.createForFilename(parentContext, project), Optional.empty());
        if (Strings.isNullOrEmpty(generatedPath)) {
            return Optional.empty();
        }
        Path expandedPath = Path.of(".");
        List<String> levels = Lists.newArrayList();
        StringBuilder levelBuilder = new StringBuilder();
        for (int i = 0; i < generatedPath.length(); i++) {
            char currentChar = generatedPath.charAt(i);
            if (currentChar == File.separatorChar) {
                expandedPath = expandedPath.resolve(levelBuilder.toString());
                levelBuilder = new StringBuilder();
            } else {
                levelBuilder.append(currentChar);
            }
        }
        expandedPath = expandedPath.resolve(levelBuilder.toString()).normalize();
        return Optional.of(expandedPath)
                .filter(Predicate.not(Path.of("")::equals));
    }

    @Value
    private static class ExpandedFile {
        Path path;
        String filename;
    }
}
