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
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.gson.Gson;
import com.samskivert.mustache.Mustache;
import com.samskivert.mustache.Mustache.TemplateLoader;
import com.samskivert.mustache.MustacheException;
import io.dataspray.core.TemplateFiles.TemplateFile;
import io.dataspray.core.TemplateFiles.TemplateType;
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
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

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

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.function.Predicate.not;

@Slf4j
@ApplicationScoped
public class CodegenImpl implements Codegen {
    public static final String PROJECT_FILENAME = "ds_project.yml";
    public static final String SCHEMAS_FOLDER = "schema";
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
    public static final String OVERWRITE_BACKUP_SUFFIX = ".original";

    @Inject
    DefinitionLoader definitionLoader;
    @Inject
    FileTracker fileTracker;
    @Inject
    ContextBuilder contextBuilder;
    @Inject
    Gson gson;
    @Inject
    MergeStrategies mergeStrategies;

    private boolean schemasFolderGenerated = false;

    @Override
    @SneakyThrows
    public Project initProject(String basePath, String projectName, SampleProject sample) {
        checkArgument(projectName.matches("^[a-zA-Z0-9-_. ]+$"), "Project name can only contain alphanumeric, spaces and - _ .");
        Definition definition = sample.getDefinitionForName(projectName);
        Path projectAbsolutePath = Path.of(basePath).resolve(definition.getNameDir()).toAbsolutePath();
        File projectAbsoluteDir = projectAbsolutePath.toFile();

        // Create project folder
        if (!projectAbsoluteDir.mkdirs()) {
            throw new IOException("Failed to create folder already exists: " + projectAbsolutePath);
        }
        log.info("Created project folder " + projectAbsolutePath);

        // Initialize Git
        Git git = getOrCreateGit(projectAbsoluteDir);

        // Create project definition
        File dsProjectFile = projectAbsolutePath.resolve(PROJECT_FILENAME).toFile();
        if (!dsProjectFile.createNewFile()) {
            throw new IOException("File already exists: " + dsProjectFile.getPath());
        }
        try (FileOutputStream fos = new FileOutputStream(dsProjectFile)) {
            fos.write(definitionLoader.toYaml(definition).getBytes(StandardCharsets.UTF_8));
        }

        return new Project(projectAbsolutePath, git, definition, Optional.empty());
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

    @Override
    public void cleanAll(Project project) {
        clean(project, Optional.empty());
    }

    @Override
    public void cleanProcessor(Project project, String processorName) {
        clean(project, Optional.of(Path.of(project.getProcessorByName(processorName).getNameDir())));
    }

    private void clean(Project project, Optional<Path> subPath) {
        ImmutableSet<Path> absoluteTrackedPathsToDelete = fileTracker.getTrackedFiles(project, subPath, Optional.empty())
                .stream()
                .filter(p -> {
                    boolean fileWriteable = getFileWriteable(p);
                    if (fileWriteable) {
                        log.warn("Template file found to have write permission restored, likely edited externally; not deleting: {}", p);
                    }
                    return !fileWriteable;
                })
                .collect(ImmutableSet.toImmutableSet());
        fileTracker.unlinkUntrackFiles(project, absoluteTrackedPathsToDelete);
    }

    @SneakyThrows
    private Project loadProject(Path projectPath, Optional<String> activeSubDirNameOpt) {
        Path projectAbsolutePath = projectPath.toAbsolutePath();
        Definition definition;
        try (FileReader reader = new FileReader(projectAbsolutePath.resolve(PROJECT_FILENAME).toFile())) {
            definition = definitionLoader.fromYaml(reader);
        }
        Git git = getOrCreateGit(projectAbsolutePath.toFile());
        Optional<String> activeProcessorNameOpt = activeSubDirNameOpt.flatMap(activeSubDirName -> definition.getProcessors().stream()
                .filter(p -> activeSubDirName.equals(p.getNameDir()))
                .findAny()
                .map(Item::getName));
        return new Project(projectAbsolutePath, git, definition, activeProcessorNameOpt);
    }

    @Override
    public void generateAll(Project project, boolean overwriteWriteableTemplate) {
        generateRoot(project, overwriteWriteableTemplate);
        project.getDefinition().getDataFormats()
                .forEach(dataFormat -> generateDataFormat(project, dataFormat, overwriteWriteableTemplate));
        project.getDefinition().getProcessors()
                .forEach(processor -> generateProcessor(project, processor, overwriteWriteableTemplate));
    }

    private void generateRoot(Project project, boolean overwriteWriteableTemplate) {
        codegen(project, project.getAbsolutePath(), Template.ROOT, contextBuilder.createForRoot(project), Optional.of(0L), overwriteWriteableTemplate);
    }

    private void generateDataFormat(Project project, DataFormat dataFormat, boolean overwriteWriteableTemplate) {
        switch (dataFormat.getSerde()) {/* Format is schemaless, Nothing to do*/
            case BINARY:
            case STRING:
                break;
            case JSON:
                codegen(project, createDataFormatDir(project, dataFormat, overwriteWriteableTemplate), Template.DATA_FORMAT_JSON, contextBuilder.createForDataFormat(project, dataFormat), Optional.empty(), overwriteWriteableTemplate);
                break;
            case PROTOBUF:
                codegen(project, createDataFormatDir(project, dataFormat, overwriteWriteableTemplate), Template.DATA_FORMAT_PROTOBUF, contextBuilder.createForDataFormat(project, dataFormat), Optional.empty(), overwriteWriteableTemplate);
                break;
            case AVRO:
                codegen(project, createDataFormatDir(project, dataFormat, overwriteWriteableTemplate), Template.DATA_FORMAT_AVRO, contextBuilder.createForDataFormat(project, dataFormat), Optional.empty(), overwriteWriteableTemplate);
                break;
        }
    }

    @Override
    public void generateProcessor(Project project, String processorName, boolean overwriteWriteableTemplate) {
        generateProcessor(project, Optional.ofNullable(project.getDefinition().getProcessors()).stream()
                        .flatMap(Collection::stream)
                        .filter(p -> p.getName().equals(processorName))
                        .findAny()
                        .orElseThrow(() -> new RuntimeException("Cannot find processor with name " + processorName)),
                overwriteWriteableTemplate);
    }

    public void generateProcessor(Project project, Processor processor, boolean overwriteWriteableTemplate) {
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
        codegen(project, processorPath, template, contextBuilder.createForProcessor(project, processor), Optional.empty(), overwriteWriteableTemplate);
    }

    private Path getDataFormatDir(Project project, DataFormat dataFormat) {
        return project.getAbsolutePath().resolve(SCHEMAS_FOLDER).resolve(dataFormat.getNameDir());
    }

    private Path createDataFormatDir(Project project, DataFormat dataFormat, boolean overwriteWriteableTemplate) {
        if (!schemasFolderGenerated) {
            schemasFolderGenerated = true;

            // Create schemas folder
            Path schemasPath = project.getAbsolutePath().resolve(SCHEMAS_FOLDER);
            if (schemasPath.toFile().mkdir()) {
                log.info("Created schemas folder " + schemasPath);
            }

            // Initialize templates folder
            applyTemplate(project, Template.SCHEMAS.getFilesFromResources(), schemasPath, Optional.of(0L), contextBuilder.createForTemplates(project), overwriteWriteableTemplate);
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
    private void codegen(Project project, Path processorPath, Template template, DatasprayContext processorContext, Optional<Long> maxDepthOpt, boolean overwriteWriteableTemplate) {
        // Initialize templates folder
        final TemplateFiles templateFiles;
        if (project.getDefinition().getShowTemplateFiles()) {

            // Create templates folder
            Path templatesAbsolutePath = project.getAbsolutePath().resolve(TEMPLATES_FOLDER);
            if (templatesAbsolutePath.toFile().mkdir()) {
                log.info("Created templates folder " + templatesAbsolutePath);
            }

            applyTemplate(project, Template.TEMPLATES.getFilesFromResources(), templatesAbsolutePath, Optional.of(0L), contextBuilder.createForTemplates(project), overwriteWriteableTemplate);

            // Copy our template from resources to repo (Skipping over overriden files)
            Path templateInRepoAbsoluteDir = templatesAbsolutePath.resolve(template.getResourceName());
            Set<Path> trackedFilesAbsolutePath = Sets.newHashSet(fileTracker.getTrackedFiles(project, Optional.of(templateInRepoAbsoluteDir), Optional.empty()));
            if (templateInRepoAbsoluteDir.toFile().mkdir()) {
                log.info("Created {} template folder {}", template.getResourceName(), templateInRepoAbsoluteDir);
            }
            log.info("Walking over template {}", template);
            template.getFilesFromResources().stream().forEach(source -> {
                String sourceFileName = source.getRelativePath().getFileName().toString();
                Path destinationAbsolutePath = templateInRepoAbsoluteDir.resolve(source.getRelativePath());
                Path destinationProjectRelativePath = project.getAbsolutePath().relativize(destinationAbsolutePath);
                // Check if file was previously tracked
                if (!trackedFilesAbsolutePath.remove(destinationAbsolutePath)) {
                    // If not, attempt to track it now
                    if (!fileTracker.trackFile(project, destinationAbsolutePath)) {
                        log.debug("Skipping file overriden by user {}", destinationProjectRelativePath);
                        return;
                    } else {
                        log.debug("Creating generated file {}", destinationProjectRelativePath);
                    }
                } else {
                    log.debug("Overwriting generated file {}", destinationProjectRelativePath);
                }
                Optional.ofNullable(destinationAbsolutePath.toFile().getParentFile())
                        .ifPresent(File::mkdirs);

                log.debug("Copying template file {} to {}", sourceFileName, destinationAbsolutePath);
                try (InputStream sourceInputStream = source.openInputStream()) {
                    Files.copy(sourceInputStream, destinationAbsolutePath, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                }
            });
            // Remove files that were not generated this round but previously most likely by older template
            fileTracker.unlinkUntrackFiles(project, trackedFilesAbsolutePath);

            templateFiles = template.getFilesFromDisk(templateInRepoAbsoluteDir);
        } else {
            templateFiles = template.getFilesFromResources();
        }

        // Finally, apply our template
        applyTemplate(project, templateFiles, processorPath, maxDepthOpt, processorContext, overwriteWriteableTemplate);
    }

    @SneakyThrows
    private void applyTemplate(Project project, TemplateFiles files, Path absoluteTemplatePath, Optional<Long> maxDepthOpt, DatasprayContext context, boolean overwriteWriteableTemplate) {
        Set<Path> trackedFilesAbsolutePath = Sets.newHashSet(fileTracker.getTrackedFiles(project, Optional.of(absoluteTemplatePath), maxDepthOpt));
        files.stream(
                // First process samples if files don't exist
                TemplateType.SAMPLE,
                // Replace any files that need replacing
                TemplateType.REPLACE,
                // Finally merge existing files; may be combined with samples
                TemplateType.MERGE
        ).forEach(item -> {
            if (maxDepthOpt.isPresent() && (item.getRelativePath().getNameCount() - 1) > maxDepthOpt.get()) {
                return;
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
            Path projectToFilePath = project.getAbsolutePath().relativize(absoluteFilePath);

            boolean fileExists = Files.exists(absoluteFilePath, LinkOption.NOFOLLOW_LINKS);
            if (item.getType() == TemplateType.SAMPLE && fileExists) {
                // Sample files are not tracked using Git tracking, they are simply created if they don't exist.
                // If they exist, it's assumed the user may have modified it for their own purposes.
                log.trace("Skipping creating sample file which already exists {}", projectToFilePath);
                return;
            }

            // Throw if its a link, directory or something else we can't replace
            if (fileExists && !Files.isRegularFile(absoluteFilePath, LinkOption.NOFOLLOW_LINKS)) {
                throw new RuntimeException("Cannot create template file, not a regular file in its place: " + projectToFilePath);
            }

            if (item.getType() == TemplateType.REPLACE) {
                // Non-sample files are overwritten every time unless user explicitly excluded the file from gitignore
                // Check if file was previously tracked
                if (!trackedFilesAbsolutePath.remove(absoluteFilePath)) {
                    // If not, attempt to track it now
                    if (!fileTracker.trackFile(project, absoluteFilePath)) {
                        log.trace("Skipping creating template file overriden by user {}", projectToFilePath);
                        return;
                    } else {
                        log.debug("Creating template file {}", projectToFilePath);
                    }
                } else {
                    log.debug("Overwriting previously generated file {}", projectToFilePath);
                }
            }

            // Execute the template
            Optional<String> resultFileOpt = runMustache(item, absoluteFilePath, context);

            // If the template evaluates to empty, skip the file
            if (resultFileOpt.isEmpty()) {
                return;
            }

            if (item.getType() == TemplateType.REPLACE
                || item.getType() == TemplateType.SAMPLE
                // If merging, but file doesn't exist, replace it
                || (item.getType() == TemplateType.MERGE && !fileExists)) {

                // Create parent directories if needed
                Optional.ofNullable(absoluteFilePath.getParent())
                        .map(Path::toFile)
                        .ifPresent(File::mkdirs);

                // Set it to writeable in case we unset it before
                if (absoluteFilePath.toFile().exists()) {
                    if (item.getType() == TemplateType.REPLACE
                        && getFileWriteable(absoluteFilePath)) {
                        if (!overwriteWriteableTemplate) {
                            log.warn("Template file found to have write permission restored, likely edited externally; creating copy with {} suffix: {}", OVERWRITE_BACKUP_SUFFIX, item.getRelativePath());
                            try {
                                Files.move(absoluteFilePath,
                                        absoluteFilePath.resolveSibling(absoluteFilePath.getFileName() + OVERWRITE_BACKUP_SUFFIX),
                                        StandardCopyOption.REPLACE_EXISTING);
                            } catch (IOException ex) {
                                throw new RuntimeException(ex);
                            }
                        } else {
                            log.info("Overwriting template file found to have write permission: {}", item.getRelativePath());
                        }
                    } else {
                        setFileWriteable(absoluteFilePath, true);
                    }
                }

                // Write out the file
                try {
                    Files.writeString(absoluteFilePath, resultFileOpt.get(), Charsets.UTF_8);
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                }

                // If file is tracked, remove the write permission
                // This is an extra warning for the user that the file will be re-generated and should not be modified
                if (item.getType() == TemplateType.REPLACE) {
                    setFileWriteable(absoluteFilePath, false);
                }
            } else if (item.getType() == TemplateType.MERGE) {
                // Perform a merge
                mergeStrategies.findMergeStrategy(item)
                        .orElseThrow(() -> new RuntimeException("Cannot merge template file, no merge strategy found for file: " + item.getRelativePath()))
                        .merge(resultFileOpt.get(), absoluteFilePath);
            }
        });
        // Remove files that were not generated this round but previously most likely by older template
        fileTracker.unlinkUntrackFiles(project, trackedFilesAbsolutePath);
    }

    private boolean getFileWriteable(Path absoluteFilePath) {
        return absoluteFilePath.toFile().canWrite();
    }

    private void setFileWriteable(Path absoluteFilePath, boolean writeable) {
        if (!absoluteFilePath.toFile().setWritable(writeable, false)) {
            throw new RuntimeException("Cannot modify write permission on file: " + absoluteFilePath);
        }
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
            return mustacheFile.getTemplateFiles().stream(TemplateType.INCLUDE)
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
                .filter(not(Path.of("")::equals))
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
                .filter(not(Path.of("")::equals));
    }

    @SneakyThrows
    private Git getOrCreateGit(File projectDir) {
        try {
            // Search for existing repository if exists
            return Git.wrap(new FileRepositoryBuilder()
                    .findGitDir(projectDir)
                    .setMustExist(true)
                    .build());
        } catch (RepositoryNotFoundException ex) {
            // Initialize a new one
            log.info("Git repository not found, initializing a new one");
            return Git.init()
                    .setDirectory(projectDir)
                    .call();
        }
    }

    @Value
    private static class ExpandedFile {
        Path path;
        String filename;
    }
}
