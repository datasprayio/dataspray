import {Multiselect, MultiselectProps} from "@cloudscape-design/components";
import React, {useRef, useState} from "react";
import {DropdownStatusProps} from "@cloudscape-design/components/internal/components/dropdown-status/interfaces";
import {NonCancelableCustomEvent} from "@cloudscape-design/components/internal/events";
import {OptionsLoadItemsDetail} from "@cloudscape-design/components/internal/components/dropdown/interfaces";
import {getClient} from "../util/dataSprayClientWrapper";
import Option = MultiselectProps.Option;


export const TargetSelect = (props: {
    currentOrganizationName?: string | null,
    selectedTargets: Option[],
    setSelectedTargets: (targets: Option[]) => void,
}) => {

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

        // TODO call backend
        // TODO handle retries too
        try {
            const result = await getClient().control().getTargets({
                organizationName: props.currentOrganizationName,
            })
            setOptions(result.targets.map(target => ({
                label: target.name,
                value: target.name,
            })))
        } catch (e: any) {
            console.error('Failed to fetch targets', e ?? 'Unknown error')
            setError(e?.message || ('Failed to fetch targets: ' + (e || 'Unknown error')))
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
                    selectedOptions={props.selectedTargets}
                    onLoadItems={onLoadItems}
                    onChange={event => props.setSelectedTargets([...event.detail.selectedOptions])}
                    statusType={status}
                    filteringType="auto"

                    /* Labels */
                    selectedAriaLabel="Selected"
                    deselectAriaLabel={option => `Remove option ${option.label} from selection`}
                    placeholder="Choose one or more targets"
                    loadingText="Loading targets"
                    errorText="Error fetching targets."
                    recoveryText="Retry"
                    finishedText='End of all results'
                    empty='No targets'
                    filteringAriaLabel="Filter targets"
                    filteringClearAriaLabel="Clear"
                    ariaRequired={true}
            />
    )
}
