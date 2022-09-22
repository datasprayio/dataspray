// SPDX-FileCopyrightText: 2019-2021 Matus Faro <matus@smotana.com>
// SPDX-License-Identifier: Apache-2.0
package io.dataspray.common;

import com.github.rholder.retry.RetryException;
import com.github.rholder.retry.Retryer;
import com.github.rholder.retry.RetryerBuilder;
import com.github.rholder.retry.StopStrategies;
import com.github.rholder.retry.WaitStrategies;
import lombok.extern.slf4j.Slf4j;

import javax.enterprise.context.ApplicationScoped;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@Slf4j
@ApplicationScoped
public class NetworkUtil {

    public static NetworkUtil get() {
        return new NetworkUtil();
    }

    public int findFreePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    public int findRandomFreePort(int rangeStartInclusive, int rangeEndExclusive) {
        while (true) {
            int port = ThreadLocalRandom.current().nextInt(rangeStartInclusive, rangeEndExclusive);
            try (ServerSocket socket = new ServerSocket()) {
                socket.setReuseAddress(false);
                socket.bind(new InetSocketAddress(InetAddress.getByName("localhost"), port), 1);
                return socket.getLocalPort();
            } catch (Exception ex) {
            }
        }
    }

    public int findAscendingFreePort(int portStart) {
        int port = portStart;
        while (true) {
            try (ServerSocket socket = new ServerSocket()) {
                socket.setReuseAddress(false);
                socket.bind(new InetSocketAddress(InetAddress.getByName("localhost"), port), 1);
                return socket.getLocalPort();
            } catch (Exception ex) {
            }
            port++;
            if (port == 65535) {
                port = 1;
            } else if (port == portStart) {
                throw new RuntimeException("Looped through all ports and they are all in use?!?!?");
            }
        }
    }

    public void waitUntilPortOpen(int port) throws IOException {
        waitUntilPortOpen("127.0.0.1", port);
    }

    public void waitUntilPortOpen(String urlStr) throws IOException {
        URL url = new URL(urlStr);
        waitUntilPortOpen(
                url.getHost(),
                url.getPort() != -1
                        ? url.getPort()
                        : url.getDefaultPort());
    }

    public void waitUntilPortOpen(String hostname, int port) throws IOException {
        Retryer<Boolean> retryer = RetryerBuilder.<Boolean>newBuilder()
                .retryIfResult(result -> !result)
                .withStopStrategy(StopStrategies.stopAfterDelay(5, TimeUnit.MINUTES))
                .withWaitStrategy(WaitStrategies.exponentialWait(50, 5, TimeUnit.SECONDS))
                .build();
        try {
            retryer.call(() -> {
                try {
                    Socket s = new Socket(hostname, port);
                    s.close();
                    return true;

                } catch (IOException e) {
                    return false;
                }
            });
        } catch (ExecutionException | RetryException ex) {
            throw new IOException(ex);
        }
    }
}
