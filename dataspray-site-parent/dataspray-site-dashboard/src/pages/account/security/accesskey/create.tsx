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
import {PageHeader} from "../../../../common/PageHeader";
import {CreateApiKey} from "../../../../account/CreateApiKey";
import {CreatedApiKey} from "../../../../account/CreatedApiKey";
import {ContentLayout} from "@cloudscape-design/components";
import DashboardLayout from "../../../../layout/DashboardLayout";
import BaseAppLayout from "../../../../layout/BaseAppLayout";
import {useState} from "react";
import {ApiKeyWithSecret} from "dataspray-client";

const Page: NextPageWithLayout = () => {
    const {} = useAuth('redirect-if-signed-out');
    const [apiKey, setApiKey] = useState<ApiKeyWithSecret>()

    return (
            <BaseAppLayout
                    content={(
                            <ContentLayout header={
                                <PageHeader title='Create Access Key'
                                            description="You can create an Access Key to use the DataSpray API programmatically."/>
                            }>
                                {!apiKey ? (
                                        <CreateApiKey onCreated={setApiKey}/>
                                ) : (
                                        <CreatedApiKey apiKey={apiKey}/>
                                )}
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
