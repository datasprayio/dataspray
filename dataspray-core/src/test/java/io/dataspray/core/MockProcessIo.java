/*
 * Copyright 2023 Matus Faro
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

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.dataspray.common.TestResourceUtil;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import io.quarkus.test.junit.QuarkusTestProfile;
import jakarta.inject.Singleton;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

@Slf4j
@Singleton
public class MockProcessIo implements QuarkusTestResourceLifecycleManager {
    private static final org.slf4j.Logger logOut = org.slf4j.LoggerFactory.getLogger("OUT");
    private static final org.slf4j.Logger logErr = org.slf4j.LoggerFactory.getLogger("ERR");

    private static final String EOF = "EOF";
    private Path tempDir;
    private File in;
    private File out;
    private File err;
    private Thread outTailer;
    private Thread errTailer;
    private volatile boolean acceptEof = false;

    @SneakyThrows
    private void tail(File file, Consumer<String> logger) {
        try (FileReader fileReader = new FileReader(file);
             BufferedReader bufferedReader = new BufferedReader(fileReader)) {
            String line;
            while (true) {
                line = bufferedReader.readLine();
                if (line == null) {
                    //noinspection BusyWait
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

    @SneakyThrows
    public void write(String data) {
        write(data, in);
    }

    @SneakyThrows
    public void write(String data, File file) {
        Files.writeString(file.toPath(), data, Charsets.UTF_8, StandardOpenOption.APPEND);
    }

    @Override
    @SneakyThrows
    public void inject(Object testInstance) {
        TestResourceUtil.injectSelf(testInstance, this);
    }

    @Override
    @SneakyThrows
    public Map<String, String> start() {
        tempDir = Files.createTempDirectory(UUID.randomUUID().toString());
        tempDir.toFile().deleteOnExit();
        in = Path.of(tempDir.toString(), "in").toFile();
        out = Path.of(tempDir.toString(), "out").toFile();
        err = Path.of(tempDir.toString(), "err").toFile();
        in.createNewFile();
        out.createNewFile();
        err.createNewFile();
        in.deleteOnExit();
        out.deleteOnExit();
        err.deleteOnExit();
        outTailer = new Thread(() -> tail(out, line -> logOut.info("{}", line)), "out tailer");
        errTailer = new Thread(() -> tail(err, line -> logErr.error("{}", line)), "err tailer");
        outTailer.start();
        errTailer.start();
        return ImmutableMap.of(
                BuilderImpl.BUILDER_IN, in.toString(),
                BuilderImpl.BUILDER_OUT, out.toString(),
                BuilderImpl.BUILDER_ERR, err.toString());
    }

    @Override
    @SneakyThrows
    public void stop() {
        acceptEof = true;

        write(EOF, out);
        write(EOF, err);

        outTailer.join();
        errTailer.join();
    }

    public static class TestProfile implements QuarkusTestProfile {

        @Override
        public List<TestResourceEntry> testResources() {
            return ImmutableList.of(new TestResourceEntry(MockProcessIo.class));
        }
    }
}
