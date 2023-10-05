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

package io.dataspray.store;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.google.gson.Gson;
import io.dataspray.singletable.SingleTable;
import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@Slf4j
@ApplicationScoped
public class SingleTableProvider {

    public static final String TABLE_PREFIX_PROP_NAME = "singletable.tablePrefix";
    public static final String TABLE_PREFIX_DEFAULT = "dataspray";
    public static final int LSI_COUNT = 0;
    public static final int GSI_COUNT = 2;

    @ConfigProperty(name = TABLE_PREFIX_PROP_NAME, defaultValue = TABLE_PREFIX_DEFAULT)
    String tablePrefix;
    @ConfigProperty(name = "singletable.createTableOnStartup", defaultValue = "false")
    boolean createTableOnStartup;

    @Inject
    Gson gson;
    @Inject
    AmazonDynamoDB dynamo;

    @Singleton
    public SingleTable getSingleTable() {
        log.debug("Opening SingleTable");
        return SingleTable.builder()
                .tablePrefix(tablePrefix)
                .overrideDynamo(dynamo)
                .overrideGson(gson)
                .build();
    }

    @Startup
    void init(SingleTable singleTable) {
        if (createTableOnStartup) {
            singleTable.createTableIfNotExists(LSI_COUNT, GSI_COUNT);
        }
    }
}
