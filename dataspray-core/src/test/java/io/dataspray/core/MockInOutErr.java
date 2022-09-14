package io.dataspray.core;

import com.google.common.base.Charsets;
import io.quarkus.arc.Priority;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import javax.enterprise.inject.Alternative;
import javax.inject.Named;
import javax.inject.Singleton;
import java.io.BufferedReader;
import java.io.Closeable;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;
import java.util.function.Consumer;

@Slf4j
@Singleton
public class MockInOutErr implements Closeable {
    private static final org.slf4j.Logger logOut = org.slf4j.LoggerFactory.getLogger("OUT");
    private static final org.slf4j.Logger logErr = org.slf4j.LoggerFactory.getLogger("ERR");

    private static final String EOF = "EOF";
    private final Path tempDir;
    private final File in;
    private final File out;
    private final File err;
    private final Thread outTailer;
    private final Thread errTailer;
    private volatile boolean acceptEof = false;

    @SneakyThrows
    public MockInOutErr() {
        tempDir = Files.createTempDirectory(UUID.randomUUID().toString());
        in = Path.of(tempDir.toString(), "in").toFile();
        out = Path.of(tempDir.toString(), "out").toFile();
        err = Path.of(tempDir.toString(), "err").toFile();
        in.createNewFile();
        out.createNewFile();
        err.createNewFile();
        outTailer = new Thread(() -> tail(out, line -> logOut.info("{}", line)));
        errTailer = new Thread(() -> tail(err, line -> logErr.error("{}", line)));
        outTailer.start();
        errTailer.start();
    }

    @SneakyThrows
    private void tail(File file, Consumer<String> logger) {
        try (FileReader fileReader = new FileReader(file);
             BufferedReader bufferedReader = new BufferedReader(fileReader)) {
            String line;
            while (true) {
                line = bufferedReader.readLine();
                if (line == null) {
                    Thread.sleep(300);
                } else if (acceptEof && EOF.equals(line)) {
                    break;
                } else {
                    logger.accept(line);
                }
            }
            while ((line = bufferedReader.readLine()) != null) {
                logger.accept(line);
            }
        }
    }

    @Named("IN")
    @Singleton
    @Alternative
    @Priority(1)
    public ProcessBuilder.Redirect getInput() {
        return ProcessBuilder.Redirect.from(in);
    }

    @Named("OUT")
    @Singleton
    @Alternative
    @Priority(1)
    public ProcessBuilder.Redirect getOutput() {
        return ProcessBuilder.Redirect.appendTo(out);
    }

    @Named("ERR")
    @Singleton
    @Alternative
    @Priority(1)
    public ProcessBuilder.Redirect getError() {
        return ProcessBuilder.Redirect.appendTo(err);
    }

    @SneakyThrows
    public void write(String data) {
        write(data, in);
    }

    @SneakyThrows
    public void write(String data, File file) {
        Files.writeString(file.toPath(), data, Charsets.UTF_8, StandardOpenOption.APPEND);
    }

    @SneakyThrows
    @Override
    public void close() throws IOException {
        acceptEof = true;

        write(EOF, out);
        write(EOF, err);

        outTailer.wait();
        errTailer.wait();

        out.delete();
        err.delete();

        tempDir.toFile().delete();
    }
}