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
import {Pagination, SplitPanel, StatusIndicator, Table} from "@cloudscape-design/components";
import DashboardAppLayout from "../../layout/DashboardAppLayout";
import {useAuth} from "../../auth/auth";
import useTaskStore from "../../deployment/taskStore";
import {TaskActionHeader} from "../../deployment/TaskActionHeader";
import {getHeaderCounterTextSingle} from "../../table/tableUtil";
import {useCallback, useState} from "react";
import {getClient} from "../../util/dataSprayClientWrapper";
import {DeleteTaskModal} from "../../deployment/DeleteTaskModal";
import {useAlerts} from "../../util/useAlerts";
import {ViewTask} from "../../deployment/ViewTask";

const Page: NextPageWithLayout = () => {
    const {currentOrganizationName} = useAuth();
    const {
        tasks,
        hasMore,
        pageIndex,
        pageCount,
        setPageIndex,
        pageTasks,
        isLoading,
    } = useTaskStore(currentOrganizationName);
    const [selectedTaskId, setSelectedTaskId] = useState<string>();
    const selectedTask = selectedTaskId ? tasks.find(task => task.taskId === selectedTaskId) : undefined;
    const [splitPanelOpen, setSplitPanelOpen] = useState<boolean>(false);
    const {beginProcessing} = useAlerts();

    const onPauseClick = useCallback(async () => {
        if (!currentOrganizationName || !selectedTaskId) {
            return;
        }
        const {onSuccess, onError} = beginProcessing({content: `Pausing task ${selectedTaskId}`});
        try {
            await getClient().control().pause({
                taskId: selectedTaskId,
                organizationName: currentOrganizationName,
            });
            onSuccess({content: `Task ${selectedTaskId} paused successfully`});
        } catch (e: any) {
            onError({content: `Failed to pause task ${selectedTaskId}: ${e?.message || 'Unknown error'}`});
        }
    }, [beginProcessing, currentOrganizationName, selectedTaskId]);
    const onResumeClick = useCallback(async () => {
        if (!currentOrganizationName || !selectedTaskId) {
            return;
        }
        const {onSuccess, onError} = beginProcessing({content: `Resuming task ${selectedTaskId}`});
        try {
            await getClient().control().resume({
                taskId: selectedTaskId,
                organizationName: currentOrganizationName,
            });
            onSuccess({content: `Task ${selectedTaskId} resumed successfully`});
        } catch (e: any) {
            onError({content: `Failed to resume task ${selectedTaskId}: ${e?.message || 'Unknown error'}`});
        }
    }, [beginProcessing, currentOrganizationName, selectedTaskId]);
    const [showConfirmDeleteTaskId, setShowConfirmDeleteTaskId] = useState<string>();
    const onDeleteClick = useCallback(() => {
        selectedTaskId && setShowConfirmDeleteTaskId(selectedTaskId);
    }, [selectedTaskId]);
    const onDeleteCancel = useCallback(() => {
        setShowConfirmDeleteTaskId(undefined);
    }, []);
    const onDeleteConfirmedClick = useCallback(async () => {
        if (!currentOrganizationName || !selectedTaskId) {
            return;
        }
        const {onSuccess, onError} = beginProcessing({content: `Deleting task ${selectedTaskId}`});
        try {
            await getClient().control()._delete({
                taskId: selectedTaskId,
                organizationName: currentOrganizationName,
            });
            onSuccess({content: `Task ${selectedTaskId} deleted successfully`});
            setShowConfirmDeleteTaskId(undefined);
        } catch (e: any) {
            onError({content: `Failed to delete task ${selectedTaskId}: ${e?.message || 'Unknown error'}`});
        }
    }, [beginProcessing, currentOrganizationName, selectedTaskId]);

    const splitPanel = selectedTask ? (
        <SplitPanel header={`Task ${selectedTask.taskId}`}>
            <ViewTask task={selectedTask}/>
        </SplitPanel>
    ) : (
        <SplitPanel header='Task'>
            No task selected.
        </SplitPanel>
    );

    return (
        <>
            <DeleteTaskModal
                taskId={showConfirmDeleteTaskId}
                show={!!showConfirmDeleteTaskId}
                onDelete={onDeleteConfirmedClick}
                onHide={onDeleteCancel}
            />
            <DashboardAppLayout
                content={(
                    <Table
                        header={(
                            <TaskActionHeader
                                counter={getHeaderCounterTextSingle(tasks.length, hasMore)}
                                onPauseClick={selectedTask?.status === 'RUNNING' ? onPauseClick : undefined}
                                onResumeClick={selectedTask?.status === 'PAUSED' ? onResumeClick : undefined}
                                onDeleteClick={!!selectedTask && selectedTask?.status !== 'NOTFOUND' ? onDeleteClick : undefined}
                            />
                        )}
                        variant='full-page'
                        stickyHeader
                        columnDefinitions={[
                            {
                                id: 'status',
                                header: 'Task Status',
                                cell: task => (
                                    <>
                                        <StatusIndicator
                                            type={task.status === 'RUNNING' ? 'success' : 'error'}> {task.status} </StatusIndicator>
                                    </>
                                ),
                            },
                            {
                                id: 'id',
                                header: 'Task ID',
                                cell: task => task.taskId,
                                isRowHeader: true,
                            },
                            {
                                id: 'lastUpdate',
                                header: 'LastUpdate',
                                cell: task => (
                                    <>
                                        <StatusIndicator
                                            type={task.lastUpdateStatus === 'SUCCESSFUL' ? 'info' : 'warning'}> {task.lastUpdateStatus} {task.lastUpdateStatusReason}</StatusIndicator>
                                    </>
                                ),
                            },
                        ]}
                        items={pageTasks}
                        selectionType="single"
                        trackBy={task => task.taskId}
                        selectedItems={selectedTask ? [selectedTask] : []}
                        onSelectionChange={event => {
                            setSelectedTaskId(event.detail.selectedItems?.[0]?.taskId);
                            if (event.detail.selectedItems?.[0]?.taskId) {
                                setSplitPanelOpen(true);
                            }
                        }}
                        loading={isLoading}
                        pagination={(
                            <Pagination
                                currentPageIndex={pageIndex + 1}
                                pagesCount={pageCount}
                                openEnd={hasMore}
                                onPreviousPageClick={() => setPageIndex(pageIndex - 1)}
                                onNextPageClick={() => setPageIndex(pageIndex + 1)}
                                onChange={e => setPageIndex(e.detail.currentPageIndex - 1)}
                                disabled={isLoading}
                            />
                        )}
                    />
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
