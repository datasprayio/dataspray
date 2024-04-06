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

import Container from "@cloudscape-design/components/container";
import Header from "@cloudscape-design/components/header";
import {getClient} from "../util/dataSprayClientWrapper";
import {Alert, Button, SpaceBetween, Table} from "@cloudscape-design/components";
import useSWR from "swr";
import {useAuth} from "../auth/auth";
import React, {useState} from "react";
import {getHeaderCounterTextSingle} from "../table/tableUtil";
import {ApiKey} from "dataspray-client";
import {dateToYyyyMmDd} from "../util/dateUtil";
import {DeleteApiKeyModal} from "./DeleteApiKeyModal";

export const ApiKeys = () => {
    const [error, setError] = React.useState<string | undefined>(undefined);
    const {currentOrganizationName} = useAuth();
    const [selectedApiKey, setSelectedApiKey] = React.useState<ApiKey | undefined>(undefined);
    const swr = useSWR(currentOrganizationName, (currOrgName) => getClient().authNz().listApiKeys({
        organizationName: currOrgName,
    }).then(apiKeys => apiKeys.keys), {
        // No need to aggressively refresh
        revalidateOnFocus: false,
        revalidateIfStale: false,
        revalidateOnReconnect: false,
    });

    const [showDeleteApiKeyModal, setShowDeleteApiKeyModal] = useState<boolean>(false);
    const deleteApiKeyModal = (
            <DeleteApiKeyModal
                    apiKey={selectedApiKey}
                    show={showDeleteApiKeyModal}
                    onHide={() => {
                        setShowDeleteApiKeyModal(false)
                    }}
                    onDelete={async () => {
                        try {
                            await getClient().authNz().revokeApiKey({
                                organizationName: currentOrganizationName!,
                                apiKeyId: selectedApiKey!.id,
                            });
                            swr.mutate();
                        } catch (e: any) {
                            console.error('Failed to revoke access key', e ?? 'Unknown error')
                            setError(e?.message || ('Failed to revoke access key: ' + (e || 'Unknown error')))
                        }
                        setShowDeleteApiKeyModal(false);
                    }}
            />
    );

    return (
            <Container
                    header={
                        <Header
                                variant="h3"
                                counter={getHeaderCounterTextSingle(swr.data?.length || 0, false)}
                                actions={
                                    <SpaceBetween size="xs" direction="horizontal">
                                        <Button disabled={!selectedApiKey}
                                                onClick={() => setShowDeleteApiKeyModal(true)}>
                                            Revoke
                                        </Button>
                                        <Button variant="primary" href='/account/security/accesskey/create'>
                                            Create
                                        </Button>
                                    </SpaceBetween>
                                }
                        >
                            Access Keys
                        </Header>
                    }
            >
                {deleteApiKeyModal}
                <SpaceBetween size='m'>
                    {!!error && (<Alert type='error'>{error}</Alert>)}
                    <Table
                            variant='borderless'
                            columnDefinitions={[
                                {
                                    id: 'id',
                                    header: 'ID',
                                    cell: apiKey => apiKey.id.length > 5 ? apiKey.id.substring(0, 5) : apiKey.id,
                                    sortingField: 'id',
                                    isRowHeader: true,
                                },
                                {
                                    id: 'description',
                                    header: 'Description',
                                    sortingField: 'description',
                                    cell: apiKey => apiKey.description,
                                },
                                {
                                    id: 'expiresAt',
                                    header: 'Expires',
                                    sortingField: 'expiresAt',
                                    cell: apiKey => apiKey.expiresAt ? dateToYyyyMmDd(new Date(apiKey.expiresAt * 1000)) : 'never',
                                },
                                {
                                    id: 'queueWhitelist',
                                    header: 'Scope',
                                    cell: apiKey => apiKey.queueWhitelist?.length ? `Targets: ${apiKey.queueWhitelist?.join(', ')}` : 'Organization',
                                },
                            ]}
                            items={swr.data || []}
                            selectionType='single'
                            selectedItems={selectedApiKey ? [selectedApiKey] : []}
                            onSelectionChange={(e) => setSelectedApiKey(e.detail.selectedItems?.[0] as ApiKey | undefined)}
                            loading={swr.isLoading}
                    />
                </SpaceBetween>
            </Container>
    );
};

