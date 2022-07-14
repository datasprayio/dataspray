package com.smotana.dataspray.core;

import com.google.common.base.Ascii;
import com.google.common.base.Charsets;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.io.Resources;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import com.samskivert.mustache.Mustache;
import com.samskivert.mustache.Mustache.TemplateLoader;
import com.samskivert.mustache.MustacheException;
import com.smotana.dataspray.core.definition.model.DataFormat;
import com.smotana.dataspray.core.definition.model.Definition;
import com.smotana.dataspray.core.definition.model.JavaProcessor;
import com.smotana.dataspray.core.definition.model.Processor;
import com.smotana.dataspray.core.definition.parser.DefinitionLoader;
import com.smotana.dataspray.core.sample.SampleProject;
import lombok.SneakyThrows;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.lang.ProcessBuilder.Redirect;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Slf4j
public class CodegenImpl implements Codegen {
    public static final String SCHEMAS_FOLDER = ".schema";
    public static final String TEMPLATES_FOLDER = ".template";
    /** Template files, always overwritten unless overriden by user */
    private static final String MUSTACHE_FILE_EXTENSION_TEMPLATE = ".template.mustache";
    /** Sample files, only written if they don't already exist, intended to be edited by user */
    private static final String MUSTACHE_FILE_EXTENSION_SAMPLE = ".sample.mustache";
    /** Sub-templates intended to be included by other templates/samples. */
    private static final String MUSTACHE_FILE_EXTENSION_INCLUDE = ".include.mustache";

    @Inject
    private DefinitionLoader definitionLoader;
    @Inject
    private FileTracker fileTracker;
    @Inject
    private ContextBuilder contextBuilder;
    @Inject
    @Named("IN")
    private Redirect in;
    @Inject
    @Named("OUT")
    private Redirect out;
    @Inject
    @Named("ERR")
    private Redirect err;

    private boolean schemasFolderGenerated = false;

    @Override
    public Project initProject(String basePath, String projectName, SampleProject sample) throws IOException {
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
        File dsProjectFile = projectPath.resolve("ds_project.yml").toFile();
        if (!dsProjectFile.createNewFile()) {
            throw new IOException("File already exists: " + dsProjectFile.getPath());
        }
        try (FileOutputStream fos = new FileOutputStream(dsProjectFile)) {
            fos.write(definitionLoader.toYaml(definition).getBytes(StandardCharsets.UTF_8));
        }

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
        switch (dataFormat.getSerde()) {
            case BINARY, STRING -> { /* Format is schemaless, Nothing to do*/ }
            case JSON -> codegen(project, createDataFormatDir(project, dataFormat), Template.DATA_FORMAT_JSON, contextBuilder.createForDataFormat(project, dataFormat));
            case PROTOBUF -> codegen(project, createDataFormatDir(project, dataFormat), Template.DATA_FORMAT_PROTOBUF, contextBuilder.createForDataFormat(project, dataFormat));
            case AVRO -> codegen(project, createDataFormatDir(project, dataFormat), Template.DATA_FORMAT_AVRO, contextBuilder.createForDataFormat(project, dataFormat));
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
        Path processorPath = createProjectDir(project, processor.getNameDir());
        codegen(project, processorPath, Template.JAVA, contextBuilder.createForProcessor(project, processor));
    }

    @Override
    public void installAll(Project project) {
        Optional.ofNullable(project.getDefinition()
                        .getJavaProcessors())
                .stream()
                .flatMap(Collection::stream)
                .map(Processor::getName)
                .forEach(processorName -> installJava(project, processorName));
    }

    @SneakyThrows
    @Override
    public void installJava(Project project, String processorName) {
        JavaProcessor processor = Optional.ofNullable(project.getDefinition()
                        .getJavaProcessors())
                .stream()
                .flatMap(Collection::stream)
                .filter(p -> p.getName().equals(processorName))
                .findAny()
                .orElseThrow(() -> new RuntimeException("Cannot find java processor with name " + processorName));

        ProcessBuilder processBuilder = isWindows()
                ? new ProcessBuilder("cmd.exe", "/c", "mvn clean install")
                : new ProcessBuilder("sh", "-c", "mvn clean install");
        processBuilder.directory(getProjectDir(project, processor.getNameDir()).toFile());
        processBuilder.redirectError(err);
        processBuilder.redirectInput(in);
        processBuilder.redirectOutput(out);
        Process process = processBuilder.start();

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("Process exited with non-zero status " + exitCode);
        }
    }

    private Path getProjectDir(Project project, String nameDir) {
        return project.getPath().resolve(nameDir);
    }

    private Path createProjectDir(Project project, String nameDir) {
        return createDir(getProjectDir(project, nameDir));
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
            applyTemplate(project, getTemplateFromResources(Template.SCHEMAS), schemasPath, Optional.of(0L), contextBuilder.createForTemplates(project));
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
        applyTemplate(project, getTemplateFromResources(Template.TEMPLATES), templatesPath, Optional.of(0L), contextBuilder.createForTemplates(project));

        // Copy our template from resources to repo (Skipping over overriden files)
        File templateInResourcesDir = getTemplateFromResources(template);
        Path templateInResourcesPath = templateInResourcesDir.toPath();
        Path templateInRepoDir = templatesPath.resolve(template.getResourceName());
        fileTracker.unlinkTrackedFiles(project, Optional.of(templateInRepoDir), Optional.empty());
        if (templateInRepoDir.toFile().mkdir()) {
            log.info("Created {} template folder {}", template.getResourceName(), templateInRepoDir);
        }
        log.info("Walking over template in dir {}", templateInResourcesDir);
        Files.walk(templateInResourcesPath)
                .map(Path::toFile)
                .filter(File::isFile)
                .forEach(source -> {
                    Path destination = templateInRepoDir.resolve(templateInResourcesPath.relativize(source.toPath()));
                    Path projectRelativePath = project.getPath().relativize(destination);
                    if (!fileTracker.trackFile(project, projectRelativePath)) {
                        log.info("Skipping file overriden by user {}", projectRelativePath);
                        return;
                    }
                    Optional.ofNullable(destination.toFile().getParentFile())
                            .ifPresent(File::mkdirs);

                    log.info("Copying template file {} to {}", source.getName(), destination);
                    try {
                        Files.copy(source.toPath(), destination, StandardCopyOption.REPLACE_EXISTING);
                    } catch (IOException ex) {
                        throw new RuntimeException(ex);
                    }
                });

        // Finally, apply our template
        applyTemplate(project, templateInRepoDir.toFile(), processorPath, Optional.empty(), processorContext);
    }

    private void applyTemplate(Project project, File templateDir, Path destination, Optional<Long> maxDepthOpt, DatasprayContext context) {
        applyTemplate(project, templateDir, destination, maxDepthOpt, context, true);
    }

    private void applyTemplate(Project project, File templateDir, Path destination, Optional<Long> maxDepthOpt, DatasprayContext context, boolean isRoot) {
        if (isRoot) {
            fileTracker.unlinkTrackedFiles(project, Optional.of(destination), maxDepthOpt);
        }
        for (File item : templateDir.listFiles()) {
            if (item.isDirectory()) {
                applyTemplate(project, item, destination.resolve(item.getName()), maxDepthOpt, context, false);
            } else if (item.isFile()) {
                boolean isSample = item.getName().endsWith(MUSTACHE_FILE_EXTENSION_SAMPLE);
                if (isSample || item.getName().endsWith(MUSTACHE_FILE_EXTENSION_TEMPLATE)) {
                    Optional<ExpandedFile> expandedFileOpt = expandFile(project, item.getName(), context);
                    if (expandedFileOpt.isPresent() && (!isSample || !expandedFileOpt.get().getPath().toFile().exists())) {
                        Path localDestination = destination.resolve(expandedFileOpt.get().getPath());
                        String expandedFileNameWithoutSuffix = expandedFileOpt.get().getFilename()
                                .substring(0, expandedFileOpt.get().getFilename().length()
                                        - (isSample ? MUSTACHE_FILE_EXTENSION_SAMPLE : MUSTACHE_FILE_EXTENSION_TEMPLATE).length());
                        Path localFile = localDestination.resolve(expandedFileNameWithoutSuffix);
                        if (!isSample) {
                            Path localFilePath = localDestination.resolve(expandedFileNameWithoutSuffix);
                            if (!fileTracker.trackFile(project, localFilePath)) {
                                log.debug("Skipping file overriden by user {}", item.getName());
                                continue;
                            }
                        } else {
                            if (localFile.toFile().exists()) {
                                log.debug("Skipping sample file which already exists {}", item.getName());
                                continue;
                            }
                        }
                        runMustache(item, localFile, context);
                    }
                } else if (item.getName().endsWith(MUSTACHE_FILE_EXTENSION_INCLUDE)) {
                    // Skip sub-templates
                } else {
                    log.info("Skipping non-mustache file {}", item.getName());
                }
            }
        }
    }

    @SneakyThrows
    private void runMustache(File mustacheFile, Path destinationFile, DatasprayContext context) {
        String mustacheStr = Resources.toString(mustacheFile.toURI().toURL(), Charsets.UTF_8);
        String content = runMustache(mustacheStr, context, Optional.of(name ->
                new FileReader(
                        mustacheFile.getParentFile().toPath().resolve(name + MUSTACHE_FILE_EXTENSION_INCLUDE).toFile(),
                        Charsets.UTF_8)));
        if (Strings.isNullOrEmpty(content)) {
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

    private Optional<ExpandedFile> expandFile(Project project, String templatePath, DatasprayContext parentContext) {
        // Since a filename cannot contain '/', use underscores '_' instead
        templatePath = templatePath.replaceAll("\\{\\{_", "{{/");
        String generatedPath = runMustache(templatePath, contextBuilder.createForFilename(parentContext, project), Optional.empty());
        if (Strings.isNullOrEmpty(generatedPath)) {
            return Optional.empty();
        }
        List<String> levels = Lists.newArrayList();
        StringBuilder levelBuilder = new StringBuilder();
        for (int i = 0; i < generatedPath.length(); i++) {
            char currentChar = generatedPath.charAt(i);
            if (currentChar == File.separatorChar) {
                levels.add(levelBuilder.toString());
                levelBuilder = new StringBuilder();
            } else {
                levelBuilder.append(currentChar);
            }
        }
        return Optional.of(new ExpandedFile(
                Path.of(".", levels.toArray(String[]::new)).normalize(),
                levelBuilder.toString()));
    }

    @Value
    private static class ExpandedFile {
        Path path;
        String filename;
    }

    @SneakyThrows
    private File getTemplateFromResources(Template template) {
        return new File(Thread.currentThread().getContextClassLoader().getResource("template/" + template.getResourceName()).toURI());
    }

    private boolean isWindows() {
        return System.getProperty("os.name")
                .toLowerCase().startsWith("windows");
    }

    public static Module module(boolean useProcessInputOutput) {
        return new AbstractModule() {
            @Override
            protected void configure() {
                bind(Codegen.class).to(CodegenImpl.class).asEagerSingleton();
                if (useProcessInputOutput) {
                    bind(Redirect.class).annotatedWith(Names.named("IN")).toInstance(Redirect.INHERIT);
                    bind(Redirect.class).annotatedWith(Names.named("OUT")).toInstance(Redirect.INHERIT);
                    bind(Redirect.class).annotatedWith(Names.named("ERR")).toInstance(Redirect.INHERIT);
                }
            }
        };
    }
}
