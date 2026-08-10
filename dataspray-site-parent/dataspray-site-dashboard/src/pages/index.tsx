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

import {NextPageWithLayout} from "./_app";
import DashboardLayout from "../layout/DashboardLayout";
import DashboardAppLayout from "../layout/DashboardAppLayout";
import {
    Box,
    Button,
    Container,
    ContentLayout,
    Header,
    SpaceBetween
} from "@cloudscape-design/components";
import {getDocsUrl} from "../util/detectEnv";

const Page: NextPageWithLayout = () => {
    return (
        <DashboardAppLayout
            content={(
                <ContentLayout
                    header={<Header variant="h1">Welcome to DataSpray</Header>}
                >
                    <SpaceBetween size="l">
                        <Container
                            header={
                                <Header
                                    variant="h2"
                                    description="Manage your stream processing pipeline from here"
                                >
                                    Get started
                                </Header>
                            }
                        >
                            <SpaceBetween size="m">
                                <Box variant="p">
                                    DataSpray is a stream processing developer toolkit. Deploy tasks,
                                    manage topics, and query your data lake all in one place.
                                </Box>
                                <SpaceBetween direction="horizontal" size="xs">
                                    <Button variant="primary" href="/deployment/task">
                                        Tasks
                                    </Button>
                                    <Button href="/deployment/topic">
                                        Topics
                                    </Button>
                                    <Button href="/storage/lake/query">
                                        Query Data Lake
                                    </Button>
                                    <Button
                                        href={getDocsUrl()}
                                        iconAlign="right"
                                        iconName="external"
                                        target="_blank"
                                    >
                                        Documentation
                                    </Button>
                                </SpaceBetween>
                            </SpaceBetween>
                        </Container>
                    </SpaceBetween>
                </ContentLayout>
            )}
        />
    )
}

Page.getLayout = (page) => (
    <DashboardLayout
        pageTitle='Home'
    >{page}</DashboardLayout>
)

export default Page
