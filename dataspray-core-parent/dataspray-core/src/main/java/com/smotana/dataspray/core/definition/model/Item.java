package com.smotana.dataspray.core.definition.model;

import com.jcabi.aspects.Cacheable;
import com.smotana.dataspray.core.util.StringUtil;
import lombok.Value;
import lombok.experimental.NonFinal;
import lombok.experimental.SuperBuilder;

import javax.annotation.Nonnull;

import static com.smotana.dataspray.core.definition.model.Definition.CACHEABLE_METHODS_LIFETIME_IN_MIN;

@Value
@SuperBuilder(toBuilder = true)
@NonFinal
public class Item {
    @Nonnull
    String name;

    @Cacheable(lifetime = CACHEABLE_METHODS_LIFETIME_IN_MIN)
    public String getNameCamelUpper() {
        return StringUtil.camelCase(name, true);
    }

    @Cacheable(lifetime = CACHEABLE_METHODS_LIFETIME_IN_MIN)
    public String getNameCamelLower() {
        return StringUtil.camelCase(name, false);
    }
}
