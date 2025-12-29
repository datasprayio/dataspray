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

import {NextPageWithLayout} from "../../_app";
import DashboardLayout from "../../../layout/DashboardLayout";
import DashboardAppLayout from "../../../layout/DashboardAppLayout";
import {
    Container,
    ContentLayout,
    Header,
    SpaceBetween,
    Select,
    SelectProps
} from "@cloudscape-design/components";
import {useAuth} from "../../../auth/auth";
import {useCallback, useEffect, useState} from "react";
import {getClient} from "../../../util/dataSprayClientWrapper";
import {useAlerts} from "../../../util/useAlerts";
import {Topic} from "dataspray-client";
import {S3FileBrowser} from "../../../deployment/S3FileBrowser";

const LakeFilesPage: NextPageWithLayout = () => {
    const {currentOrganizationName} = useAuth();
    const [topics, setTopics] = useState<Topic[]>([]);
    const [selectedTopic, setSelectedTopic] = useState<SelectProps.Option | null>(null);
    const [isLoadingTopics, setIsLoadingTopics] = useState(false);
    const {addAlert} = useAlerts();

    const loadTopics = useCallback(async () => {
        if (!currentOrganizationName) return;

        setIsLoadingTopics(true);
        try {
            const response = await getClient().control().getTopics({
                organizationName: currentOrganizationName
            });
            const batchTopics = Object.entries(response.topics || {})
                .filter(([_, topic]) => topic.batch)
                .map(([name, topic]) => ({ ...topic, name }));
            setTopics(batchTopics as any);

            // Auto-select first topic if available
            if (batchTopics.length > 0 && !selectedTopic) {
                setSelectedTopic({
                    label: batchTopics[0].name,
                    value: batchTopics[0].name
                });
            }
        } catch (e: any) {
            console.error('Failed to load topics:', e);
            const errorMessage = e.response?.data?.error?.message || e.message || 'Unknown error';
            addAlert({type: 'error', content: `Failed to load topics: ${errorMessage}`});
        } finally {
            setIsLoadingTopics(false);
        }
    }, [currentOrganizationName, selectedTopic, addAlert]);

    useEffect(() => {
        loadTopics();
    }, [currentOrganizationName]);

    return (
        <DashboardAppLayout
            content={(
                <ContentLayout
                    header={<Header variant="h1">Data Lake Files</Header>}
                >
                    <SpaceBetween size="l">
                        <Container
                            header={<Header variant="h2">Select Topic</Header>}
                        >
                            <Select
                                selectedOption={selectedTopic}
                                onChange={({detail}) => setSelectedTopic(detail.selectedOption)}
                                options={topics.map((t: any) => ({
                                    label: t.name,
                                    value: t.name,
                                    description: t.batch ? 'Batch enabled' : ''
                                }))}
                                placeholder="Choose a topic to browse files"
                                empty="No batch-enabled topics found"
                                loadingText="Loading topics..."
                                statusType={isLoadingTopics ? "loading" : "finished"}
                            />
                        </Container>

                        {selectedTopic && currentOrganizationName && (
                            <S3FileBrowser
                                organizationName={currentOrganizationName}
                                topicName={selectedTopic.value!}
                            />
                        )}
                    </SpaceBetween>
                </ContentLayout>
            )}
        />
    );
};

LakeFilesPage.getLayout = (page) => (
    <DashboardLayout
        pageTitle='Lake Files'
    >{page}</DashboardLayout>
);

export default LakeFilesPage;
