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

import {NextPageWithLayout} from "../_app";
import DashboardLayout from "../../layout/DashboardLayout";
import {ContentLayout, SpaceBetween, SplitPanel, StatusIndicator, Table} from "@cloudscape-design/components";
import DashboardAppLayout from "../../layout/DashboardAppLayout";
import {useAuth} from "../../auth/auth";
import {getHeaderCounterTextSingle} from "../../table/tableUtil";
import {useCallback, useState} from "react";
import {getClient} from "../../util/dataSprayClientWrapper";
import useTopicStore from "../../deployment/topicStore";
import {DeleteTopicModal} from "../../deployment/DeleteTopicModal";
import {TopicActionHeader} from "../../deployment/TopicActionHeader";
import {EditTopic, EditType} from "../../deployment/EditTopic";
import {useAlerts} from "../../util/useAlerts";
import {S3FileBrowser} from "../../deployment/S3FileBrowser";

const Yes = <StatusIndicator type='success'>Yes</StatusIndicator>;
const No = <StatusIndicator type='stopped'>No</StatusIndicator>;

const Page: NextPageWithLayout = () => {
    const {currentOrganizationName} = useAuth();
    const {
        data,
        isLoading,
        update,
    } = useTopicStore(currentOrganizationName);
    const [selectedTopicName, setSelectedTopicName] = useState<string>();
    const selectedTopic = selectedTopicName ? data?.topics[selectedTopicName] : undefined;

    const [showConfirmDeleteTopicName, setShowConfirmDeleteTopicName] = useState<string>();
    const onDeleteClick = useCallback(() => {
        selectedTopicName && setShowConfirmDeleteTopicName(selectedTopicName);
    }, [selectedTopicName]);
    const onDeleteCancel = useCallback(() => {
        setShowConfirmDeleteTopicName(undefined);
    }, []);
    const {beginProcessing, addAlert} = useAlerts();
    const onDeleteConfirmedClick = useCallback(async () => {
        if (!currentOrganizationName || !selectedTopicName) {
            return;
        }
        const {onSuccess, onError} = beginProcessing({content: `Deleting topic ${selectedTopicName}`});
        try {
            const updatedTopics = await getClient().control().deleteTopic({
                topicName: selectedTopicName,
                organizationName: currentOrganizationName,
            });
            update(updatedTopics);
            onSuccess({content: `Topic ${selectedTopicName} deleted successfully`});
            setShowConfirmDeleteTopicName(undefined);
        } catch (e: any) {
            onError({content: `Failed to delete topic ${selectedTopicName}: ${e?.message || 'Unknown error'}`});
        }
    }, [beginProcessing, currentOrganizationName, selectedTopicName, update]);

    const [isRecalculatingSchema, setIsRecalculatingSchema] = useState(false);
    const onRecalculateSchemaClick = useCallback(async () => {
        if (!currentOrganizationName || !selectedTopicName) {
            return;
        }

        // Check if topic has batch enabled
        if (!selectedTopic?.batch) {
            addAlert({
                type: 'error',
                content: `Topic ${selectedTopicName} does not have batch enabled`
            });
            return;
        }

        setIsRecalculatingSchema(true);
        try {
            const schema = await getClient().control().recalculateTopicSchema({
                topicName: selectedTopicName,
                organizationName: currentOrganizationName,
            });
            addAlert({
                type: 'success',
                content: `Schema recalculated successfully for topic ${selectedTopicName}`
            });
            console.log('Recalculated schema:', schema);
        } catch (e: any) {
            addAlert({
                type: 'error',
                content: `Failed to recalculate schema: ${e?.message || 'Unknown error'}`
            });
        } finally {
            setIsRecalculatingSchema(false);
        }
    }, [addAlert, currentOrganizationName, selectedTopicName, selectedTopic]);

    const [splitPanelOpen, setSplitPanelOpen] = useState<boolean>(false);
    const [editType, setEditType] = useState<EditType>(EditType.EDIT_TOPIC);
    const onCreateClick = useCallback(() => {
        setEditType(EditType.CREATE_TOPIC);
        setSelectedTopicName(undefined);
        setSplitPanelOpen(true);
    }, []);
    const onEditDefaultClick = useCallback(() => {
        setEditType(EditType.EDIT_DEFAULT_TOPIC);
        setSelectedTopicName(undefined);
        setSplitPanelOpen(true);
    }, []);
    var splitPanelDisabled = false;
    var splitPanelHeader: string = '';
    switch(editType) {
        case EditType.EDIT_TOPIC:
            if(selectedTopicName) {
            splitPanelHeader = `Edit topic ${selectedTopicName}`;
            } else {
                splitPanelHeader = `Select a topic`;
                splitPanelDisabled = true
            }
            break;
        case EditType.EDIT_DEFAULT_TOPIC:
            splitPanelHeader = 'Edit default topic';
            break;
        case EditType.CREATE_TOPIC:
            splitPanelHeader = 'Create new topic';
            break;
    }
    const splitPanel = (
        <SplitPanel header={splitPanelHeader}>
            {!!splitPanelDisabled && 'No topic selected.'}
            {!splitPanelDisabled && (
                <EditTopic
                    key={splitPanelHeader}
                    editType={editType}
                    topicName={selectedTopicName}
                    topic={editType === EditType.EDIT_DEFAULT_TOPIC
                        ? data?.undefinedTopic
                        : selectedTopic}
                    allowUndefinedTopics={data?.allowUndefinedTopics}
                    onUpdated={update}
                />
            )}
        </SplitPanel>
    );

    return (
        <>
            <DeleteTopicModal
                topicName={showConfirmDeleteTopicName}
                show={!!showConfirmDeleteTopicName}
                onDelete={onDeleteConfirmedClick}
                onHide={onDeleteCancel}
            />
            <DashboardAppLayout
                content={(
                    <ContentLayout disableOverlap>
                        <SpaceBetween size="l">
                            <Table
                                header={(
                                    <TopicActionHeader
                                        counter={getHeaderCounterTextSingle(Object.keys(data?.topics || {}).length, false)}
                                        onCreateClick={editType !== EditType.CREATE_TOPIC ? onCreateClick : undefined}
                                        onEditDefaultClick={editType !== EditType.EDIT_DEFAULT_TOPIC ? onEditDefaultClick : undefined}
                                        onRecalculateSchemaClick={selectedTopic?.batch ? onRecalculateSchemaClick : undefined}
                                        recalculateSchemaLoading={isRecalculatingSchema}
                                        onDeleteClick={selectedTopic ? onDeleteClick : undefined}
                                    />
                                )}
                                variant='container'
                                stickyHeader
                                columnDefinitions={[
                            {
                                id: 'name',
                                header: 'Name',
                                cell: topic => topic.name,
                            },
                            {
                                id: 'stream',
                                header: 'Streams',
                                cell: topic => topic.streams?.length ? Yes : No,
                            },
                            {
                                id: 'batch',
                                header: 'Batch',
                                cell: topic => topic.batch ? Yes : No,
                            },
                            {
                                id: 'store',
                                header: 'Store',
                                cell: topic => topic.store ? Yes : No,
                            },
                        ]}
                        items={Array.from(
                            Object.entries(data?.topics || {}),
                            ([name, value]) => ({name, ...value})
                        )}
                        selectionType="single"
                        trackBy={topic => topic?.name}
                        selectedItems={(selectedTopic && selectedTopicName) ? [{name: selectedTopicName, ...selectedTopic}] : []}
                        onSelectionChange={event => {
                            setSelectedTopicName(event.detail.selectedItems?.[0]?.name);
                            setEditType(EditType.EDIT_TOPIC);
                        }}
                        loading={isLoading}
                    />

                    {selectedTopicName && selectedTopic?.batch && currentOrganizationName && (
                        <S3FileBrowser
                            organizationName={currentOrganizationName}
                            topicName={selectedTopicName}
                        />
                    )}
                        </SpaceBetween>
                    </ContentLayout>
                )}
                splitPanel={splitPanel}
                splitPanelOpen={splitPanelOpen}
                onSplitPanelToggle={e => setSplitPanelOpen(e.detail.open)}
            />
        </>
    )
}

Page.getLayout = (page) => (
    <DashboardLayout
        pageTitle='Tasks'
    >{page}</DashboardLayout>
)

export default Page
