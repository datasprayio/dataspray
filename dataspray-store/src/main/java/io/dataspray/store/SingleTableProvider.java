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
import software.amazon.awscdk.services.dynamodb.Table;
import software.constructs.Construct;

@Slf4j
@ApplicationScoped
public class SingleTableProvider {

    public static final String TABLE_PREFIX = "dataspray";
    private static final int LSI_COUNT = 0;
    private static final int GSI_COUNT = 2;


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
                .tablePrefix(TABLE_PREFIX)
                .overrideDynamo(dynamo)
                .overrideGson(gson)
                .build();
    }

    /**
     * Workaround to provide access to the CDK table within SingleTable without involving Quarkus CDI.
     */
    public static Table createCdkTable(Construct scope, String stackId) {
        return SingleTable.builder()
                .tablePrefix(TABLE_PREFIX)
                .build()
                .createCdkTable(scope, stackId, LSI_COUNT, GSI_COUNT);
    }

    @Startup
    void init(SingleTable singleTable) {
        if (createTableOnStartup) {
            singleTable.createTableIfNotExists(LSI_COUNT, GSI_COUNT);
        }
    }
}
