package io.dataspray.core;

import com.google.common.base.Ascii;
import com.google.common.base.Charsets;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.samskivert.mustache.Mustache;
import com.samskivert.mustache.Mustache.TemplateLoader;
import com.samskivert.mustache.MustacheException;
import io.dataspray.core.TemplateFiles.TemplateFile;
import io.dataspray.core.definition.model.DataFormat;
import io.dataspray.core.definition.model.Definition;
import io.dataspray.core.definition.model.JavaProcessor;
import io.dataspray.core.definition.parser.DefinitionLoader;
import io.dataspray.core.sample.SampleProject;
import lombok.SneakyThrows;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
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

@Slf4j
@ApplicationScoped
public class CodegenImpl implements Codegen {
    public static final String PROJECT_FILENAME = "ds_project.yml";
    public static final String SCHEMAS_FOLDER = ".schema";
    public static final String TEMPLATES_FOLDER = ".template";
    public static final String MUSTACHE_FILE_EXTENSION = ".mustache";
    /** Template files, always overwritten unless overriden by user */
    private static final String MUSTACHE_FILE_EXTENSION_TEMPLATE = ".template" + MUSTACHE_FILE_EXTENSION;
    /** Sample files, only written if they don't already exist, intended to be edited by user */
    private static final String MUSTACHE_FILE_EXTENSION_SAMPLE = ".sample" + MUSTACHE_FILE_EXTENSION;
    /** Sub-templates intended to be included by other templates/samples. */
    private static final String MUSTACHE_FILE_EXTENSION_INCLUDE = ".include" + MUSTACHE_FILE_EXTENSION;

    @Inject
    DefinitionLoader definitionLoader;
    @Inject
    FileTracker fileTracker;
    @Inject
    ContextBuilder contextBuilder;

    private boolean schemasFolderGenerated = false;

    @Override
    @SneakyThrows
    public Project initProject(String basePath, String projectName, SampleProject sample) {
        Definition definition = sample.getDefinitionForName(projectName);
        Path projectPath = Path.of(basePath, definition.getNameDir());
        File projectDir = projectPath.toFile();

        // Create project folder
        if (!projectDir.mkdir()) {
            throw new IOException("Folder already exists: " + projectPath);
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

        return new Project(projectPath, git, definition);
    }

    @Override
    @SneakyThrows
    public Project loadProject(String projectPathStr) {
        Path projectPath = Path.of(projectPathStr);
        Definition definition;
        try (FileReader reader = new FileReader(projectPath.resolve(PROJECT_FILENAME).toFile())) {
            definition = definitionLoader.fromYaml(reader);
        }
        Git git = Git.open(projectPath.toFile());
        return new Project(projectPath, git, definition);
    }

    @Override
    public void generateAll(Project project) {
        project.getDefinition().getDataFormats()
                .forEach(dataFormat -> generateDataFormat(project, dataFormat));
        Optional.ofNullable(project.getDefinition().getJavaProcessors()).stream()
                .flatMap(Collection::stream)
                .forEach(processor -> generateJava(project, processor));
    }

    @Override
    public void generateDataFormat(Project project, String dataFormatName) {
        generateDataFormat(project, project.getDefinition().getDataFormats().stream()
                .filter(p -> p.getName().equals(dataFormatName))
                .findAny()
                .orElseThrow(() -> new RuntimeException("Cannot find data format with name " + dataFormatName)));
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
    public void generateJava(Project project, String processorName) {
        generateJava(project, Optional.ofNullable(project.getDefinition().getJavaProcessors()).stream()
                .flatMap(Collection::stream)
                .filter(p -> p.getName().equals(processorName))
                .findAny()
                .orElseThrow(() -> new RuntimeException("Cannot find java processor with name " + processorName)));
    }

    private void generateJava(Project project, JavaProcessor processor) {
        Path processorPath = createProcessorDir(project, processor.getNameDir());
        codegen(project, processorPath, Template.JAVA, contextBuilder.createForProcessor(project, processor));
    }

    public static Path getProcessorDir(Project project, String nameDir) {
        return project.getPath().resolve(nameDir);
    }

    private Path createProcessorDir(Project project, String nameDir) {
        return createDir(getProcessorDir(project, nameDir));
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
        template.getFilesFromResources().stream()
                .forEach(source -> {
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
        applyTemplate(project, template.getFilesFromDisk(templateInRepoDir), processorPath, Optional.empty(), processorContext);
    }

    @SneakyThrows
    private void applyTemplate(Project project, TemplateFiles files, Path absoluteTemplatePath, Optional<Long> maxDepthOpt, DatasprayContext context) {
        Set<Path> trackedFiles = Sets.newHashSet(fileTracker.getTrackedFiles(project, Optional.of(absoluteTemplatePath), maxDepthOpt));
        files.stream().forEach(item -> {
            if (maxDepthOpt.isPresent() && (item.getRelativePath().getNameCount() - 1) > maxDepthOpt.get()) {
                return;
            }
            String templateFilename = item.getRelativePath().getFileName().toString();
            boolean isSample = templateFilename.endsWith(MUSTACHE_FILE_EXTENSION_SAMPLE);
            if (isSample || templateFilename.endsWith(MUSTACHE_FILE_EXTENSION_TEMPLATE)) {
                Optional<ExpandedFile> expandedFileOpt = expandPath(project, item.getRelativePath(), context);

                // Don't create file if expanding template evaluates to empty filename
                if (!expandedFileOpt.isPresent()) {
                    log.trace("Skipping creating template as the template indicated file should not be created {}", item.getRelativePath());
                    return;
                }

                // Retrieve final filename by stripping mustache suffix
                String filenameWithoutSuffix = expandedFileOpt.get().getFilename()
                        .substring(0, expandedFileOpt.get().getFilename().length()
                                - (isSample ? MUSTACHE_FILE_EXTENSION_SAMPLE : MUSTACHE_FILE_EXTENSION_TEMPLATE).length());

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

                if (!isSample) {
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
                } else {
                    // Sample files are not tracked using Git tracking, they are simply created if they don't exist.
                    // If they exist, it's assumed the user may have modified it for their own purposes.
                    if (fileExists) {
                        log.trace("Skipping creating sample file which already exists {}", projectToFilePath);
                        return;
                    }
                }

                runMustache(item, absoluteFilePath, context);
            } else if (templateFilename.endsWith(MUSTACHE_FILE_EXTENSION_INCLUDE)) {
                // Skip sub-templates
                log.trace("Skipping sub template files {}", item.getRelativePath());
            } else {
                log.warn("Skipping unexpected non-template file {}", item.getRelativePath());
            }
        });
        // Remove files that were not generated this round but previously most likely by older template
        fileTracker.unlinkUntrackFiles(project, trackedFiles);
    }

    @SneakyThrows
    private void runMustache(TemplateFile mustacheFile, Path destinationFile, DatasprayContext context) {
        String mustacheStr;
        try (InputStream is = mustacheFile.openInputStream()) {
            mustacheStr = IOUtils.toString(is, Charsets.UTF_8);
        }
        String content = runMustache(mustacheStr, context, Optional.of(requestedFilename -> {
            Path requestedPath = mustacheFile.getRelativePath()
                    .getParent()
                    .resolve(requestedFilename + MUSTACHE_FILE_EXTENSION_INCLUDE)
                    .normalize();
            return mustacheFile.getTemplateFiles().stream()
                    .filter(f -> f.getRelativePath().equals(requestedPath))
                    .map(f -> new InputStreamReader(f.openInputStream(), Charsets.UTF_8))
                    .findAny()
                    .orElseThrow(() -> new FileNotFoundException("Template file not found: " + requestedPath));
        }));
        if (content.isBlank()) {
            return;
        }
        Optional.ofNullable(destinationFile.getParent())
                .map(Path::toFile)
                .ifPresent(File::mkdirs);
        Files.writeString(destinationFile, content, Charsets.UTF_8);
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
