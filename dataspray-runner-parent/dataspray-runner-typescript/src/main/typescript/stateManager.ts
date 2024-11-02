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

import {AttributeValue, DynamoDBClient, GetItemCommand, ReturnValue, UpdateItemCommand} from "@aws-sdk/client-dynamodb";
import {mergeStrings} from "./util/stringSerdeUtil";

export interface StateManager {

    /**
     * Get the key of the item this state manager is managing.
     */
    getKey(): string[];


    /**
     * Update the TTL to reset the expiration time.
     */
    touch(): Promise<void>;


    getJson<T>(key: string): Promise<T | null>;

    setJson<T>(key: string, item: T): Promise<void>;


    getString(key: string): Promise<string>;

    setString(key: string, value: string): Promise<void>;


    getBoolean(key: string): Promise<boolean>;

    setBoolean(key: string, value: boolean): Promise<void>;


    getNumber(key: string): Promise<number>;

    setNumber(key: string, number: number): Promise<void>;

    addToNumber(key: string, increment: number): Promise<void>;


    getStringSet(key: string): Promise<Set<string>>;

    setStringSet(key: string, set: Set<string>): Promise<void>;

    addToStringSet(key: string, ...values: string[]): Promise<void>;

    deleteFromStringSet(key: string, ...values: string[]): Promise<void>;


    delete(key: string): Promise<void>;


    flush(): Promise<void>;

    close(): Promise<void>;
}

export class DynamoStateManager implements StateManager {
    public static readonly TTL_IN_EPOCH_SEC_KEY_NAME = "ttlInEpochSec";
    private static readonly SORT_KEY = "state";
    private tableName: string;
    private key: string[];
    private keyStr: string;
    private dynamo: DynamoDBClient;
    private ttlInSec: number | undefined;
    private setUpdates: Map<string, string> = new Map();
    private removeUpdates: Set<string> = new Set();
    private addUpdates: Map<string, string> = new Map();
    private deleteUpdates: Map<string, string> = new Map();
    private nameMap: Map<string, string> = new Map();
    private valMap: Map<string, AttributeValue> = new Map();
    private item?: Record<string, AttributeValue>;
    private isClosed: boolean = false;

    constructor(tableName: string, dynamo: DynamoDBClient, key: string[], ttlInSec?: number) {
        this.tableName = tableName;
        this.key = key;
        this.keyStr = mergeStrings(key);
        this.dynamo = dynamo;
        this.ttlInSec = ttlInSec;
    }


    public getKey(): string[] {
        return this.key
    }

    public async touch(): Promise<void> {
        this.checkClosed();
        if (this.ttlInSec === undefined) {
            return;
        }
        const ttlInEpochSec = Math.floor(Date.now() / 1000) + this.ttlInSec;
        await this.set(DynamoStateManager.TTL_IN_EPOCH_SEC_KEY_NAME, {N: ttlInEpochSec.toString()});
    }

    public async getJson<T>(key: string): Promise<T | null> {
        const jsonString = await this.getString(key);
        return jsonString != null ? JSON.parse(jsonString) : null;
    }

    public async setJson<T>(key: string, item: T): Promise<void> {
        await this.setString(key, JSON.stringify(item));
    }

    public async getString(key: string): Promise<string> {
        this.checkClosed();
        return (await this.get(key))?.S || '';
    }

    public async setString(key: string, value: string): Promise<void> {
        this.checkClosed();
        await this.flushForKey(key);
        await this.touch();
        await this.set(key, {S: value});
    }

    public async getBoolean(key: string): Promise<boolean> {
        this.checkClosed();
        const attribute = await this.get(key);
        return attribute?.BOOL || false;
    }

    public async setBoolean(key: string, value: boolean): Promise<void> {
        this.checkClosed();
        await this.flushForKey(key);
        await this.touch();
        await this.set(key, {BOOL: value});
    }

    public async getNumber(key: string): Promise<number> {
        this.checkClosed();
        const attribute = await this.get(key);
        return attribute?.N ? parseFloat(attribute.N) : 0;
    }

    public async setNumber(key: string, number: number): Promise<void> {
        this.checkClosed();
        await this.flushForKey(key);
        await this.touch();
        await this.set(key, {N: number.toString()});
    }

    public async addToNumber(key: string, increment: number): Promise<void> {
        this.checkClosed();
        await this.flushForKey(key);
        await this.touch();

        const fieldMappingKey = this.fieldMapping(key);
        const constantMappingZero = this.constantMapping('zero', {N: '0'});
        const constantMappingKey = this.constantMapping(key, {N: increment.toString()});

        this.setUpdates.set(key, `${fieldMappingKey} = if_not_exists(${fieldMappingKey}, ${constantMappingZero}) + ${constantMappingKey}`);
    }

    public async getStringSet(key: string): Promise<Set<string>> {
        this.checkClosed();
        const attribute = await this.get(key);
        return new Set(attribute?.SS || []);
    }

    public async setStringSet(key: string, set: Set<string>): Promise<void> {
        this.checkClosed();
        await this.flushForKey(key);
        await this.touch();
        await this.set(key, {SS: Array.from(set)});
    }

    public async addToStringSet(key: string, ...values: string[]): Promise<void> {
        this.checkClosed();
        await this.flushForKey(key);
        await this.touch();

        const fieldMappingKey = this.fieldMapping(key);
        const constantMappingKey = this.constantMapping(key, {SS: values});

        this.addUpdates.set(key, `${fieldMappingKey} ${constantMappingKey}`);
    }

    public async deleteFromStringSet(key: string, ...values: string[]): Promise<void> {
        this.checkClosed();
        await this.flushForKey(key);
        await this.touch();

        const fieldMappingKey = this.fieldMapping(key);
        const constantMappingKey = this.constantMapping(key, {SS: values});

        this.deleteUpdates.set(key, `${fieldMappingKey} ${constantMappingKey}`);
    }

    public async delete(key: string): Promise<void> {
        this.checkClosed();
        await this.flushForKey(key);
        await this.touch();
        this.removeUpdates.add(key);
    }

    private async set(key: string, value: AttributeValue): Promise<void> {
        const fieldMappingKey = this.fieldMapping(key);
        const constantMappingKey = this.constantMapping(key, value);

        this.setUpdates.set(key, `${fieldMappingKey} = ${constantMappingKey}`);
    }

    private async get(key: string): Promise<AttributeValue | undefined> {
        return (await this.getAttrVals())[key];
    }

    private async getAttrVals(): Promise<Record<string, AttributeValue>> {
        return await this.flushAndGet() || await this.getItem()
    }

    private checkClosed() {
        if (!this.isClosed) throw new Error('Cannot use state manager that is already closed');
    }

    private async flushForKey(key: string) {
        if (this.setUpdates.has(key)
                || this.removeUpdates.has(key)
                || this.addUpdates.has(key)
                || this.deleteUpdates.has(key)) {
            await this.flushAndGet();
        }
        this.item = undefined;
    }

    public async flush(): Promise<void> {
        await this.flushAndGet()
    }

    /**
     * Flushes the current state to the database if any pending updates exist.
     *
     * @return The updated item if any updates were flushed otherwise empty.
     */
    private async flushAndGet(): Promise<Record<string, AttributeValue> | undefined> {
        if (!this.setUpdates.size
                && !this.removeUpdates.size
                && !this.addUpdates.size
                && !this.deleteUpdates.size) {
            return;
        }
        let updateExpression: string = "";
        if (this.setUpdates.size) {
            updateExpression += " SET " + Array.from(this.setUpdates.values()).join(", ");
        }
        if (this.removeUpdates.size) {
            updateExpression += " REMOVE " + Array.from(this.removeUpdates).join(", ");
        }
        if (this.addUpdates.size) {
            updateExpression += " ADD " + Array.from(this.addUpdates).join(", ");
        }
        if (this.deleteUpdates.size) {
            updateExpression += " DELETE " + Array.from(this.deleteUpdates).join(", ");
        }
        console.log(`Flushing dynamo update for table ${this.tableName} key ${this.key}: ${updateExpression}`)
        this.item = (await this.dynamo.send(new UpdateItemCommand({
            TableName: this.tableName,
            Key: {
                pk: {S: this.keyStr},
                sk: {S: DynamoStateManager.SORT_KEY}
            },
            UpdateExpression: updateExpression,
            ExpressionAttributeNames: Object.fromEntries(this.nameMap),
            ExpressionAttributeValues: Object.fromEntries(this.valMap),
            ReturnValues: ReturnValue.ALL_NEW,
        }))).Attributes;

        this.setUpdates.clear();
        this.removeUpdates.clear();
        this.deleteUpdates.clear();
        this.addUpdates.clear();
        this.nameMap.clear();
        this.valMap.clear();

        return this.item;
    }

    private async getItem(): Promise<Record<string, AttributeValue>> {
        if (!this.item) {
            console.log(`Fetching dynamo item for table ${this.tableName} partitionKey ${this.keyStr} sortKey ${DynamoStateManager.SORT_KEY}`)
            this.item = (await this.dynamo.send(new GetItemCommand({
                TableName: this.tableName,
                Key: {
                    pk: {S: this.keyStr},
                    sk: {S: DynamoStateManager.SORT_KEY}
                },
            }))).Item || {};
        }
        return this.item;
    }

    public fieldMapping(fieldName: string): string {
        const mappedName = "#" + this.sanitizeFieldMapping(fieldName);
        this.nameMap.set(mappedName, fieldName);
        return mappedName;
    }

    public constantMapping(name: string, value: AttributeValue): string {
        const mappedName = ":" + this.sanitizeFieldMapping(name);
        this.valMap.set(mappedName, value);
        return mappedName;
    }

    private sanitizeFieldMapping(fieldName: string): string {
        return fieldName.replace(/(^[^a-z])|[^a-zA-Z0-9]/g, 'x');
    }

    public async close(): Promise<void> {
        await this.flush();
        this.isClosed = true;
    }
}