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
 * THE SOFTWARE IS PROVIDED "AS IS"), WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

import React, {useCallback, useEffect, useState} from 'react';
import {
    Box,
    Button,
    Container,
    Header,
    Link,
    Pagination,
    SpaceBetween,
    Table
} from '@cloudscape-design/components';
import {getClient} from '../util/dataSprayClientWrapper';
import {useAlerts} from '../util/useAlerts';
import {S3Object} from 'dataspray-client';

interface Props {
    organizationName: string;
    topicName: string;
}

export function S3FileBrowser({organizationName, topicName}: Props) {
    const [files, setFiles] = useState<S3Object[]>([]);
    const [isLoading, setIsLoading] = useState(false);
    const [prefix, setPrefix] = useState('');
    const [nextToken, setNextToken] = useState<string | undefined>();
    const [currentPage, setCurrentPage] = useState(1);
    const {addAlert} = useAlerts();

    const loadFiles = useCallback(async (prefix: string, token?: string) => {
        if (!organizationName || !topicName) return;

        setIsLoading(true);
        try {
            const response = await getClient().control().listTopicFiles({
                organizationName,
                topicName,
                prefix: prefix || undefined,
                maxResults: 50,
                nextToken: token
            });
            setFiles(response.files || []);
            setNextToken(response.nextToken);
        } catch (e: any) {
            addAlert({
                type: 'error',
                content: `Failed to load files: ${e?.message || 'Unknown error'}`
            });
        } finally {
            setIsLoading(false);
        }
    }, [organizationName, topicName, addAlert]);

    useEffect(() => {
        if (organizationName && topicName) {
            loadFiles(prefix);
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [organizationName, topicName, prefix]);

    const handleDownload = async (key: string) => {
        try {
            const response = await getClient().control().getTopicFileDownloadUrl({
                organizationName,
                topicName,
                key
            });

            // Open the presigned URL in a new tab to download
            window.open(response.url, '_blank');
        } catch (e: any) {
            addAlert({
                type: 'error',
                content: `Failed to generate download URL: ${e?.message || 'Unknown error'}`
            });
        }
    };

    const formatBytes = (bytes: number): string => {
        if (bytes === 0) return '0 Bytes';
        const k = 1024;
        const sizes = ['Bytes', 'KB', 'MB', 'GB', 'TB'];
        const i = Math.floor(Math.log(bytes) / Math.log(k));
        return Math.round(bytes / Math.pow(k, i) * 100) / 100 + ' ' + sizes[i];
    };

    const formatDate = (date: string | Date): string => {
        return new Date(date).toLocaleString();
    };

    // Parse S3 file path to extract metadata
    // Expected format (defined in backend FirehoseS3AthenaBatchStore.java ETL_BUCKET_PREFIX):
    // retention={RETENTION}/organization={ORG}/topic={TOPIC}/year={YYYY}/month={MM}/day={DD}/hour={HH}/{filename}
    // Example: retention=THREE_MONTHS/organization=smotana/topic=http-events/year=2025/month=12/day=21/hour=09/file.gz
    const parseS3Key = (key: string): { filename: string; topic?: string; retention?: string } => {
        try {
            // Regex to extract metadata from S3 path format
            // IMPORTANT: If you change this regex, update the backend path generation in:
            //   dataspray-store/src/main/java/io/dataspray/store/impl/FirehoseS3AthenaBatchStore.java ETL_BUCKET_PREFIX
            const regex = /^retention=([^/]+)\/organization=([^/]+)\/topic=([^/]+)\/year=(\d+)\/month=(\d+)\/day=(\d+)\/hour=(\d+)\/(.+)$/;
            const match = key.match(regex);

            if (match) {
                return {
                    retention: match[1],
                    topic: match[3],
                    filename: match[8]
                };
            }
        } catch (e) {
            console.error('Failed to parse S3 key:', e);
        }

        // Fallback: show full key as filename
        return { filename: key };
    };

    return (
        <Container
            header={
                <Header
                    variant="h2"
                    description="Browse and download files stored in S3"
                    actions={
                        <Button
                            iconName="refresh"
                            onClick={() => loadFiles(prefix)}
                            loading={isLoading}
                        >
                            Refresh
                        </Button>
                    }
                >
                    Files
                </Header>
            }
        >
            <SpaceBetween size="m">
                {prefix && (
                    <Box>
                        <Link
                            variant="primary"
                            onFollow={() => {
                                // Go up one level
                                const parts = prefix.split('/');
                                parts.pop(); // Remove last part
                                parts.pop(); // Remove empty string after trailing slash
                                setPrefix(parts.length > 0 ? parts.join('/') + '/' : '');
                                setCurrentPage(1);
                            }}
                        >
                            ← Back
                        </Link>
                        <Box margin={{left: 's'}} display="inline">
                            Current prefix: {prefix}
                        </Box>
                    </Box>
                )}

                <Table
                    columnDefinitions={[
                        {
                            id: 'actions',
                            header: 'Actions',
                            cell: (item: S3Object) => (
                                <Button
                                    variant="inline-icon"
                                    iconName="download"
                                    onClick={() => handleDownload(item.key)}
                                    ariaLabel="Download file"
                                />
                            ),
                            width: 80
                        },
                        {
                            id: 'size',
                            header: 'Size',
                            cell: (item: S3Object) => formatBytes(item.size),
                            width: 120
                        },
                        {
                            id: 'key',
                            header: 'Name',
                            cell: (item: S3Object) => {
                                const displayName = prefix
                                    ? item.key.substring(prefix.length)
                                    : item.key;

                                // Check if this looks like a folder (ends with multiple path parts)
                                const parts = displayName.split('/');
                                if (parts.length > 1 && parts[parts.length - 1] === '') {
                                    // It's a folder-like prefix
                                    return (
                                        <Link
                                            variant="primary"
                                            onFollow={() => {
                                                setPrefix(item.key);
                                                setCurrentPage(1);
                                            }}
                                        >
                                            📁 {parts[0]}/
                                        </Link>
                                    );
                                }

                                // Parse the S3 key to extract filename
                                const parsed = parseS3Key(item.key);
                                return parsed.filename;
                            },
                            sortingField: 'key'
                        },
                        {
                            id: 'lastModified',
                            header: 'Last Modified',
                            cell: (item: S3Object) => formatDate(item.lastModified),
                            width: 200
                        },
                        {
                            id: 'topic',
                            header: 'Topic',
                            cell: (item: S3Object) => {
                                const parsed = parseS3Key(item.key);
                                return parsed.topic || '-';
                            },
                            width: 150
                        },
                        {
                            id: 'retention',
                            header: 'Retention',
                            cell: (item: S3Object) => {
                                const parsed = parseS3Key(item.key);
                                return parsed.retention || '-';
                            },
                            width: 150
                        }
                    ]}
                    items={files}
                    loading={isLoading}
                    loadingText="Loading files..."
                    empty={
                        <Box textAlign="center">
                            <Box variant="strong">No files</Box>
                            <Box variant="p">No files found in this location.</Box>
                        </Box>
                    }
                    pagination={
                        nextToken ? (
                            <Pagination
                                currentPageIndex={currentPage}
                                pagesCount={currentPage + 1}
                                onNextPageClick={() => {
                                    loadFiles(prefix, nextToken);
                                    setCurrentPage(currentPage + 1);
                                }}
                                onPreviousPageClick={() => {
                                    // Note: S3 list API doesn't support going back,
                                    // so we reload from the beginning
                                    loadFiles(prefix);
                                    setCurrentPage(1);
                                }}
                            />
                        ) : undefined
                    }
                />
            </SpaceBetween>
        </Container>
    );
}
