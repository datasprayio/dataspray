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
import {SplitPanel, StatusIndicator, Table} from "@cloudscape-design/components";
import DashboardAppLayout from "../../layout/DashboardAppLayout";
import {useAuth} from "../../auth/auth";
import {getHeaderCounterTextSingle} from "../../table/tableUtil";
import {useCallback, useState} from "react";
import {getClient} from "../../util/dataSprayClientWrapper";
import {useAlerts} from "../../util/useAlerts";
import useTopicStore from "../../deployment/topicStore";
import {DeleteTopicModal} from "../../deployment/DeleteTopicModal";
import {TopicActionHeader} from "../../deployment/TopicActionHeader";
import {EditTopic} from "../../deployment/EditTopic";

const Yes = <StatusIndicator type='success'>Yes</StatusIndicator>;
const No = <StatusIndicator type='loading'>No</StatusIndicator>;

const Page: NextPageWithLayout = () => {
    const {currentOrganizationName} = useAuth();
    const {
        data,
        isLoading,
    } = useTopicStore(currentOrganizationName);
    const [selectedTopicName, setSelectedTopicName] = useState<string>();
    const selectedTopic = selectedTopicName ? data?.topics[selectedTopicName] : undefined;
    const {beginProcessing} = useAlerts();

    // const onUpdateClick = useCallback(async () => {
    //     if (!currentOrganizationName || !selectedTaskId) {
    //         return;
    //     }
    //     const {onSuccess, onError} = beginProcessing({content: `Resuming task ${selectedTaskId}`});
    //     try {
    //         await getClient().control().resume({
    //             taskId: selectedTaskId,
    //             organizationName: currentOrganizationName,
    //         });
    //         onSuccess({content: `Task ${selectedTaskId} resumed successfully`});
    //     } catch (e: any) {
    //         onError({content: `Failed to resume task ${selectedTaskId}: ${e?.message || 'Unknown error'}`});
    //     }
    // }, [beginProcessing, currentOrganizationName, selectedTaskId]);
    const [showConfirmDeleteTopicName, setShowConfirmDeleteTopicName] = useState<string>();
    const onDeleteClick = useCallback(() => {
        selectedTopicName && setShowConfirmDeleteTopicName(selectedTopicName);
    }, [selectedTopicName]);
    const onDeleteCancel = useCallback(() => {
        setShowConfirmDeleteTopicName(undefined);
    }, []);
    const onDeleteConfirmedClick = useCallback(async () => {
        if (!currentOrganizationName || !selectedTopicName) {
            return;
        }
        const {onSuccess, onError} = beginProcessing({content: `Deleting topic ${selectedTopicName}`});
        try {
            await getClient().control().deleteTopic({
                topicName: selectedTopicName,
                organizationName: currentOrganizationName,
            });
            onSuccess({content: `Topic ${selectedTopicName} deleted successfully`});
            setShowConfirmDeleteTopicName(undefined);
        } catch (e: any) {
            onError({content: `Failed to delete topic ${selectedTopicName}: ${e?.message || 'Unknown error'}`});
        }
    }, [beginProcessing, currentOrganizationName, selectedTopicName]);

    var splitPanel: React.ReactNode;
    if (!!selectedTopicName && !!selectedTopic) {
        splitPanel = (
            <SplitPanel header={`Edit topic ${selectedTopicName}`}>
                <EditTopic key={`edit-${selectedTopicName}`} topicName={selectedTopicName} topic={selectedTopic}/>
            </SplitPanel>
        );
    } else {
        splitPanel = (
            <SplitPanel header='Create a topic'>
                <EditTopic key='create' />
            </SplitPanel>
        );
    }

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
                    <Table
                        header={(
                            <TopicActionHeader
                                counter={getHeaderCounterTextSingle(Object.keys(data?.topics || {}).length, false)}
                                onDeleteClick={selectedTopic ? onDeleteClick : undefined}
                            />
                        )}
                        variant='full-page'
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
                        onSelectionChange={event => setSelectedTopicName(event.detail.selectedItems?.[0]?.name)}
                        loading={isLoading}
                    />
                )}
                splitPanel={splitPanel}
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
