// SPDX-FileCopyrightText: 2019-2022 Matus Faro <matus@smotana.com>
// SPDX-License-Identifier: Apache-2.0
package com.smotana.dataspray.runner;

public interface RawCoordinator {

    void send(byte[] messageBytes, String destinationName);
}
