import {Box} from "@cloudscape-design/components";
import SpaceBetween from "@cloudscape-design/components/space-between";
import Button from "@cloudscape-design/components/button";

// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0
export const TableNoMatchState = ({onClearFilter}: { onClearFilter: () => void }) => (
        <Box margin={{vertical: 'xs'}} textAlign="center" color="inherit">
            <SpaceBetween size="xxs">
                <div>
                    <b>No matches</b>
                    <Box variant="p" color="inherit">
                        We can&apos;t find a match.
                    </Box>
                </div>
                <Button onClick={onClearFilter}>Clear filter</Button>
            </SpaceBetween>
        </Box>
);
