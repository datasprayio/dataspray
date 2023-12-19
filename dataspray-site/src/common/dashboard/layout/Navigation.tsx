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

import {SideNavigation, SideNavigationProps} from "@cloudscape-design/components";
import {useRouterOnFollow} from "../../util/useRouterOnFollow";
import {useRouter} from "next/router";

const items: SideNavigationProps['items'] = [
    {type: 'link', text: 'Home', href: '/dashboard'},
    {
        type: 'section', text: 'Explore', items: [
            {type: 'link', text: 'Visual', href: '/dashboard/explore/visual'},
            {type: 'link', text: 'Query editor', href: '/dashboard/explore/query-editor'},
        ]
    },
    {
        type: 'section', text: 'Deployment', items: [
            {type: 'link', text: 'Tasks', href: '/dashboard/deployment/task'},
            {type: 'link', text: 'Queues', href: '/dashboard/deployment/queues'},
        ]
    },
    {
        type: 'section', text: 'Development', items: [
            {type: 'link', text: 'Visual', href: '/dashboard/development/task'},
            {type: 'link', text: 'Tasks', href: '/dashboard/development/task'},
        ]
    },
];

export default function Navigation(props: {}) {
    const router = useRouter();
    const routerOnFollow = useRouterOnFollow();

    return (
        <SideNavigation
            activeHref={router.pathname}
            header={{href: '/home/index.html', text: 'Service'}}
            items={items}
            onFollow={routerOnFollow}
        />
    )
}
