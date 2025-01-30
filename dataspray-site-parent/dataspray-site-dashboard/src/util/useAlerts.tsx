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


import {useCallback} from "react";
import {FlashbarProps} from "@cloudscape-design/components";
import {create} from "zustand";
import {persist, subscribeWithSelector} from "zustand/middleware";
import {memoryStorage} from "./memoryStorage";

export interface AlertDefinition extends FlashbarProps.MessageDefinition {
    id: string;
}

export const useAlertsStore = create<{
    items: AlertDefinition[];
    addAlert: (alert: AlertDefinition) => void;
    removeAlert: (id: string) => void;
}>()(
    subscribeWithSelector(
        persist(
            set => ({
                items: [],
                addAlert: (alert: AlertDefinition) => set(s => ({
                    items: [
                        ...s.items.filter(({id}) => id !== alert.id),
                        alert
                    ]
                })),
                removeAlert: (id: string) => set(s => ({
                    items: s.items.filter(({id: alertId}) => alertId !== id)
                })),
            }),
            {
                name: "ds_store_alerts",
                storage: memoryStorage(),
            },
        )
    )
);

export const useAlerts = (): {
    addAlert: (alert: FlashbarProps.MessageDefinition) => () => void;
    removeAlert: (id: string) => void;
    beginProcessing: (alertInProgress: FlashbarProps.MessageDefinition) => {
        onSuccess: (alertSuccess: FlashbarProps.MessageDefinition) => void;
        onError: (alertError: FlashbarProps.MessageDefinition) => void;
    };
} => {
    const {
        addAlert,
        removeAlert,
    } = useAlertsStore();

    const addAlertWithoutId = useCallback((alert: FlashbarProps.MessageDefinition) => {
        const id = alert.id || Math.random().toString(36).substring(7);
        addAlert({
            id,
            dismissible: true,
            onDismiss: () => removeAlert(id),
            ...alert});
        return () => removeAlert(id);
    }, [addAlert, removeAlert]);

    const beginProcessing = useCallback((alertInProgress: FlashbarProps.MessageDefinition) => {
        let dismissInProgress = addAlertWithoutId({
            type: 'in-progress',
            content: 'Processing...',
            loading: true,
            ...alertInProgress,
        });
        return {
            onSuccess: (alertSuccess: FlashbarProps.MessageDefinition) => {
                dismissInProgress();
                return addAlertWithoutId({
                    type: 'success',
                    content: 'Operation completed successfully',
                    ...alertSuccess,
                });
            },
            onError: (alertError: FlashbarProps.MessageDefinition) => {
                dismissInProgress();
                return addAlertWithoutId({
                    type: 'error',
                    content: 'Failed to complete the operation',
                    ...alertError,
                });
            },
        };
    }, [addAlertWithoutId]);

    return {
        addAlert: addAlertWithoutId,
        removeAlert,
        beginProcessing,
    };
}
