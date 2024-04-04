import Container from "@cloudscape-design/components/container";
import Header from "@cloudscape-design/components/header";
import {getClient} from "../util/dataSprayClientWrapper";
import {Table} from "@cloudscape-design/components";
import useSWR from "swr";
import {useAuth} from "../auth/auth";

export const ApiKeys = () => {
    const {currentOrganizationName} = useAuth();

    const swr = useSWR('', !currentOrganizationName ? null : () => getClient().authNz().listApiKeys({
        organizationName: currentOrganizationName,
    }).then(apiKeys => apiKeys.keys));

    // TODO create
    // TODO revoke
    return (
            <Container
                    header={
                        <Header variant="h3">
                            API Keys
                        </Header>
                    }
            >
                <Table
                        variant='container'
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
                        selectionType="single"
                        loading={swr.isLoading}
                />
            </Container>
    );
};

