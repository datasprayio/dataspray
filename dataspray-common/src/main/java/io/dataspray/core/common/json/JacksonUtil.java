package io.dataspray.core.common.json;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.Dependent;

@ApplicationScoped
public class JacksonUtil {

    @Dependent
    ObjectMapper getInstance() {
        return new ObjectMapper()
                .registerModule(new Jdk8Module().configureAbsentsAsNulls(true))
                .findAndRegisterModules();
    }
}
