/*
 * Copyright 2023 Matus Faro
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

import {getClientAuth} from "../client";
import {urlWithHiddenParams} from "../../util/routerUtil";
import {AuthResult, SignInResponse, SignUpResponse} from "../../../client";
import React, {useEffect, useMemo, useState} from "react";
import {Router, useRouter} from "next/router";
import {isCsr} from "../../util/isoUtil";


export const useAuth = (redirect?: 'redirect-if-signed-in' | 'redirect-if-signed-out') => {

    // Fetch auth result from persistent storage
    const authResultFromStorage = useMemo(getResultFromStorage, []);

    // Keep auth result in memory
    const [authResult, setAuthResult] = useState(authResultFromStorage)

    // Refresh auth result if necessary
    useEffect(() => refreshTokenIfNecessary(authResult), [authResult]);

    // On sign in hook to update auth result
    const onSignIn = React.useCallback(async (result: AuthResult) => {
        setAuthResult(result)
        setResultToStorage(result)
    }, []);

    // Redirect if this page requests that user is signed in/out
    const router = useRouter();
    useEffect(() => {
        if(redirect === 'redirect-if-signed-in' && authResult) {
            router.push('/dashboard');
        } else if(redirect === 'redirect-if-signed-out' && !authResult) {
            router.push({
                pathname: '/dashboard/auth/signin',
                query: {to: router.asPath},
            });
        }
    }, [authResult, redirect, router]);

    return useMemo(() => ({
        authResult,
        signUp: (...args: OmitFirstArg<typeof signUp>) => signUp(onSignIn, ...args),
        signUpConfirmCode: (...args: OmitFirstArg<typeof signUpConfirmCode>) => signUpConfirmCode(onSignIn, ...args),
        signIn: (...args: OmitFirstArg<typeof signIn>) => signIn(onSignIn, ...args),
        signInConfirmTotp: (...args: OmitFirstArg<typeof signInConfirmTotp>) => signInConfirmTotp(onSignIn, ...args),
        signInPasswordChange: (...args: OmitFirstArg<typeof signInPasswordChange>) => signInPasswordChange(onSignIn, ...args),
    }), [onSignIn]);
}

// Persistent storage where to keep auth to persist through page refresh/load
const AuthResultStorageKey = 'DATASPRAY_AUTH_TOKEN';
const getResultFromStorage = () => {
    const token = isCsr() && window.sessionStorage.getItem(AuthResultStorageKey);
    return token ? JSON.parse(token) as AuthResult : undefined;
}
const setResultToStorage = (result: AuthResult) => {
    isCsr() && window.sessionStorage.putItem(AuthResultStorageKey, JSON.stringify(result));
}

const refreshTokenIfNecessary = (authResult?: AuthResult) => {
    // TODO refresh token
}

const signUp = async (
    onSignIn: (response: AuthResult) => void,
    values: {
        email: string,
        password: string,
        marketingAgree: boolean,
        tosAgree: boolean,
    },
    setError: (error: string) => void,
    routerPush: Router['push'],
): Promise<void> => {
    try {
        const signupResponse = await getClientAuth().signUp({
            signUpRequest: {
                email: values.email,
                password: values.password,
                marketingAgreed: values.marketingAgree,
                tosAgreed: values.tosAgree,
            }
        });

        await handleSignupResponse(onSignIn, signupResponse, values.email, values.password, setError, routerPush);
    } catch (e: any) {
        console.error('Failed to sign up', e ?? 'Unknown error')
        setError(e?.message || ('Failed to sign up: ' + (e || 'Unknown error')))
    }
};


const signUpConfirmCode = async (
    onSignIn: (response: AuthResult) => void,
    email: string,
    code: string,
    password: string | undefined,
    setError: (error: string) => void,
    routerPush: Router['push'],
): Promise<void> => {
    try {
        const signupResponse = await getClientAuth().signUpConfirmCode({
            signUpConfirmCodeRequest: { email, code }
        });

        await handleSignupResponse(onSignIn, signupResponse, email, password, setError, routerPush);
    } catch (e: any) {
        console.error('Failed to confirm code', e ?? 'Unknown error')
        setError(e?.message || ('Failed to confirm code: ' + (e || 'Unknown error')))
    }
};

const handleSignupResponse = async (
    onSignIn: (response: AuthResult) => void,
    signupResponse: SignUpResponse,
    email: string,
    password: string | undefined,
    setError: (error: string) => void,
    routerPush: Router['push'],
): Promise<void> => {

    // All good, sign-up doesn't log in, attempt to login, or redirect to login page
    if (signupResponse.confirmed) {

        // Attempt to sign in if we still remember the password
        // The password is only kept as router state so it's lost on refresh
        if (password) {
            const authResult = await signIn(onSignIn, {email, password}, undefined, setError, routerPush)
            if (authResult) {
                return; // All good, signed up, signed in, and already redirect
            }
            // Failed to sign in after sign up for some reason, redirect to login page
        }

        // On sign-in error or when we don't remember the password, redirect to login page
        await routerPush(...urlWithHiddenParams({
            pathname: '/dashboard/auth/signin',
            query: {email},
        }))
        return;
    }

    // Email confirmation necessary, redirect to ask for code
    if (signupResponse.codeRequired) {
        await routerPush(...urlWithHiddenParams({
            pathname: '/dashboard/auth/confirm-email',
            query: {
                email: email,
                password: password,
            },
        }, 'password'))
        return;
    }

    // Something happened, show error
    if (signupResponse.errorMsg) {
        setError(signupResponse.errorMsg)
        return;
    }

    // Unknown state,
    setError('Unknown error')
}

const signIn = async (
    onSignIn: (response: AuthResult) => void,
    values: {
        email: string,
        password: string,
    },
    to: string | undefined,
    setError: (error: string) => void,
    routerPush: Router['push'],
): Promise<AuthResult | undefined> => {

    let signInResponse: SignInResponse;
    try {
        signInResponse = await getClientAuth().signIn({
            signInRequest: {
                email: values.email,
                password: values.password,
            }
        });
    } catch (e: any) {
        console.error('Failed to sign in', e ?? 'Unknown error')
        setError(e?.message || ('Failed to sign in: ' + (e || 'Unknown error')))
        return;
    }

    return await handleSignInResponse(onSignIn, signInResponse, values.email, values.password, to, setError, routerPush);
}

const signInConfirmTotp = async (
    onSignIn: (response: AuthResult) => void,
    email: string,
    session: string,
    code: string,
    to: string | undefined,
    setError: (error: string) => void,
    routerPush: Router['push'],
): Promise<AuthResult | undefined> => {

    let signInResponse: SignInResponse;
    try {
        signInResponse = await getClientAuth().signInChallengeTotpCode({
            signInChallengeTotpCodeRequest: {email, code, session}
        });
    } catch (e: any) {
        console.error('Failed to confirm TOTP code', e ?? 'Unknown error')
        setError(e?.message || ('Failed to sign in: ' + (e || 'Unknown error')))
        return;
    }

    return await handleSignInResponse(onSignIn, signInResponse, email, undefined, to, setError, routerPush);
}

const signInPasswordChange = async (
    onSignIn: (response: AuthResult) => void,
    email: string,
    session: string,
    newPassword: string,
    to: string | undefined,
    setError: (error: string) => void,
    routerPush: Router['push'],
): Promise<AuthResult | undefined> => {

    let signInResponse: SignInResponse;
    try {
        signInResponse = await getClientAuth().signInChallengePasswordChange({
            signInChallengePasswordChangeRequest: {email, session, newPassword}
        });
    } catch (e: any) {
        console.error('Failed to change password', e ?? 'Unknown error')
        setError(e?.message || ('Failed to change password: ' + (e || 'Unknown error')))
        return;
    }

    return await handleSignInResponse(onSignIn, signInResponse, email, undefined, to, setError, routerPush);
}

const handleSignInResponse = async (
    onSignIn: (response: AuthResult) => void,
    signInResponse: SignInResponse,
    email: string,
    password: string | undefined,
    to: string | undefined,
    setError: (error: string) => void,
    routerPush: Router['push'],
): Promise<AuthResult | undefined> => {

    // Email confirmation necessary, redirect to ask for code
    if (signInResponse.codeRequired) {
        await routerPush(...urlWithHiddenParams({
            pathname: '/dashboard/auth/confirm-email',
            query: {email, password},
        }, 'password'))
        return;
    }

    // Password needs to be changed
    if (signInResponse.challengePasswordChange) {
        await routerPush(...urlWithHiddenParams({
            pathname: '/dashboard/auth/password-change',
            query: {
                email,
                session: signInResponse.challengePasswordChange.session,
            },
        }))
        return;
    }

    // TOTP code is required
    if (signInResponse.challengeTotpCode) {
        await routerPush(...urlWithHiddenParams({
            pathname: '/dashboard/auth/totp',
            query: {
                to,
                email,
                session: signInResponse.challengeTotpCode.session,
            },
        }))
        return;
    }

    if (signInResponse.errorMsg) {
        setError(signInResponse.errorMsg)
        return;
    }

    if (!signInResponse.result) {
        setError('Unknown error')
        return;
    }

    // Persist token
    onSignIn(signInResponse.result);

    // All good, redirect to dashboard
    await routerPush(to || '/dashboard');
    return signInResponse.result;
}
