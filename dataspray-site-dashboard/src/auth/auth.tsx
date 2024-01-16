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

import {AuthResult, getClientAuth, SignInResponse, SignUpResponse} from "../api";
import {urlWithHiddenParams} from "../util/routerUtil";
import React, {useEffect, useMemo, useRef, useState} from "react";
import {Router, useRouter} from "next/router";
import {isCsr, isSsr} from "../util/isoUtil";
import {jwtDecode, JwtPayload} from "jwt-decode";

interface CognitoAccessToken {
    auth_time: number,
    client_id: string,
    device_key: string,
    event_id: string,
    exp: number,
    iat: number,
    iss: string,
    jti: string,
    origin_jti: string,
    scope: string,
    sub: string,
    token_use: string,
    username: string,
}

interface CognitoIdToken {
    aud: string,
    auth_time: number,
    "cognito:username": string,
    "custom:marketing-agreed": string,
    "custom:tos-agreed": string,
    email: string,
    email_verified: boolean,
    event_id: string,
    exp: number,
    iat: number,
    iss: string,
    jti: string,
    origin_jti: string,
    sub: string,
    token_use: string,
}

export const useAuth = (behavior?: 'redirect-if-signed-in' | 'redirect-if-signed-out') => {

    // Keep auth result in memory
    const [authResult, setAuthResult] = useState<undefined | null | AuthResult>()

    const accessToken = React.useMemo(() => {
        return authResult ? jwtDecode(authResult.accessToken) as JwtPayload & CognitoAccessToken : undefined
    }, [authResult]);

    const idToken = React.useMemo(() => {
        return authResult ? jwtDecode(authResult.idToken) as JwtPayload & CognitoIdToken : undefined
    }, [authResult]);

    // On sign in hook to update auth result
    const onSignIn = React.useCallback((result: AuthResult | null) => {
        setAuthResult(result);
        setResultToStorage(result);
    }, []);

    // Load auth result from storage and refresh if necessary
    const router = useRouter();
    useEffect(() => {

        // Fetch from storage inside useEffect to avoid SSR issues
        const authResultFromStorage = getResultFromStorage();

        // Emit null/result to indicate loading is complete
        setAuthResult(authResultFromStorage);

        // Refresh auth result if necessary
        refreshTokenIfNecessary(authResultFromStorage);

    }, []);

    // Redirect if this page requests that user is signed in/out
    const redirected = useRef(false)
    useEffect(() => {
        if (redirected.current || authResult === undefined) return

        // Redirect if necessary
        if (behavior === 'redirect-if-signed-in' && !!authResult) {
            redirected.current = true
            router.push('/');
        } else if (behavior === 'redirect-if-signed-out' && authResult == null) {
            redirected.current = true
            router.push({
                pathname: '/auth/signin',
                query: {to: router.asPath},
            });
        }

        // router and redirect as a dep causes an infinite loop
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [authResult]);

    return useMemo(() => ({
        authResult,
        accessToken,
        idToken,
        signUp: (...args: OmitFirstArg<typeof signUp>) => signUp(onSignIn, ...args),
        signUpConfirmCode: (...args: OmitFirstArg<typeof signUpConfirmCode>) => signUpConfirmCode(onSignIn, ...args),
        signIn: (...args: OmitFirstArg<typeof signIn>) => signIn(onSignIn, ...args),
        signInConfirmTotp: (...args: OmitFirstArg<typeof signInConfirmTotp>) => signInConfirmTotp(onSignIn, ...args),
        signInPasswordChange: (...args: OmitFirstArg<typeof signInPasswordChange>) => signInPasswordChange(onSignIn, ...args),
        signOut: () => signOut(onSignIn, authResult?.refreshToken),
    }), [authResult, accessToken, idToken, onSignIn]);
}

// Persistent storage where to keep auth to persist through page refresh/load
const AuthResultStorageKey = 'DATASPRAY_AUTH_TOKEN';
const getResultFromStorage = () => {
    const token = isCsr() && window.sessionStorage.getItem(AuthResultStorageKey);
    return token ? JSON.parse(token) as AuthResult : null;
}
const setResultToStorage = (result: AuthResult | null) => {
    if (isSsr()) return;
    if (result) {
        window.sessionStorage.setItem(AuthResultStorageKey, JSON.stringify(result));
    } else {
        window.sessionStorage.removeItem(AuthResultStorageKey);
    }
}

const refreshTokenIfNecessary = (authResult: AuthResult | null) => {
    // TODO refresh token if necessary
    // TODO setup future promise to refresh token
    // TODO use signInRefresh()
}

const signUp = async (
    onSignIn: (response: AuthResult | null) => void,
    values: {
        username: string,
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
                username: values.username,
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
    onSignIn: (response: AuthResult | null) => void,
    username: string,
    code: string,
    password: string | undefined,
    setError: (error: string) => void,
    routerPush: Router['push'],
): Promise<void> => {
    try {
        const signupResponse = await getClientAuth().signUpConfirmCode({
            signUpConfirmCodeRequest: {username, code}
        });

        await handleSignupResponse(onSignIn, signupResponse, username, password, setError, routerPush);
    } catch (e: any) {
        console.error('Failed to confirm code', e ?? 'Unknown error')
        setError(e?.message || ('Failed to confirm code: ' + (e || 'Unknown error')))
    }
};

const handleSignupResponse = async (
    onSignIn: (response: AuthResult | null) => void,
    signupResponse: SignUpResponse,
    username: string,
    password: string | undefined,
    setError: (error: string) => void,
    routerPush: Router['push'],
): Promise<void> => {

    // All good, sign-up doesn't log in, attempt to login, or redirect to login page
    if (signupResponse.confirmed) {

        // Attempt to sign in if we still remember the password
        // The password is only kept as router state so it's lost on refresh
        if (password) {
            const authResult = await signIn(
                onSignIn,
                {usernameOrEmail: username, password},
                undefined,
                setError,
                routerPush)
            if (authResult) {
                return; // All good, signed up, signed in, and already redirect
            }
            // Failed to sign in after sign up for some reason, redirect to login page
        }

        // On sign-in error or when we don't remember the password, redirect to login page
        await routerPush(...urlWithHiddenParams({
            pathname: '/auth/signin',
            query: {usernameOrEmail: username},
        }))
        return;
    }

    // Email confirmation necessary, redirect to ask for code
    if (signupResponse.codeRequired) {
        await routerPush(...urlWithHiddenParams({
            pathname: '/auth/confirm-email',
            query: {
                username: signupResponse.codeRequired.username,
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
    onSignIn: (response: AuthResult | null) => void,
    values: {
        usernameOrEmail: string,
        password: string,
    },
    to: string | undefined,
    setError: (error: string) => void,
    routerPush: Router['push'],
): Promise<AuthResult | undefined> => {
    try {
        const signInResponse = await getClientAuth().signIn({
            signInRequest: {
                usernameOrEmail: values.usernameOrEmail,
                password: values.password,
            }
        });

        return await handleSignInResponse(onSignIn, signInResponse, values.password, to, setError, routerPush);
    } catch (e: any) {
        console.error('Failed to sign in', e ?? 'Unknown error')
        setError(e?.message || ('Failed to sign in: ' + (e || 'Unknown error')))
        return;
    }
}

const signInConfirmTotp = async (
    onSignIn: (response: AuthResult | null) => void,
    username: string,
    session: string,
    code: string,
    to: string | undefined,
    setError: (error: string) => void,
    routerPush: Router['push'],
): Promise<AuthResult | undefined> => {

    try {
        const signInResponse = await getClientAuth().signInChallengeTotpCode({
            signInChallengeTotpCodeRequest: {username, code, session}
        });

        return await handleSignInResponse(onSignIn, signInResponse, undefined, to, setError, routerPush);
    } catch (e: any) {
        console.error('Failed to confirm TOTP code', e ?? 'Unknown error')
        setError(e?.message || ('Failed to sign in: ' + (e || 'Unknown error')))
        return;
    }
}

const signInPasswordChange = async (
    onSignIn: (response: AuthResult | null) => void,
    username: string,
    session: string,
    newPassword: string,
    to: string | undefined,
    setError: (error: string) => void,
    routerPush: Router['push'],
): Promise<AuthResult | undefined> => {
    try {
        const signInResponse = await getClientAuth().signInChallengePasswordChange({
            signInChallengePasswordChangeRequest: {username, session, newPassword}
        });

        return await handleSignInResponse(onSignIn, signInResponse, undefined, to, setError, routerPush);
    } catch (e: any) {
        console.error('Failed to change password', e ?? 'Unknown error')
        setError(e?.message || ('Failed to change password: ' + (e || 'Unknown error')))
        return;
    }
}

const handleSignInResponse = async (
    onSignIn: (response: AuthResult | null) => void,
    signInResponse: SignInResponse,
    password: string | undefined,
    to: string | undefined,
    setError: (error: string) => void,
    routerPush: Router['push'],
): Promise<AuthResult | undefined> => {

    // Email confirmation necessary, redirect to ask for code
    if (signInResponse.codeRequired) {
        await routerPush(...urlWithHiddenParams({
            pathname: '/auth/confirm-email',
            query: {
                username: signInResponse.codeRequired.username,
                password
            },
        }, 'password'))
        return;
    }

    // Password needs to be changed
    if (signInResponse.challengePasswordChange) {
        await routerPush(...urlWithHiddenParams({
            pathname: '/auth/password-change',
            query: {
                username: signInResponse.challengePasswordChange.username,
                session: signInResponse.challengePasswordChange.session,
            },
        }))
        return;
    }

    // TOTP code is required
    if (signInResponse.challengeTotpCode) {
        await routerPush(...urlWithHiddenParams({
            pathname: '/auth/totp',
            query: {
                to,
                username: signInResponse.challengeTotpCode.username,
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
    await routerPush(to || '/');
    return signInResponse.result;
}

const signInRefresh = async (
    onSignIn: (response: AuthResult | null) => void,
    refreshToken: string,
    setError: (error: string) => void,
    routerPush: Router['push'],
): Promise<AuthResult | undefined> => {
    try {
        const signInResponse = await getClientAuth().signInRefreshToken({
            signInRefreshTokenRequest: {refreshToken}
        })

        return await handleSignInResponse(onSignIn, signInResponse, undefined, undefined, setError, routerPush);
    } catch (e: any) {
        console.error('Failed to refresh session', e ?? 'Unknown error')
        setError(e?.message || ('Failed to refresh session: ' + (e || 'Unknown error')))
        return;
    }
}

const signOut = async (
    onSignIn: (response: AuthResult | null) => void,
    refreshToken?: string,
): Promise<undefined> => {

    // Remove token from browser session
    onSignIn(null);

    // Invalidate tokens from server
    if (refreshToken) {
        try {
            await getClientAuth().signOut({
                signOutRequest: {refreshToken}
            });
        } catch (e: any) {
            console.error('Failed to sign out', e ?? 'Unknown error')
        }
    }
}
