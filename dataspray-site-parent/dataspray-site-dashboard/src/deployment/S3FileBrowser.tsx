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
import {getErrorMessage} from '../util/errorUtil';

interface Props {
    organizationName: string;
    topicName: string;
}

/**
 * Row shown in the browser table: either a real S3 object (file) or a virtual
 * folder derived client-side from the next `/`-separated segment of the keys
 * relative to the current prefix (the S3 listing is done without a delimiter,
 * so it never returns folder entries itself).
 */
type BrowserItem =
    | { itemType: 'folder'; name: string; fullPrefix: string }
    | ({ itemType: 'file' } & S3Object);

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
                content: `Failed to load files: ${await getErrorMessage(e)}`
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
                content: `Failed to generate download URL: ${await getErrorMessage(e)}`
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

    // Derive virtual folders from the next `/`-separated segment of each key
    // relative to the current prefix; deduped folders are listed before files.
    const items: BrowserItem[] = React.useMemo(() => {
        const folderNames = new Set<string>();
        const fileItems: BrowserItem[] = [];
        for (const file of files) {
            const relativeKey = file.key.startsWith(prefix)
                ? file.key.substring(prefix.length)
                : file.key;
            const slashIndex = relativeKey.indexOf('/');
            if (slashIndex >= 0) {
                // Key is nested deeper; surface only its next segment as a folder
                folderNames.add(relativeKey.substring(0, slashIndex));
            } else if (relativeKey) {
                fileItems.push({itemType: 'file', ...file});
            }
        }
        return [
            ...Array.from(folderNames).sort().map(name => ({
                itemType: 'folder' as const,
                name,
                fullPrefix: prefix + name + '/',
            })),
            ...fileItems,
        ];
    }, [files, prefix]);

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
                                // Go up exactly one segment
                                const segments = prefix.split('/').filter(segment => segment.length > 0);
                                segments.pop();
                                setPrefix(segments.length > 0 ? segments.join('/') + '/' : '');
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
                            cell: (item: BrowserItem) => item.itemType === 'file' ? (
                                <Button
                                    variant="inline-icon"
                                    iconName="download"
                                    onClick={() => handleDownload(item.key)}
                                    ariaLabel="Download file"
                                />
                            ) : null,
                            width: 80
                        },
                        {
                            id: 'size',
                            header: 'Size',
                            cell: (item: BrowserItem) => item.itemType === 'file'
                                ? formatBytes(item.size)
                                : '-',
                            width: 120
                        },
                        {
                            id: 'key',
                            header: 'Name',
                            cell: (item: BrowserItem) => {
                                if (item.itemType === 'folder') {
                                    return (
                                        <Link
                                            variant="primary"
                                            onFollow={() => {
                                                setPrefix(item.fullPrefix);
                                                setCurrentPage(1);
                                            }}
                                        >
                                            📁 {item.name}/
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
                            cell: (item: BrowserItem) => item.itemType === 'file'
                                ? formatDate(item.lastModified)
                                : '-',
                            width: 200
                        },
                        {
                            id: 'topic',
                            header: 'Topic',
                            cell: (item: BrowserItem) => item.itemType === 'file'
                                ? (parseS3Key(item.key).topic || '-')
                                : '-',
                            width: 150
                        },
                        {
                            id: 'retention',
                            header: 'Retention',
                            cell: (item: BrowserItem) => item.itemType === 'file'
                                ? (parseS3Key(item.key).retention || '-')
                                : '-',
                            width: 150
                        }
                    ]}
                    items={items}
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
