package io.dataspray.common.json;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Dependent;

@ApplicationScoped
public class JacksonUtil {

    @Dependent
    ObjectMapper getInstance() {
        return new ObjectMapper()
                .registerModule(new Jdk8Module().configureAbsentsAsNulls(true))
                .findAndRegisterModules();
    }
}
