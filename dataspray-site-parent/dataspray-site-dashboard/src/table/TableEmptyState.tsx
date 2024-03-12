import {Box} from "@cloudscape-design/components";
import SpaceBetween from "@cloudscape-design/components/space-between";
import Button from "@cloudscape-design/components/button";

// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0
export const TableEmptyState = ({resourceName}: { resourceName: string }) => (
        <Box margin={{vertical: 'xs'}} textAlign="center" color="inherit">
            <SpaceBetween size="xxs">
                <div>
                    <b>No {resourceName.toLowerCase()}s</b>
                    <Box variant="p" color="inherit">
                        No {resourceName.toLowerCase()}s associated with this resource.
                    </Box>
                </div>
                <Button>Create {resourceName.toLowerCase()}</Button>
            </SpaceBetween>
        </Box>
);
