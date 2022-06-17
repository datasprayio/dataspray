package com.smotana.dataspray.core;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.samskivert.mustache.Mustache;
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

import javax.validation.constraints.NotNull;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.lang.ProcessBuilder.Redirect;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Collection;

@Slf4j
public class CodegenImpl implements Codegen {
    private static final String TEMPLATES_FOLDER = ".templates";
    private static final String MUSTACHE_FILE_EXTENSION_TEMPLATE = ".template.mustache";
    private static final String MUSTACHE_FILE_EXTENSION_INCLUDE = ".include.mustache";

    @Inject
    DefinitionLoader definitionLoader;

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
        project.getDefinition()
                .getJavaProcessors()
                .stream()
                .flatMap(Collection::stream)
                .map(Item::getName)
                .forEach(processorName -> generateJava(project, processorName));
    }

    @Override
    public void generateJava(Project project, String processorName) {
        JavaProcessor processor = project.getDefinition()
                .getJavaProcessors()
                .stream()
                .flatMap(Collection::stream)
                .filter(p -> p.getName().equals(processorName))
                .findAny()
                .orElseThrow(() -> new RuntimeException("Cannot find java processor with name " + processorName));

        Path processorPath = createProcessorDir(project, processorName);
        Template template;
        switch (processor.getDialect()) {
            case VANILLA -> template = Template.JAVA;
            case SAMZA -> throw new RuntimeException("Samza not yet supported");
            default -> throw new RuntimeException(processor.getDialect() + " not yet supported");
        }
        codegen(project, processorPath, template);
    }

    @Override
    public void installAll(Project project) {
        project.getDefinition()
                .getJavaProcessors()
                .stream()
                .flatMap(Collection::stream)
                .map(Item::getName)
                .forEach(processorName -> installJava(project, processorName));
    }

    @SneakyThrows
    @Override
    public void installJava(Project project, String processorName) {
        JavaProcessor processor = project.getDefinition()
                .getJavaProcessors()
                .stream()
                .flatMap(Collection::stream)
                .filter(p -> p.getName().equals(processorName))
                .findAny()
                .orElseThrow(() -> new RuntimeException("Cannot find java processor with name " + processorName));

        ProcessBuilder processBuilder = isWindows()
                ? new ProcessBuilder("cmd.exe", "/c", "mvn clean install")
                : new ProcessBuilder("sh", "-c", "mvn clean install");
        processBuilder.directory(getProcessorDir(project, processorName).toFile());
        processBuilder.redirectError(Redirect.INHERIT);
        processBuilder.redirectInput(Redirect.INHERIT);
        processBuilder.redirectOutput(Redirect.INHERIT);
        Process process = processBuilder.start();

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("Process exited with non-zero status " + exitCode);
        }
    }

    private Path getProcessorDir(Project project, String processorName) {
        return Path.of(project.getPath().toString(), processorName);
    }

    private Path createProcessorDir(Project project, String processorName) {
        Path processorPath = getProcessorDir(project, processorName);
        if (processorPath.toFile().mkdir()) {
            log.info("Created processor folder " + processorPath);
        }
        return processorPath;
    }

    @SneakyThrows
    private void codegen(Project project, Path processorPath, Template template) {
        // Create templates folder
        Path templatesPath = Path.of(project.getPath().toString(), TEMPLATES_FOLDER);
        if (templatesPath.toFile().mkdir()) {
            log.info("Created templates folder " + templatesPath);
        }

        // Initialize templates folder
        applyTemplate(getTemplateFromResources(Template.TEMPLATES), templatesPath, new MustacheContext(project));

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
            Path destination = Paths.get(templateInRepoDir.toString(), source.toString()
                    .substring(templateInResourcesDir.toString().length()));
            try {
                log.info("Copying template file {}", source.getFileName());
                Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        });

        // Finally apply our template
        applyTemplate(templateInRepoDir.toFile(), processorPath, new MustacheContext(project));
    }

    private void applyTemplate(File templateDir, Path destination, MustacheContext context) {
        for (File item : templateDir.listFiles()) {
            if (item.isDirectory()) {
                applyTemplate(item, Path.of(destination.toString(), item.getName()), context);
            } else if (item.isFile()) {
                if (item.getName().endsWith(MUSTACHE_FILE_EXTENSION_TEMPLATE)) {
                    runMustache(item,
                            Path.of(destination.toString(),
                                    StringUtils.removeEnd(item.toPath().getFileName().toString(), MUSTACHE_FILE_EXTENSION_TEMPLATE)),
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
    private void runMustache(File mustacheFile, Path destinationFile, MustacheContext context) {
        Mustache.Compiler c = Mustache.compiler().withLoader(name -> new FileReader(
                Path.of(mustacheFile.getParent(), name + MUSTACHE_FILE_EXTENSION_INCLUDE).toFile(), Charsets.UTF_8));
        String template = Resources.toString(mustacheFile.toURI().toURL(), Charsets.UTF_8);
        String content = c.compile(template).execute(context);
        Files.writeString(destinationFile, content, Charsets.UTF_8);
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

    @Value
    private static class MustacheContext {
        @NotNull
        Project project;
    }

    public static Module module() {
        return new AbstractModule() {
            @Override
            protected void configure() {
                bind(Codegen.class).to(CodegenImpl.class).asEagerSingleton();
            }
        };
    }
}
