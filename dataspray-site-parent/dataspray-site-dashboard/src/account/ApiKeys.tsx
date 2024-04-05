import Container from "@cloudscape-design/components/container";
import Header from "@cloudscape-design/components/header";
import {getClient} from "../util/dataSprayClientWrapper";
import {Button, SpaceBetween, Table} from "@cloudscape-design/components";
import useSWR from "swr";
import {useAuth} from "../auth/auth";
import React from "react";
import {getHeaderCounterTextSingle} from "../table/tableUtil";
import {ApiKey} from "../../../../dataspray-api-parent/dataspray-client-typescript";

export const ApiKeys = () => {
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

    return (
            <Container
                    header={
                        <Header
                                variant="h3"
                                counter={getHeaderCounterTextSingle(swr.data?.length || 0, false)}
                                actions={
                                    <SpaceBetween size="xs" direction="horizontal">
                                        <Button disabled={!selectedApiKey}>
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
                                cell: apiKey => apiKey.expiresAt,
                            },
                            {
                                id: 'queueWhitelist',
                                header: 'Limited to',
                                cell: apiKey => apiKey.queueWhitelist?.join(', '),
                            },
                        ]}
                        items={swr.data || []}
                        selectionType='single'
                        selectedItems={selectedApiKey ? [selectedApiKey] : []}
                        onSelectionChange={(e) => setSelectedApiKey(e.detail.selectedItems?.[0] as ApiKey | undefined)}
                        loading={swr.isLoading}
                />
            </Container>
    );
};

