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
import {FullPageHeader} from "../../table/FullPageHeader";
import {getHeaderCounterTextSingle} from "../../table/tableUtil";
import Link from "@cloudscape-design/components/link";
import {useState} from "react";

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
    const [selectedTaskId, setSelectedTaskIds] = useState<string>();
    const selectedTask = selectedTaskId ? tasks.find(task => task.taskId === selectedTaskId) : undefined;

    var splitPanel: React.ReactNode;
    if (!selectedTask) {
        splitPanel = (
                <SplitPanel header='No task selected'>
                    Select a task to see its details.
                </SplitPanel>
        );
    } else {
        splitPanel = (
                <SplitPanel header={selectedTask.taskId}>
                    taskId: {selectedTask.taskId}
                    Status: {selectedTask.status}
                    TODO
                </SplitPanel>
        );
    }

    return (
            <DashboardAppLayout
                    content={(
                            <Table
                                    header={
                                        <FullPageHeader
                                                title="Instances"
                                                createButtonText="Create instance"
                                                selectedItemsCount={selectedTaskId ? 1 : 0}
                                                counter={getHeaderCounterTextSingle(tasks.length, hasMore)}
                                        />
                                    }
                                    variant='full-page'
                                    stickyHeader
                                    columnDefinitions={[
                                        {
                                            id: 'id',
                                            header: 'Task ID',
                                            cell: task => <Link href="#">{task.taskId}</Link>,
                                            isRowHeader: true,
                                        },
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
                                    ]}
                                    items={pageTasks}
                                    selectionType="single"
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
            />
    )
}

Page.getLayout = (page) => (
        <DashboardLayout
                pageTitle='Tasks'
        >{page}</DashboardLayout>
)

export default Page
