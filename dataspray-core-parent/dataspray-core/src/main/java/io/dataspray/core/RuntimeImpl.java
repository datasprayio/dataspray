package io.dataspray.core;

import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Module;
import io.dataspray.core.definition.model.JavaProcessor;
import io.dataspray.stream.client.StreamApi;
import io.dataspray.stream.client.model.DeployRequest;
import io.dataspray.stream.client.model.TaskStatus;
import io.dataspray.stream.client.model.UploadCodeResponse;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.revwalk.RevCommit;

import java.io.File;
import java.nio.file.Path;
import java.util.Iterator;

import static com.google.common.base.Preconditions.checkState;

@Slf4j
public class RuntimeImpl implements Runtime {

    @Inject
    private ContextBuilder contextBuilder;

    @Override
    @SneakyThrows
    public void statusAll(Project project) {
        new StreamApi().statusAll()
                .getTasks()
                .forEach(this::printStatus);
    }

    @Override
    public void deploy(Project project, JavaProcessor processor) {
        switch (processor.getTarget()) {
            case DATASPRAY -> deployStream(project, processor);
            case SAMZA, FLINK -> throw new RuntimeException("Not yet implemented");
            default -> throw new RuntimeException("Unknown target " + processor.getTarget());
        }
    }

    @SneakyThrows
    private void deployStream(Project project, JavaProcessor processor) {
        // First upload code
        Path processorDir = CodegenImpl.getProcessorDir(project, processor.getNameDir());
        File codeZipFile = processorDir.resolve(Path.of("target", processor.getNameDir() + ".zip")).toFile();
        checkState(!codeZipFile.isFile(), "Missing code zip file, forgot to install? Expecting: %s", codeZipFile.getPath());
        StreamApi streamApi = new StreamApi();
        UploadCodeResponse uploadCodeResponse = streamApi.uploadCode(
                processor.getName(),
                codeZipFile,
                getCommitHash(project));

        // Then initiate deployment
        TaskStatus deployStatus = streamApi.deploy(new DeployRequest()
                .taskId(processor.getName())
                .codeUrl(uploadCodeResponse.getUrl()));
        printStatus(deployStatus);
    }

    private String getCommitHash(Project project) throws GitAPIException {
        Iterator<RevCommit> revs = project.getGit()
                .log()
                .setMaxCount(1)
                .call()
                .iterator();
        if (revs.hasNext()) {
            return revs.next().getName();
        } else {
            return "unknown";
        }
    }

    private void printStatus(TaskStatus taskStatus) {
        log.info("{}\t{}", taskStatus.getTaskId(), taskStatus.getStatus());
    }

    public static Module module() {
        return new AbstractModule() {
            @Override
            protected void configure() {
                bind(Runtime.class).to(RuntimeImpl.class).asEagerSingleton();
            }
        };
    }
}
