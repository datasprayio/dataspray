/*
 * Copyright 2025 Matus Faro
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

import {NextPageWithLayout} from "../_app";
import DashboardLayout from "../../layout/DashboardLayout";
import {Box, Container, Header} from "@cloudscape-design/components";
import DashboardAppLayout from "../../layout/DashboardAppLayout";

const Page: NextPageWithLayout = () => {
    return (
        <DashboardAppLayout
            content={
                <Container
                    header={
                        <Header
                            variant="h1"
                            description="View and manage state storage for your tasks"
                        >
                            State Storage
                        </Header>
                    }
                >
                    <Box textAlign="center" padding="xxl">
                        <Box variant="h2" padding={{bottom: 's'}}>
                            State Storage
                        </Box>
                        <Box variant="p" color="text-body-secondary">
                            State storage management will be available here.
                        </Box>
                    </Box>
                </Container>
            }
        />
    )
}

Page.getLayout = (page) => (
    <DashboardLayout
        pageTitle='State'
    >{page}</DashboardLayout>
)

export default Page
