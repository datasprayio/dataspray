// SPDX-FileCopyrightText: 2019-2022 Matus Faro <matus@smotana.com>
// SPDX-License-Identifier: Apache-2.0
package io.dataspray.runner;

public interface RawCoordinator {

    void send(byte[] data, StoreType storeType, String storeName, String streamName);

    enum StoreType {
        KAFKA
    }
}
