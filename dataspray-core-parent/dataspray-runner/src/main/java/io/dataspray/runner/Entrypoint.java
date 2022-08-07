// SPDX-FileCopyrightText: 2019-2022 Matus Faro <matus@smotana.com>
// SPDX-License-Identifier: Apache-2.0
package io.dataspray.runner;

public interface Entrypoint {

    void process(Message<byte[]> message, String sourceName, RawCoordinator coordinator);
}