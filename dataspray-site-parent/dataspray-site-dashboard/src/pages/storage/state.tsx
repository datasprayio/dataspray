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
import {
    Box,
    Button,
    ColumnLayout,
    Container,
    ContentLayout,
    FormField,
    Header,
    Input,
    Select,
    SpaceBetween,
    SplitPanel,
    Table,
    Tabs,
    Textarea
} from "@cloudscape-design/components";
import DashboardAppLayout from "../../layout/DashboardAppLayout";
import {useAuth} from "../../auth/auth";
import {useCallback, useEffect, useState} from "react";
import {getClient} from "../../util/dataSprayClientWrapper";
import {useAlerts} from "../../util/useAlerts";
import useTaskStore from "../../deployment/taskStore";
import {StateEntry} from "dataspray-client";

const StatePage: NextPageWithLayout = () => {
    const {currentOrganizationName} = useAuth();
    const {tasks} = useTaskStore(currentOrganizationName);
    const {addAlert, beginProcessing} = useAlerts();

    // Key builder state
    const [selectedTaskId, setSelectedTaskId] = useState<string>();
    const [keyParts, setKeyParts] = useState<string[]>([]);
    const [customKeyParts, setCustomKeyParts] = useState<string>("");

    // State entries
    const [stateEntries, setStateEntries] = useState<StateEntry[]>([]);
    const [selectedEntry, setSelectedEntry] = useState<StateEntry>();
    const [isLoading, setIsLoading] = useState(false);

    // Split panel
    const [splitPanelOpen, setSplitPanelOpen] = useState(false);
    const [activeTab, setActiveTab] = useState("view");

    // Edit state
    const [editAttributes, setEditAttributes] = useState<string>("");
    const [editTtl, setEditTtl] = useState<string>("");

    // Load state entries based on current key filter
    const loadStateEntries = useCallback(async () => {
        if (!currentOrganizationName) return;

        setIsLoading(true);
        try {
            const response = await getClient().control().listState({
                organizationName: currentOrganizationName,
                stateListRequest: {
                    keyPrefix: keyParts.length > 0 ? keyParts : undefined,
                    limit: 100
                }
            });
            setStateEntries(response.entries || []);
        } catch (e: any) {
            addAlert({
                type: 'error',
                content: `Failed to load state: ${e?.message || 'Unknown error'}`
            });
        } finally {
            setIsLoading(false);
        }
    }, [currentOrganizationName, keyParts, addAlert]);

    // Load when key changes
    useEffect(() => {
        loadStateEntries();
    }, [loadStateEntries]);

    // Build key from task selection
    const handleTaskSelect = (taskId: string | undefined) => {
        setSelectedTaskId(taskId);
        if (taskId) {
            setKeyParts(["task", taskId]);
            setCustomKeyParts(`task:${taskId}`);
        } else {
            setKeyParts([]);
            setCustomKeyParts("");
        }
    };

    // Parse custom key input
    const handleCustomKeyChange = (value: string) => {
        setCustomKeyParts(value);
        const parts = value.split(':').map(p => p.trim()).filter(p => p);
        setKeyParts(parts);
        setSelectedTaskId(undefined);
    };

    // Select entry for viewing/editing
    const handleEntrySelect = (entry: StateEntry) => {
        setSelectedEntry(entry);
        setEditAttributes(JSON.stringify(entry.attributes, null, 2));
        setEditTtl(entry.ttlInEpochSec?.toString() || "");
        setSplitPanelOpen(true);
        setActiveTab("view");
    };

    // Update state entry
    const handleUpdate = async () => {
        if (!currentOrganizationName || !selectedEntry) return;

        const {onSuccess, onError} = beginProcessing({
            content: `Updating state ${selectedEntry.mergedKey}`
        });

        try {
            const attributes = JSON.parse(editAttributes);
            const ttlInSec = editTtl ? parseInt(editTtl) : undefined;

            await getClient().control().upsertState({
                organizationName: currentOrganizationName,
                stateUpsertRequest: {
                    keyParts: selectedEntry.keyParts,
                    attributes,
                    ttlInSec
                }
            });

            onSuccess({content: "State updated successfully"});
            loadStateEntries();
        } catch (e: any) {
            onError({
                content: `Failed to update state: ${e?.message || 'Unknown error'}`
            });
        }
    };

    // Delete state entry
    const handleDelete = async () => {
        if (!currentOrganizationName || !selectedEntry) return;

        const {onSuccess, onError} = beginProcessing({
            content: `Deleting state ${selectedEntry.mergedKey}`
        });

        try {
            await getClient().control().deleteState({
                organizationName: currentOrganizationName,
                stateDeleteRequest: {
                    keyParts: selectedEntry.keyParts
                }
            });

            onSuccess({content: "State deleted successfully"});
            setSelectedEntry(undefined);
            setSplitPanelOpen(false);
            loadStateEntries();
        } catch (e: any) {
            onError({
                content: `Failed to delete state: ${e?.message || 'Unknown error'}`
            });
        }
    };

    const splitPanel = selectedEntry ? (
        <SplitPanel header={`State: ${selectedEntry.mergedKey}`}>
            <Tabs
                activeTabId={activeTab}
                onChange={({detail}) => setActiveTab(detail.activeTabId)}
                tabs={[
                    {
                        id: "view",
                        label: "View",
                        content: (
                            <Container>
                                <SpaceBetween size="m">
                                    <ColumnLayout columns={2}>
                                        <div>
                                            <Box variant="awsui-key-label">Key Parts</Box>
                                            <Box>{selectedEntry.keyParts.join(' → ')}</Box>
                                        </div>
                                        <div>
                                            <Box variant="awsui-key-label">Merged Key</Box>
                                            <Box variant="code">{selectedEntry.mergedKey}</Box>
                                        </div>
                                    </ColumnLayout>

                                    {selectedEntry.ttlInEpochSec && (
                                        <div>
                                            <Box variant="awsui-key-label">TTL</Box>
                                            <Box>{new Date(selectedEntry.ttlInEpochSec * 1000).toLocaleString()}</Box>
                                        </div>
                                    )}

                                    <FormField label="Attributes">
                                        <pre style={{
                                            background: '#f4f4f4',
                                            padding: '12px',
                                            borderRadius: '4px',
                                            overflow: 'auto'
                                        }}>
                                            {JSON.stringify(selectedEntry.attributes, null, 2)}
                                        </pre>
                                    </FormField>
                                </SpaceBetween>
                            </Container>
                        )
                    },
                    {
                        id: "edit",
                        label: "Edit",
                        content: (
                            <Container>
                                <SpaceBetween size="m">
                                    <FormField
                                        label="Attributes (JSON)"
                                        description="Edit the state attributes as JSON"
                                    >
                                        <Textarea
                                            value={editAttributes}
                                            onChange={({detail}) => setEditAttributes(detail.value)}
                                            rows={15}
                                        />
                                    </FormField>

                                    <FormField
                                        label="TTL (seconds from now)"
                                        description="Optional: Set time-to-live in seconds"
                                    >
                                        <Input
                                            value={editTtl}
                                            onChange={({detail}) => setEditTtl(detail.value)}
                                            type="number"
                                        />
                                    </FormField>

                                    <Box float="right">
                                        <SpaceBetween direction="horizontal" size="xs">
                                            <Button onClick={handleDelete} variant="link">
                                                Delete
                                            </Button>
                                            <Button onClick={handleUpdate} variant="primary">
                                                Save Changes
                                            </Button>
                                        </SpaceBetween>
                                    </Box>
                                </SpaceBetween>
                            </Container>
                        )
                    }
                ]}
            />
        </SplitPanel>
    ) : (
        <SplitPanel header="State Entry">
            No state entry selected.
        </SplitPanel>
    );

    return (
        <DashboardAppLayout
            content={(
                <ContentLayout
                    header={<Header variant="h1">State Browser</Header>}
                >
                    <SpaceBetween size="l">
                        {/* Key Builder */}
                        <Container
                            header={
                                <Header
                                    variant="h2"
                                    description="Build a composite key to filter state entries"
                                >
                                    Key Builder
                                </Header>
                            }
                        >
                            <SpaceBetween size="m">
                                <FormField label="Select Task (Optional)">
                                    <Select
                                        selectedOption={
                                            selectedTaskId
                                                ? {label: selectedTaskId, value: selectedTaskId}
                                                : null
                                        }
                                        onChange={({detail}) => {
                                            handleTaskSelect(detail.selectedOption.value);
                                        }}
                                        options={tasks.map(t => ({
                                            label: t.taskId,
                                            value: t.taskId
                                        }))}
                                        placeholder="Select a task"
                                    />
                                </FormField>

                                <FormField
                                    label="Or Enter Custom Key"
                                    description="Use colon-separated values (e.g., 'task:myProcessor:someKey')"
                                >
                                    <Input
                                        value={customKeyParts}
                                        onChange={({detail}) => handleCustomKeyChange(detail.value)}
                                        placeholder="task:processorId:key"
                                    />
                                </FormField>

                                <Box>
                                    <Box variant="awsui-key-label">Current Filter</Box>
                                    <Box variant="code">
                                        {keyParts.length > 0
                                            ? keyParts.join(' : ')
                                            : '(none - showing all state)'}
                                    </Box>
                                </Box>

                                <Button onClick={loadStateEntries} iconName="refresh">
                                    Refresh
                                </Button>
                            </SpaceBetween>
                        </Container>

                        {/* State Entries Table */}
                        <Container>
                            <Table
                                header={
                                    <Header
                                        variant="h2"
                                        counter={`(${stateEntries.length})`}
                                    >
                                        State Entries
                                    </Header>
                                }
                                columnDefinitions={[
                                    {
                                        id: 'mergedKey',
                                        header: 'Key',
                                        cell: (item: StateEntry) => (
                                            <Box variant="code">{item.mergedKey}</Box>
                                        ),
                                        isRowHeader: true
                                    },
                                    {
                                        id: 'keyParts',
                                        header: 'Key Parts',
                                        cell: (item: StateEntry) => (
                                            item.keyParts.join(' → ')
                                        )
                                    },
                                    {
                                        id: 'ttl',
                                        header: 'TTL',
                                        cell: (item: StateEntry) => (
                                            item.ttlInEpochSec
                                                ? new Date(item.ttlInEpochSec * 1000).toLocaleDateString()
                                                : '-'
                                        ),
                                        width: 150
                                    },
                                    {
                                        id: 'preview',
                                        header: 'Attributes Preview',
                                        cell: (item: StateEntry) => {
                                            const preview = JSON.stringify(item.attributes);
                                            return preview.length > 50
                                                ? preview.substring(0, 50) + '...'
                                                : preview;
                                        }
                                    }
                                ]}
                                items={stateEntries}
                                loading={isLoading}
                                loadingText="Loading state entries..."
                                selectionType="single"
                                trackBy={(item) => item.mergedKey}
                                selectedItems={selectedEntry ? [selectedEntry] : []}
                                onSelectionChange={({detail}) => {
                                    const entry = detail.selectedItems[0];
                                    if (entry) handleEntrySelect(entry);
                                }}
                                empty={
                                    <Box textAlign="center" padding="xxl">
                                        <SpaceBetween size="m">
                                            <Box variant="strong">No state entries</Box>
                                            <Box variant="p" color="text-body-secondary">
                                                No state entries found for this key filter.
                                            </Box>
                                        </SpaceBetween>
                                    </Box>
                                }
                            />
                        </Container>
                    </SpaceBetween>
                </ContentLayout>
            )}
            splitPanel={splitPanel}
            splitPanelOpen={splitPanelOpen}
            onSplitPanelToggle={(e) => setSplitPanelOpen(e.detail.open)}
        />
    );
};

StatePage.getLayout = (page) => (
    <DashboardLayout pageTitle='State'>{page}</DashboardLayout>
);

export default StatePage;
