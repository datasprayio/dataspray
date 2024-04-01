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

package io.dataspray.cdk.store;

import io.dataspray.cdk.template.BaseStack;
import io.dataspray.common.DeployEnvironment;
import io.dataspray.singletable.SingleTable;
import io.dataspray.store.SingleTableProvider;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awscdk.services.dynamodb.Table;
import software.constructs.Construct;

@Slf4j
@Getter
public class SingleTableStack extends BaseStack {

    private final String tablePrefix;
    private final Table singleTableTable;

    public SingleTableStack(Construct parent, DeployEnvironment deployEnv) {
        super(parent, "singletable", deployEnv);

        tablePrefix = getConstructId("control");
        singleTableTable = SingleTable.builder()
                .tablePrefix(tablePrefix)
                .build()
                .createCdkTable(
                        this,
                        tablePrefix,
                        SingleTableProvider.LSI_COUNT,
                        SingleTableProvider.GSI_COUNT);

        // TODO delete me after next deploy
        SingleTable.builder()
                .tablePrefix(getConstructId("dataspray"))
                .build()
                .createCdkTable(
                        this,
                        "dataspray",
                        SingleTableProvider.LSI_COUNT,
                        SingleTableProvider.GSI_COUNT);
    }
}
