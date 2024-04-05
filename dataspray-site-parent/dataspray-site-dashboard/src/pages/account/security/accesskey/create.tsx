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

import {NextPageWithLayout} from "../../../_app";
import {useAuth} from "../../../../auth/auth";
import SpaceBetween from "@cloudscape-design/components/space-between";
import {PageHeader} from "../../../../common/PageHeader";
import {CreateApiKey} from "../../../../account/CreateApiKey";
import {ContentLayout} from "@cloudscape-design/components";
import DashboardLayout from "../../../../layout/DashboardLayout";
import BaseAppLayout from "../../../../layout/BaseAppLayout";

const Page: NextPageWithLayout = () => {
    const {} = useAuth('redirect-if-signed-out');

    return (
            <BaseAppLayout
                    content={(
                            <ContentLayout header={
                                <PageHeader title='Security'/>
                            }>
                                <SpaceBetween size="l">
                                    <CreateApiKey/>
                                </SpaceBetween>
                            </ContentLayout>
                    )}
            />
    )
}

Page.getLayout = (page) => (
        <DashboardLayout
                pageTitle='Create Access Key'
        >{page}</DashboardLayout>
)

export default Page
