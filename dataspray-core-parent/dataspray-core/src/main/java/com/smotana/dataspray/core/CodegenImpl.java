package com.smotana.dataspray.core;

import com.google.common.base.Charsets;
import com.google.common.collect.Lists;
import com.google.common.io.Resources;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import com.samskivert.mustache.Mustache;
import com.samskivert.mustache.Mustache.TemplateLoader;
import com.smotana.dataspray.core.definition.model.DataFormat;
import com.smotana.dataspray.core.definition.model.DataSprayDefinition;
import com.smotana.dataspray.core.definition.model.Item;
import com.smotana.dataspray.core.definition.model.JavaProcessor;
import com.smotana.dataspray.core.definition.parser.DefinitionLoader;
import com.smotana.dataspray.core.sample.SampleProject;
import lombok.SneakyThrows;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
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
    public static final String DATA_FORMATS_FOLDER = ".formats";
    private static final String TEMPLATES_FOLDER = ".templates";
    private static final String MUSTACHE_FILE_EXTENSION_TEMPLATE = ".template.mustache";
    private static final String MUSTACHE_FILE_EXTENSION_INCLUDE = ".include.mustache";

    @Inject
    private DefinitionLoader definitionLoader;
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

    @Override
    public Project initProject(String basePath, String projectName, SampleProject sample) throws IOException {
        Path projectPath = Path.of(basePath, projectName);
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
        File dsProjectFile = Path.of(projectPath.toString(), "ds_project.yml").toFile();
        if (!dsProjectFile.createNewFile()) {
            throw new IOException("File already exists: " + dsProjectFile.getPath());
        }
        DataSprayDefinition definition = sample.getDefinitionForName(projectName);
        try (FileOutputStream fos = new FileOutputStream(dsProjectFile)) {
            fos.write(definitionLoader.toYaml(definition).getBytes(StandardCharsets.UTF_8));
        }

        return new Project(projectPath, git, definition);
    }

    @Override
    public void generateAll(Project project) {
        Optional.ofNullable(project.getDefinition().getDataFormats()).stream()
                .flatMap(Collection::stream)
                .forEach(dataFormat -> generateDataFormat(project, dataFormat));
        Optional.ofNullable(project.getDefinition().getJavaProcessors()).stream()
                .flatMap(Collection::stream)
                .forEach(processor -> generateJava(project, processor));
    }

    @Override
    public void generateDataFormat(Project project, String dataFormatName) {
        generateDataFormat(project, Optional.ofNullable(project.getDefinition().getDataFormats()).stream()
                .flatMap(Collection::stream)
                .filter(p -> p.getName().equals(dataFormatName))
                .findAny()
                .orElseThrow(() -> new RuntimeException("Cannot find data format with name " + dataFormatName)));
    }

    private void generateDataFormat(Project project, DataFormat dataFormat) {
        switch (dataFormat.getSerde()) {
            case BINARY, NUMBER, STRING -> { /* Format is schemaless, Nothing to do*/ }
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
        Path processorPath = createProjectDir(project, processor.getName());
        codegen(project, processorPath, Template.JAVA, contextBuilder.createForProcessor(project, processor));
    }

    @Override
    public void installAll(Project project) {
        Optional.ofNullable(project.getDefinition()
                        .getJavaProcessors())
                .stream()
                .flatMap(Collection::stream)
                .map(Item::getName)
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
        processBuilder.directory(getProjectDir(project, processorName).toFile());
        processBuilder.redirectError(err);
        processBuilder.redirectInput(in);
        processBuilder.redirectOutput(out);
        Process process = processBuilder.start();

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("Process exited with non-zero status " + exitCode);
        }
    }

    private Path getProjectDir(Project project, String name) {
        return Path.of(project.getPath().toString(), name);
    }

    private Path createProjectDir(Project project, String name) {
        return createDir(getProjectDir(project, name));
    }

    private Path getDataFormatDir(Project project, DataFormat dataFormat) {
        return Path.of(project.getPath().toString(), DATA_FORMATS_FOLDER, dataFormat.getName());
    }

    private Path createDataFormatDir(Project project, DataFormat dataFormat) {
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
        Path templatesPath = Path.of(project.getPath().toString(), TEMPLATES_FOLDER);
        if (templatesPath.toFile().mkdir()) {
            log.info("Created templates folder " + templatesPath);
        }

        // Initialize templates folder
        applyTemplate(project, getTemplateFromResources(Template.TEMPLATES), templatesPath, contextBuilder.createForTemplates(project));

        // Copy our template from resources to repo (Skipping over overriden files)
        File templateInResourcesDir = getTemplateFromResources(template);
        Path templateInRepoDir = Path.of(templatesPath.toString(), template.getResourceName());
        if (templateInRepoDir.toFile().mkdir()) {
            log.info("Created {} template folder {}", template.getResourceName(), templatesPath);
        }
        log.info("Walking over template in dir {}", templateInResourcesDir);
        Files.walk(templateInResourcesDir.toPath()).forEach(source -> {
            if (isGitIgnored(project, source)) {
                log.info("Skipping overwrite of source-controlled template file {}", source.getFileName());
                return; // Do not replace checked in files
            }
            Path destination = templateInRepoDir.resolve(source.toString()
                    .substring(templateInResourcesDir.toString().length()));
            try {
                log.info("Copying template file {} to {}", source.getFileName(), destination);
                Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        });

        // Finally, apply our template
        applyTemplate(project, templateInRepoDir.toFile(), processorPath, processorContext);
    }

    private void applyTemplate(Project project, File templateDir, Path destination, DatasprayContext context) {
        for (File item : templateDir.listFiles()) {
            if (item.isDirectory()) {
                applyTemplate(project, item, Path.of(destination.toString(), item.getName()), context);
            } else if (item.isFile()) {
                if (item.getName().endsWith(MUSTACHE_FILE_EXTENSION_TEMPLATE)) {
                    ExpandedFile expandedFile = expandFile(project, item.getName(), context);
                    Path localDestination = destination.resolve(expandedFile.getPath());
                    destination.toFile().mkdirs();
                    runMustache(item,
                            Path.of(localDestination.toString(),
                                    StringUtils.removeEnd(expandedFile.getFilename(), MUSTACHE_FILE_EXTENSION_TEMPLATE)),
                            context);
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
                        Path.of(mustacheFile.getParent(), name + MUSTACHE_FILE_EXTENSION_INCLUDE).toFile(),
                        Charsets.UTF_8)));
        Files.writeString(destinationFile, content, Charsets.UTF_8);
    }

    @SneakyThrows
    private String runMustache(String mustacheStr, DatasprayContext context, Optional<TemplateLoader> templateLoaderOpt) {
        Mustache.Compiler c = Mustache.compiler();
        templateLoaderOpt.ifPresent(c::withLoader);
        return c.compile(mustacheStr).execute(context);
    }

    private ExpandedFile expandFile(Project project, String templatePath, DatasprayContext parentContext) {
        // Since a filename cannot contain '/', use underscores '_' instead
        templatePath = templatePath.replaceAll("\\{\\{_", "{{/");
        String generatedPath = runMustache(templatePath, contextBuilder.createForFilename(parentContext, project), Optional.empty());
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
        return new ExpandedFile(
                Path.of(".", levels.toArray(String[]::new)),
                levelBuilder.toString());
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

    @SneakyThrows
    private boolean isGitIgnored(Project project, Path source) {
        String sourceAbsPath = source.toAbsolutePath().toString();
        return project.getGit()
                .status()
                .addPath(sourceAbsPath)
                .call()
                .getIgnoredNotInIndex()
                // TODO Check if non-existent file is also ignored
                // Right now we only skip if file exists AND is ignored here
                // But we want to also check for file does not exist and is ignored
                // Explore if the Status gives us this information
                .contains(sourceAbsPath);
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
