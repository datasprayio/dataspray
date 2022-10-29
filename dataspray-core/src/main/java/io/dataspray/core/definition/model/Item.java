package io.dataspray.core.definition.model;

import com.jcabi.aspects.Cacheable;
import io.dataspray.common.StringUtil;
import lombok.Value;
import lombok.experimental.NonFinal;
import lombok.experimental.SuperBuilder;

import javax.annotation.Nonnull;

@Value
@SuperBuilder(toBuilder = true)
@NonFinal
public class Item {
    @Nonnull
    String name;

    @Cacheable(lifetime = Definition.CACHEABLE_METHODS_LIFETIME_IN_MIN)
    public String getNameDir() {
        return StringUtil.dirName(name);
    }

    public String getTaskId() {
        return getNameDir();
    }

    @Cacheable(lifetime = Definition.CACHEABLE_METHODS_LIFETIME_IN_MIN)
    public String getNameCamelUpper() {
        return StringUtil.camelCase(name, true);
    }

    @Cacheable(lifetime = Definition.CACHEABLE_METHODS_LIFETIME_IN_MIN)
    public String getNameCamelLower() {
        return StringUtil.camelCase(name, false);
    }
}
