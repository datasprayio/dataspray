package io.dataspray.core;

import com.google.common.base.Charsets;
import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.name.Names;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

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
public class MockInOutErr implements Closeable {
    private static final String EOF = "EOF";
    private final Path tempDir;
    private final File in;
    private final File out;
    private final File err;
    private final Thread outTailer;
    private final Thread errTailer;

    @SneakyThrows
    public MockInOutErr() {
        tempDir = Files.createTempDirectory(UUID.randomUUID().toString());
        in = Path.of(tempDir.toString(), "in").toFile();
        out = Path.of(tempDir.toString(), "out").toFile();
        err = Path.of(tempDir.toString(), "err").toFile();
        in.createNewFile();
        out.createNewFile();
        err.createNewFile();
        outTailer = new Thread(() -> tail(out, line -> log.info("OUT: {}", line)));
        errTailer = new Thread(() -> tail(err, line -> log.error("ERR: {}", line)));
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
                    Thread.sleep(100);
                } else if (EOF.equals(line)) {
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

    public ProcessBuilder.Redirect getInput() {
        return ProcessBuilder.Redirect.from(in);
    }

    public ProcessBuilder.Redirect getOutput() {
        return ProcessBuilder.Redirect.appendTo(out);
    }

    public ProcessBuilder.Redirect getError() {
        return ProcessBuilder.Redirect.appendTo(err);
    }

    public void write(String line) {
        try {
            Files.writeString(in.toPath(), line, Charsets.UTF_8, StandardOpenOption.APPEND);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    @SneakyThrows
    @Override
    public void close() throws IOException {
        Files.writeString(out.toPath(), EOF, Charsets.UTF_8, StandardOpenOption.APPEND);
        Files.writeString(err.toPath(), EOF, Charsets.UTF_8, StandardOpenOption.APPEND);
        outTailer.wait();
        errTailer.wait();
        out.delete();
        err.delete();
        tempDir.toFile().delete();
    }

    public Module module() {
        return new AbstractModule() {
            @Override
            protected void configure() {
                bind(ProcessBuilder.Redirect.class).annotatedWith(Names.named("IN")).toInstance(getInput());
                bind(ProcessBuilder.Redirect.class).annotatedWith(Names.named("OUT")).toInstance(getOutput());
                bind(ProcessBuilder.Redirect.class).annotatedWith(Names.named("ERR")).toInstance(getError());
            }
        };
    }
}
