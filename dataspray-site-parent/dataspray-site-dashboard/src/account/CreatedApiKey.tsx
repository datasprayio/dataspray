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

import React, {useState} from "react";
import Header from "@cloudscape-design/components/header";
import Container from "@cloudscape-design/components/container";
import Input from "@cloudscape-design/components/input";
import SpaceBetween from "@cloudscape-design/components/space-between";
import Button from "@cloudscape-design/components/button";
import FormField from "@cloudscape-design/components/form-field";
import {downloadStringFile} from "../util/fileUtil";
import {ApiKeyWithSecret} from "dataspray-client";

export const CreatedApiKey = (props: { apiKey: ApiKeyWithSecret }) => {
    const [hide, setHide] = useState<boolean>(true);
    const [copied, setCopied] = useState<boolean>(false);
    const [downloaded, setDownloaded] = useState<boolean>(false);

    return (
            <Container header={(
                    <Header variant='h2'
                            actions={(
                                    <SpaceBetween direction="horizontal" size="xs">
                                        <Button disabled={downloaded}
                                                onClick={() => {
                                                    downloadStringFile(
                                                            `dataspray-access-key-${props.apiKey.id.substring(0, 4)}.csv`,
                                                            'text/csv',
                                                            "Secret Access Key,Expires,Scope,Description\n" +
                                                            `${props.apiKey.secret},${props.apiKey.expiresAt ? new Date(props.apiKey.expiresAt) : 'never'},${props.apiKey.queueWhitelist?.length ? `targets:${props.apiKey.queueWhitelist.join(';')}` : 'organization'},${props.apiKey.description}`);
                                                    setDownloaded(true);
                                                }}>
                                            {downloaded ? 'Downloaded' : 'Download CSV'}
                                        </Button>
                                        <Button onClick={() => setHide(!hide)}>
                                            {hide ? 'Show' : 'Hide'}
                                        </Button>
                                        <Button variant='primary'
                                                disabled={copied}
                                                onClick={() => {
                                                    window.navigator.clipboard?.writeText(props.apiKey.secret);
                                                    setCopied(true);
                                                }}>
                                            {copied ? 'Copied' : 'Copy'}
                                        </Button>
                                    </SpaceBetween>
                            )}
                            description='If you lose or forget your access key, you cannot retrieve it. Instead, create a new access key and revoke the old key.'
                    >
                        Save your generated Access Key
                    </Header>
            )}>
                <FormField>
                    <Input
                            value={props.apiKey.secret}
                            readOnly
                            type={hide ? 'password' : 'text'}
                    />
                </FormField>
            </Container>
    );
};

