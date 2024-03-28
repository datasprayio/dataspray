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

import {TopNavigation} from "@cloudscape-design/components";
import styles from './DashboardLayout.module.scss';
import BaseLayout from "./BaseLayout";
import {useAuth} from "../auth/auth";
import {useMode} from "../util/modeUtil";
import {getDocsUrl, getFeedbackUrl} from "../util/detectEnv";
import {Mode} from "@cloudscape-design/global-styles";
import React from "react";
import DarkModeSvg from "../icons/DarkModeSvg";
import LightModeSvg from "../icons/LightModeSvg";
import {useRouter} from "next/router";

export const TopNavId = 'top-nav';

export default function DashboardLayout(props: {
    children: React.ReactNode,
    pageTitle?: string,
}) {
    const router = useRouter()
    const {idToken, currentOrganizationName, setCurrentOrganizationName} = useAuth('redirect-if-signed-out');
    const {mode, toggle} = useMode();
    const organizations = idToken?.["cognito:groups"] || [];

    // Change organization if requested
    const changeToOrganizationName = router.query.organization as string | undefined;
    React.useEffect(() => {
        if (!changeToOrganizationName
                || changeToOrganizationName === currentOrganizationName
                || !organizations.includes(changeToOrganizationName)) {
            return
        }
        setCurrentOrganizationName(changeToOrganizationName)
    }, [changeToOrganizationName]);

    return (
            <BaseLayout pageTitle={props.pageTitle}>
                <TopNavigation
                        id={TopNavId}
                        className={styles.topNav}
                        identity={{
                            logo: {src: '/logo/logo-small.png', alt: 'Logo'},
                            title: props.pageTitle,
                            href: '/',
                        }}
                        utilities={[
                            {
                                type: 'menu-dropdown',
                                text: currentOrganizationName || 'Create Org',
                                items: [
                                    {
                                        id: "organizations",
                                        text: "Organizations",
                                        items: [
                                            ...organizations.map(organizationName => ({
                                                id: organizationName,
                                                text: organizationName,
                                                disabled: organizationName === currentOrganizationName,
                                                href: "/?organization=" + encodeURIComponent(organizationName),
                                            })),
                                        ]
                                    },
                                    {
                                        id: "create",
                                        text: "Create...",
                                        href: "/auth/create-organization",
                                    },
                                ],
                            },
                            {
                                type: 'button',
                                onClick: toggle,
                                iconSvg: (mode === Mode.Dark ? <DarkModeSvg/> : <LightModeSvg/>),
                            },
                            {
                                type: "menu-dropdown",
                                text: idToken?.["cognito:username"],
                                description: idToken?.email,
                                iconName: "user-profile",
                                items: [
                                    {id: "profile", text: "Profile"},
                                    {
                                        id: "preferences",
                                        text: "Preferences"
                                    },
                                    {id: "security", text: "Security"},
                                    {
                                        id: "support-group",
                                        text: "Support",
                                        items: [
                                            {
                                                id: "documentation",
                                                text: "Documentation",
                                                iconName: "file",
                                                href: getDocsUrl(),
                                                external: true,
                                                externalIconAriaLabel: " (opens in new tab)"
                                            },
                                            {
                                                id: "feedback",
                                                text: "Feedback",
                                                iconName: "bug",
                                                href: getFeedbackUrl(),
                                                external: true,
                                                externalIconAriaLabel: " (opens in new tab)"
                                            },
                                            {
                                                id: "support",
                                                text: "Support",
                                                iconName: "envelope",
                                                href: "mailto:support@dataspray.io",
                                                external: true,
                                                externalIconAriaLabel: " (opens in new tab)"
                                            },
                                        ]
                                    },
                                    {
                                        id: "signout",
                                        text: "Sign out",
                                        href: "/auth/signout",
                                    }]
                            },
                        ]}
                />
                {props.children}
            </BaseLayout>
    )
}
