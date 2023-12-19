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

import {AppLayout, TopNavigation} from "@cloudscape-design/components";
import styles from './DashboardLayout.module.scss';
import Navigation from "./Navigation";
import BaseLayout from "../../BaseLayout";
import {useAuth} from "../auth/auth";

export default function DashboardLayout(props: {
    children: React.ReactNode,
    pageTitle?: string,
}) {
    const {authResult} = useAuth('redirect-if-signed-out');
    return (
        <BaseLayout pageTitle={props.pageTitle}>
            <div id='top-nav' className={styles.topNav}>
                <TopNavigation
                    identity={{
                        logo: {src: '/logo/logo-small.png', alt: 'Logo'},
                        title: props.pageTitle,
                        href: '/dashboard',
                    }}
                />
            </div>
            {!!authResult && (
                <AppLayout
                    headerSelector='#top-nav'
                    navigation={(<Navigation/>)}
                />
            )}
            {props.children}
        </BaseLayout>
    )
}
