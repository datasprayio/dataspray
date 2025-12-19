/*
 * Copyright 2025 Matus Faro
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

import {NextPageWithLayout} from "../_app";
import DashboardLayout from "../../layout/DashboardLayout";
import DashboardAppLayout from "../../layout/DashboardAppLayout";
import {
    Box,
    Button,
    Container,
    ContentLayout,
    ExpandableSection,
    FormField,
    Header,
    SpaceBetween,
    StatusIndicator,
    StatusIndicatorProps,
    Table,
    Textarea,
    ColumnLayout
} from "@cloudscape-design/components";
import {useAuth} from "../../auth/auth";
import {useCallback, useEffect, useState} from "react";
import {getClient} from "../../util/dataSprayClientWrapper";
import {useAlerts} from "../../util/useAlerts";
import {
    DatabaseSchemaResponse,
    QueryExecutionStatus,
    QueryHistoryResponse,
    QueryResultsResponse
} from "dataspray-client";
import {formatBytes, formatDuration, getStatusType, truncate} from "../../util/queryUtil";


const LakePage: NextPageWithLayout = () => {
    const {currentOrganizationName} = useAuth();
    const [sqlQuery, setSqlQuery] = useState('');
    const [queryExecutionId, setQueryExecutionId] = useState<string>();
    const [queryStatus, setQueryStatus] = useState<QueryExecutionStatus>();
    const [queryResults, setQueryResults] = useState<QueryResultsResponse>();
    const [queryHistory, setQueryHistory] = useState<QueryExecutionStatus[]>([]);
    const [isExecuting, setIsExecuting] = useState(false);
    const [schema, setSchema] = useState<DatabaseSchemaResponse>();
    const {addAlert, beginProcessing} = useAlerts();

    const loadSchema = useCallback(async () => {
        if (!currentOrganizationName) return;

        try {
            const response = await getClient().query().getDatabaseSchema({
                organizationName: currentOrganizationName
            });
            setSchema(response);
        } catch (e: any) {
            console.error('Failed to load schema:', e);
            addAlert({type: 'error', content: 'Failed to load database schema'});
        }
    }, [currentOrganizationName, addAlert]);

    const loadHistory = useCallback(async () => {
        if (!currentOrganizationName) return;

        try {
            const response = await getClient().query().getQueryHistory({
                organizationName: currentOrganizationName,
                maxResults: 50
            });
            setQueryHistory(response.queries || []);
        } catch (e: any) {
            console.error('Failed to load history:', e);
            // Silently fail for history - not critical
        }
    }, [currentOrganizationName]);

    // Load schema on mount
    useEffect(() => {
        if (currentOrganizationName) {
            loadSchema();
        }
    }, [currentOrganizationName, loadSchema]);

    // Load query history
    useEffect(() => {
        if (currentOrganizationName) {
            loadHistory();
        }
    }, [currentOrganizationName, loadHistory]);

    const loadResults = useCallback(async (qid: string, nextToken?: string) => {
        if (!currentOrganizationName) return;

        try {
            const results = await getClient().query().getQueryResults({
                organizationName: currentOrganizationName,
                queryExecutionId: qid,
                nextToken,
                maxResults: 100
            });
            setQueryResults(results);
        } catch (e: any) {
            console.error('Failed to load results:', e);
            addAlert({type: 'error', content: 'Failed to load query results'});
        }
    }, [currentOrganizationName, addAlert]);

    const checkQueryStatus = useCallback(async (qid: string) => {
        if (!currentOrganizationName) return;

        try {
            const status = await getClient().query().getQueryStatus({
                organizationName: currentOrganizationName,
                queryExecutionId: qid
            });
            setQueryStatus(status);

            if (status.state === 'SUCCEEDED') {
                await loadResults(qid);
                await loadHistory(); // Refresh history
                setIsExecuting(false);
            } else if (status.state === 'FAILED') {
                addAlert({type: 'error', content: `Query failed: ${status.errorMessage}`});
                setIsExecuting(false);
            }
        } catch (e: any) {
            console.error('Failed to check query status:', e);
            addAlert({type: 'error', content: 'Failed to check query status'});
            setIsExecuting(false);
        }
    }, [currentOrganizationName, addAlert, loadHistory, loadResults]);

    // Poll for query status if query is running
    useEffect(() => {
        if (!queryExecutionId || !queryStatus) return;
        if (queryStatus.state === 'SUCCEEDED' || queryStatus.state === 'FAILED') return;

        const interval = setInterval(() => {
            checkQueryStatus(queryExecutionId);
        }, 2000);

        return () => clearInterval(interval);
    }, [queryExecutionId, queryStatus, checkQueryStatus]);

    const executeQuery = async () => {
        if (!currentOrganizationName || !sqlQuery.trim()) return;

        setIsExecuting(true);
        setQueryResults(undefined);
        setQueryStatus(undefined);

        try {
            const response = await getClient().query().submitQuery({
                organizationName: currentOrganizationName,
                submitQueryRequest: {sqlQuery}
            });
            setQueryExecutionId(response.queryExecutionId);
            addAlert({type: 'success', content: `Query submitted: ${response.queryExecutionId}`});
            await loadHistory(); // Refresh history
        } catch (e: any) {
            console.error('Failed to submit query:', e);
            addAlert({type: 'error', content: 'Failed to submit query. Check console for details.'});
            setIsExecuting(false);
        }
    };

    const viewHistoryQuery = (query: QueryExecutionStatus) => {
        setQueryExecutionId(query.queryExecutionId);
        setSqlQuery(query.sqlQuery || '');
        checkQueryStatus(query.queryExecutionId!);
    };

    return (
        <DashboardAppLayout
            content={(
                <ContentLayout
                    header={<Header variant="h1">Data Lake Query</Header>}
                >
                    <SpaceBetween size="l">
                {/* Query Editor Section */}
                <Container
                    header={<Header variant="h2">Query Editor</Header>}
                >
                    <SpaceBetween size="m">
                        <FormField
                            label="SQL Query"
                            description="Execute SELECT queries on your data lake"
                        >
                            <Textarea
                                value={sqlQuery}
                                onChange={({detail}) => setSqlQuery(detail.value)}
                                rows={10}
                                placeholder="SELECT * FROM stream_mytopic LIMIT 10"
                            />
                        </FormField>

                        <Box float="right">
                            <Button
                                onClick={executeQuery}
                                loading={isExecuting}
                                variant="primary"
                                disabled={!sqlQuery.trim()}
                            >
                                Execute Query
                            </Button>
                        </Box>
                    </SpaceBetween>
                </Container>

                {/* Query Status Section */}
                {queryStatus && (
                    <Container
                        header={<Header variant="h2">Query Status</Header>}
                    >
                        <ColumnLayout columns={4}>
                            <div>
                                <Box variant="awsui-key-label">Status</Box>
                                <StatusIndicator type={getStatusType(queryStatus.state!)}>
                                    {queryStatus.state}
                                </StatusIndicator>
                            </div>
                            <div>
                                <Box variant="awsui-key-label">Execution ID</Box>
                                <Box>{queryExecutionId}</Box>
                            </div>
                            <div>
                                <Box variant="awsui-key-label">Bytes Scanned</Box>
                                <Box>{formatBytes(queryStatus.bytesScanned)}</Box>
                            </div>
                            <div>
                                <Box variant="awsui-key-label">Execution Time</Box>
                                <Box>{formatDuration(queryStatus.executionTimeMs)}</Box>
                            </div>
                        </ColumnLayout>
                    </Container>
                )}

                {/* Results Section */}
                {queryResults && (
                    <Container
                        header={
                            <Header
                                variant="h2"
                                counter={`(${queryResults.rows?.length || 0} rows)`}
                            >
                                Query Results
                            </Header>
                        }
                    >
                        <SpaceBetween size="m">
                            <Table
                                columnDefinitions={
                                    (queryResults.columns || []).map((col, idx) => ({
                                        id: col.name!,
                                        header: col.name!,
                                        cell: (item: string[]) => item[idx] || 'NULL'
                                    }))
                                }
                                items={queryResults.rows || []}
                                loadingText="Loading results..."
                                empty={
                                    <Box textAlign="center">
                                        <Box variant="strong">No results</Box>
                                    </Box>
                                }
                            />

                            {queryResults.nextToken && (
                                <Box textAlign="center">
                                    <Button
                                        onClick={() => loadResults(queryExecutionId!, queryResults.nextToken)}
                                    >
                                        Load More Results
                                    </Button>
                                </Box>
                            )}
                        </SpaceBetween>
                    </Container>
                )}

                {/* Query History Section */}
                <Container
                    header={<Header variant="h2">Query History</Header>}
                >
                    <Table
                        columnDefinitions={[
                            {
                                id: 'queryExecutionId',
                                header: 'Query ID',
                                cell: (item: QueryExecutionStatus) => item.queryExecutionId,
                                width: 200
                            },
                            {
                                id: 'sqlQuery',
                                header: 'Query',
                                cell: (item: QueryExecutionStatus) => (
                                    <Box variant="code">{truncate(item.sqlQuery || '', 80)}</Box>
                                )
                            },
                            {
                                id: 'state',
                                header: 'Status',
                                cell: (item: QueryExecutionStatus) => (
                                    <StatusIndicator type={getStatusType(item.state!)}>
                                        {item.state}
                                    </StatusIndicator>
                                ),
                                width: 120
                            },
                            {
                                id: 'bytesScanned',
                                header: 'Data Scanned',
                                cell: (item: QueryExecutionStatus) =>
                                    formatBytes(item.bytesScanned),
                                width: 120
                            },
                            {
                                id: 'actions',
                                header: 'Actions',
                                cell: (item: QueryExecutionStatus) => (
                                    <Button
                                        iconName="search"
                                        variant="inline-link"
                                        onClick={() => viewHistoryQuery(item)}
                                    >
                                        View
                                    </Button>
                                ),
                                width: 100
                            }
                        ]}
                        items={queryHistory}
                        loadingText="Loading history..."
                        empty={
                            <Box textAlign="center">
                                <Box variant="strong">No query history</Box>
                            </Box>
                        }
                    />
                </Container>

                {/* Schema Browser Section */}
                {schema && (
                    <Container
                        header={
                            <Header
                                variant="h2"
                                description="Available tables and columns in your data lake"
                            >
                                Schema Browser
                            </Header>
                        }
                    >
                        <ExpandableSection
                            headerText={`Database: ${schema.databaseName}`}
                            defaultExpanded
                        >
                            <SpaceBetween size="m">
                                {(schema.tables || []).map(table => (
                                    <ExpandableSection
                                        key={table.name}
                                        headerText={`Table: ${table.name}`}
                                    >
                                        <Table
                                            columnDefinitions={[
                                                {
                                                    id: 'name',
                                                    header: 'Column Name',
                                                    cell: (item: any) => (
                                                        <Box variant="code">{item.name}</Box>
                                                    )
                                                },
                                                {
                                                    id: 'type',
                                                    header: 'Data Type',
                                                    cell: (item: any) => item.type
                                                }
                                            ]}
                                            items={table.columns || []}
                                            variant="embedded"
                                        />
                                    </ExpandableSection>
                                ))}
                            </SpaceBetween>
                        </ExpandableSection>
                    </Container>
                )}
                    </SpaceBetween>
                </ContentLayout>
            )}
        />
    );
};

LakePage.getLayout = (page) => (
    <DashboardLayout
        pageTitle='Lake'
    >{page}</DashboardLayout>
);

export default LakePage;
