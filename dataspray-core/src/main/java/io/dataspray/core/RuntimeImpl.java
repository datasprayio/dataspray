package io.dataspray.core;

import io.dataspray.core.definition.model.JavaProcessor;
import io.dataspray.stream.client.StreamApi;
import io.dataspray.stream.control.client.ControlApi;
import io.dataspray.stream.control.client.model.DeployRequest;
import io.dataspray.stream.control.client.model.TaskStatus;
import io.dataspray.stream.control.client.model.UploadCodeRequest;
import io.dataspray.stream.control.client.model.UploadCodeResponse;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.revwalk.RevCommit;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.io.File;
import java.nio.file.Path;
import java.util.Iterator;

import static com.google.common.base.Preconditions.checkState;

@Slf4j
@ApplicationScoped
public class RuntimeImpl implements Runtime {

    @Inject
    ContextBuilder contextBuilder;
    @Inject
    StreamApi streamApi;

    @Override
    @SneakyThrows
    public void statusAll(String apiKey, Project project) {
        streamApi.control(apiKey).statusAll()
                .getTasks()
                .forEach(this::printStatus);
    }

    @Override
    public void deploy(String apiKey, Project project, JavaProcessor processor) {
        switch (processor.getTarget()) {
            case DATASPRAY:
                deployStream(apiKey, project, processor);
                break;
            case SAMZA:
            case FLINK:
                throw new RuntimeException("Not yet implemented");
            default:
                throw new RuntimeException("Unknown target " + processor.getTarget());
        }
    }

    @SneakyThrows
    private void deployStream(String apiKey, Project project, JavaProcessor processor) {
        // First get S3 upload presigned url
        Path processorDir = CodegenImpl.getProcessorDir(project, processor.getNameDir());
        File codeZipFile = processorDir.resolve(Path.of("target", processor.getNameDir() + ".zip")).toFile();
        checkState(!codeZipFile.isFile(), "Missing code zip file, forgot to install? Expecting: %s", codeZipFile.getPath());
        ControlApi controlApi = streamApi.control(apiKey);
        UploadCodeResponse uploadCodeResponse = controlApi.uploadCode(new UploadCodeRequest()
                .taskId(processor.getNameDir())
                .contentLengthBytes(codeZipFile.length()));

        // Upload to S3
        streamApi.uploadCode(uploadCodeResponse.getPresignedUrl(), codeZipFile);

        // Initiate deployment
        TaskStatus deployStatus = controlApi.deploy(new DeployRequest()
                .taskId(processor.getName())
                .runtime(DeployRequest.RuntimeEnum.JAVA11)
                .codeUrl(uploadCodeResponse.getCodeUrl()));

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
}
