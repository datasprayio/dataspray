package io.dataspray.core.definition.model;

import lombok.EqualsAndHashCode;
import lombok.Value;
import lombok.experimental.SuperBuilder;

import javax.annotation.Nullable;

@Value
@SuperBuilder(toBuilder = true)
@EqualsAndHashCode(callSuper = true)
public class DataSprayStore extends Store {
    /**
     * Customer ID that owns the data stream. If left blank or set to 'default', will try to extract currently logged in
     * customer.
     */
    @Nullable
    String customerId;
}
