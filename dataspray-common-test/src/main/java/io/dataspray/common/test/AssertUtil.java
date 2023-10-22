// SPDX-FileCopyrightText: 2019-2022 Matus Faro <matus@smotana.com>
// SPDX-License-Identifier: Apache-2.0
package io.dataspray.common.test;

import com.github.rholder.retry.RetryerBuilder;
import com.github.rholder.retry.StopStrategies;
import com.github.rholder.retry.WaitStrategies;

import java.util.concurrent.TimeUnit;

public class AssertUtil {

    public static <T> T retry(Assertable<T> assertable) throws Exception {
        return RetryerBuilder.<T>newBuilder()
                .withStopStrategy(StopStrategies.stopAfterDelay(30, TimeUnit.SECONDS))
                .withWaitStrategy(WaitStrategies.exponentialWait())
                .retryIfExceptionOfType(Throwable.class)
                .build()
                .call(() -> {
                    assertable.assertIt();
                    return null;
                });
    }

    public interface Assertable<T> {
        T assertIt() throws Exception;
    }

    private AssertUtil() {
        // disallow ctor
    }

}
