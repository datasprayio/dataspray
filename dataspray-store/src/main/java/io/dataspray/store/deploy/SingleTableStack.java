package io.dataspray.store.deploy;

import io.dataspray.backend.deploy.BaseStack;
import io.dataspray.store.SingleTableProvider;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awscdk.services.dynamodb.Table;
import software.constructs.Construct;

@Slf4j
public class SingleTableStack extends BaseStack {

    @Getter
    private final Table singleTableTable;

    public SingleTableStack(Construct parent, String stackName) {
        super(parent, stackName);

        singleTableTable = SingleTableProvider.createCdkTable(this, stackName);
    }
}
