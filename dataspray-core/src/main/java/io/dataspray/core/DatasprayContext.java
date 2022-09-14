package io.dataspray.core;

import com.google.common.collect.ImmutableMap;
import com.samskivert.mustache.Mustache.CustomContext;

public class DatasprayContext implements CustomContext {

    private final ImmutableMap<String, Object> data;

    public DatasprayContext(ImmutableMap<String, Object> data) {
        this.data = data;
    }

    @Override
    public Object get(String name) throws Exception {
        return data.get(name);
    }

    public ImmutableMap<String, Object> getData() {
        return data;
    }
}
