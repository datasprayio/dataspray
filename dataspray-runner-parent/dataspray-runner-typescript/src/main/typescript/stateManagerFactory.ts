/*
 * Copyright 2024 Matus Faro
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

import {DynamoDBClient} from "@aws-sdk/client-dynamodb";
import {DynamoStateManager, StateManager} from "./stateManager";

export interface StateManagerFactory {

    getStateManager(key: string[], ttlInEpochSec?: number): StateManager;

    getDynamoClient(): DynamoDBClient;

    flushAll(): void;

    closeAll(): void;
}

export class StateManagerFactoryImpl implements StateManagerFactory {
    private static instance: StateManagerFactory | null = null;
    private readonly tableName: string;
    private readonly dynamo: DynamoDBClient;
    private stateManagers: Map<string[], StateManager> = new Map();

    constructor(tableName: string, dynamo: DynamoDBClient) {
        this.tableName = tableName;
        this.dynamo = dynamo;
    }

    static get(): StateManagerFactory | null {
        return StateManagerFactoryImpl.instance || null;
    }

    static getOrCreate(): StateManagerFactory {
        if (StateManagerFactoryImpl.instance === null) {
            StateManagerFactoryImpl.instance = new StateManagerFactoryImpl(
                    process.env.STATE_TABLE_NAME!,
                    new DynamoDBClient()
            );
        }
        return StateManagerFactoryImpl.instance;
    }

    public getStateManager(key: string[], ttlInSec?: number): StateManager {
        var stateManager = this.stateManagers.get(key);
        if (!stateManager) {
            stateManager = new DynamoStateManager(this.tableName, this.dynamo, key, ttlInSec);
            this.stateManagers.set(key, stateManager);
        }
        return stateManager;
    }

    getDynamoClient(): DynamoDBClient {
        return this.dynamo;
    }

    public flushAll(): void {
        this.stateManagers.forEach(stateManager => stateManager.flush());
    }

    public closeAll(): void {
        this.stateManagers.forEach(stateManager => stateManager.close());
    }
}
