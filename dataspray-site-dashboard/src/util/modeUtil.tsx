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

import {applyMode as applyModeCloudscape, Mode} from "@cloudscape-design/global-styles";
import {isCsr, isSsr} from "./isoUtil";
import React, {useState} from "react";

const ModeStorageKey = 'DATASPRAY_MODE';
let currentMode: Mode = Mode.Light;
let currentModeChanged: ((mode: Mode) => void) | undefined;

export const applyDefaultMode = () => {
    if (isSsr()) return

    const modePersistent = getPersistentMode()
    if (modePersistent) return applyMode(modePersistent);

    const modeBrowserPreference = (window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches) ? Mode.Dark : Mode.Light;
    return applyMode(modeBrowserPreference);
}

const applyMode = (mode: Mode) => {
    currentMode = mode;
    currentModeChanged?.(mode);
    setPersistentMode(mode);
    applyModeCloudscape(mode);
    return mode;
}

const toggleMode = () => applyMode(currentMode === Mode.Dark ? Mode.Light : Mode.Dark);

const setPersistentMode = (mode: Mode) => isCsr() && window.localStorage.setItem(ModeStorageKey, mode);
const getPersistentMode = (): Mode | undefined => isCsr() && window.localStorage.getItem(ModeStorageKey) as (Mode | null) || undefined;

export const useMode = () => {

    const [modeCache, setModeCache] = useState<Mode | undefined>(undefined);

    // Apply initial state of mode
    React.useEffect(() => {
        modeCache !== currentMode && setModeCache(currentMode);
        // Only run once to set default
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, []);

    // Setup callback for if mode changes externally
    React.useEffect(() => {
        currentModeChanged = mode => setModeCache(mode);
        return () => currentModeChanged = undefined;
    }, [setModeCache]);

    // Toggle mode function
    const onChange = React.useCallback(() => {
        setModeCache(toggleMode());
    }, []);

    return {
        mode: modeCache,
        toggle: onChange,
    };
}
