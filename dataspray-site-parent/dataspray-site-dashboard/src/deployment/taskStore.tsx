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

import {getClient} from '../util/dataSprayClientWrapper';
import useSWRInfinite, {SWRInfiniteResponse} from 'swr/infinite';
import {TaskStatus, TaskStatuses} from 'dataspray-client';
import {useState} from "react";

export default function useTaskStore(organizationName?: string | null): SWRInfiniteResponse<TaskStatuses, any> & {
    hasMore: boolean,
    tasks: TaskStatus[],
    pageIndex: number,
    pageCount: number,
    setPageIndex: (index: number) => void,
    pageTasks: TaskStatus[],
} {

    const getKey = (pageIndex: number, previousPageData: any): null | ['taskStatuses', string | null | undefined, string | undefined] => {
        // reached the end
        if (!!previousPageData && !previousPageData.cursor) return null
        // add the cursor to the API endpoint
        return ['taskStatuses', organizationName, previousPageData?.cursor]
    }
    const fetcher = !organizationName ? null : async ([taskStatuses, key, cursor]: [string, string, string | undefined]): Promise<TaskStatuses> => {
        const page = await getClient().control().statusAll({
            organizationName,
            cursor,
        });
        return page;
    }
    const swrInifinite = useSWRInfinite(getKey, fetcher);

    const hasMore = !!(swrInifinite.data?.[swrInifinite.data.length - 1] as any)?.cursor;
    const tasks = swrInifinite.data?.map(d => d?.tasks).flat() || [];

    const [pageIndex, setPageIndex] = useState<number>(0);
    const pageTasks = swrInifinite.data?.[pageIndex]?.tasks || [];
    const pageCount = swrInifinite.data?.length || 0;

    return {
        ...swrInifinite,
        hasMore,
        tasks,
        pageIndex,
        pageCount,
        setPageIndex,
        pageTasks,
    };
}
