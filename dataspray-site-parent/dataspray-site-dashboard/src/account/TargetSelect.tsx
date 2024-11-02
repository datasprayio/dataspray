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

import {Multiselect, MultiselectProps} from "@cloudscape-design/components";
import React, {useRef, useState} from "react";
import {DropdownStatusProps} from "@cloudscape-design/components/internal/components/dropdown-status/interfaces";
import {NonCancelableCustomEvent} from "@cloudscape-design/components/internal/events";
import {OptionsLoadItemsDetail} from "@cloudscape-design/components/internal/components/dropdown/interfaces";
import {getClient} from "../util/dataSprayClientWrapper";
import Option = MultiselectProps.Option;


export const TargetSelect = (props: {
    currentOrganizationName?: string | null,
    selectedTopics: Option[],
    setSelectedTopics: (topics: Option[]) => void,
} & Partial<React.ComponentProps<typeof Multiselect>>) => {
    const {
        currentOrganizationName,
        selectedTopics,
        setSelectedTopics,
        ...multiSelectProps
    } = props;

    const loadInitiated = useRef<boolean>(false);
    const [options, setOptions] = useState<Option[]>()
    const [loading, setLoading] = useState<boolean>(false)
    const [error, setError] = useState<string | undefined>()

    const onLoadItems = React.useCallback(async (e: NonCancelableCustomEvent<OptionsLoadItemsDetail>) => {
        if (!props.currentOrganizationName || loadInitiated.current) {
            return;
        }
        loadInitiated.current = true;
        setLoading(true);

        try {
            const result = await getClient().control().getTopics({
                organizationName: props.currentOrganizationName,
            })
            setOptions(result.topics.map(target => ({
                label: target.name,
                value: target.name,
            })))
        } catch (e: any) {
            loadInitiated.current = false; // Allow retry
            console.error('Failed to fetch topics', e ?? 'Unknown error')
            setError(e?.message || ('Failed to fetch topics: ' + (e || 'Unknown error')))
        }
        setLoading(false);
    }, [props.currentOrganizationName]);

    let status: DropdownStatusProps.StatusType;
    if (options !== undefined) {
        status = 'finished';
    } else if (loading) {
        status = 'pending';
    } else if (error) {
        status = 'error';
    } else {
        status = 'pending';
    }

    return (
            <Multiselect
                    options={options}
                    selectedOptions={props.selectedTopics}
                    onLoadItems={onLoadItems}
                    onChange={event => props.setSelectedTopics([...event.detail.selectedOptions])}
                    statusType={status}
                    filteringType="auto"

                    /* Labels */
                    selectedAriaLabel="Selected"
                    deselectAriaLabel={option => `Remove option ${option.label} from selection`}
                    placeholder="Choose one or more topics"
                    loadingText="Loading topics"
                    errorText="Error fetching topics."
                    recoveryText="Retry"
                    finishedText='End of all results'
                    empty='No topics'
                    filteringAriaLabel="Filter topics"
                    filteringClearAriaLabel="Clear"
                    ariaRequired={true}

                    {...multiSelectProps}
            />
    )
}
