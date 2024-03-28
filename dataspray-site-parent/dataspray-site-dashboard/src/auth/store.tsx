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

import {AuthResult} from "dataspray-client";
import {getClient} from "../util/dataSprayClientWrapper";
import {create} from "zustand";
import {createJSONStorage, persist, subscribeWithSelector} from "zustand/middleware";

interface State {
    authResult: AuthResult | null | undefined,
    setAuthResult: (result: AuthResult | null) => void,
    currentOrganizationName: string | null,
    setCurrentOrganizationName: (currentOrganizationName: string) => void,
}

const useAuthStore = create<State>()(
        subscribeWithSelector(
        persist(
                set => ({
                    authResult: undefined,
                    setAuthResult: authResult => set(state => ({...state, authResult})),
                    currentOrganizationName: null,
                    setCurrentOrganizationName: currentOrganizationName => set(state => ({
                        ...state,
                        currentOrganizationName
                    })),
                }),
                {
                    name: "ds_store_auth",
                    storage: createJSONStorage(() => sessionStorage),
                },
        )
        )
);

// Listening changes to auth result to trigger updating the access token in the DS client
const onAuthResultHandler = (authResult: AuthResult | null | undefined) => {
    if (authResult) {
        getClient().setAccessToken(authResult.accessToken)
    } else {
        getClient().unsetAuth();
    }
}
useAuthStore.subscribe(state => state.authResult, onAuthResultHandler);
onAuthResultHandler(useAuthStore.getState().authResult);

export default useAuthStore;
