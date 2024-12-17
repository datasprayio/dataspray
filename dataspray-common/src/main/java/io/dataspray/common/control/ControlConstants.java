// SPDX-FileCopyrightText: 2019-2021 Matus Faro <matus@smotana.com>
// SPDX-License-Identifier: Apache-2.0
package io.dataspray.common.control;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ApplicationScoped
public class ControlConstants {

    /**
     * AWS limit of uncompressed package size; enforced client-side.
     * If you need to increase this limit, need to package as a container.
     */
    public static final long CODE_MAX_SIZE_UNCOMPRESSED_IN_BYTES = 250 * 1024 * 1024;
    /**
     * There is no AWS limit on compressed package size, this is a server-side enforced sanity check.
     */
    public static final long CODE_MAX_SIZE_COMPRESSED_IN_BYTES = 200 * 1024 * 1024;

    private ControlConstants() {
        // Disallow ctor
    }
}
