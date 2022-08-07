package io.dataspray.stream.web;

import io.dataspray.stream.resource.AdminResourceApi;
import io.dataspray.stream.resource.ControlResourceApi;
import lombok.extern.slf4j.Slf4j;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.server.ServerProperties;
import org.glassfish.jersey.server.filter.RolesAllowedDynamicFeature;

import javax.ws.rs.ApplicationPath;

@Slf4j
@ApplicationPath("/")
public class StreamApplication extends ResourceConfig {
    public StreamApplication() {
        super();

        // Serialize/Deserialize JSON API requests and responses
        register(GsonMessageBody.class);

        // Speed up start time
        property(ServerProperties.FEATURE_AUTO_DISCOVERY_DISABLE, true);
        property(ServerProperties.WADL_FEATURE_DISABLE, true);
        property(ServerProperties.METAINF_SERVICES_LOOKUP_DISABLE, true);
        property(ServerProperties.BV_FEATURE_DISABLE, true);
        property(ServerProperties.JSON_PROCESSING_FEATURE_DISABLE, true);
        property(ServerProperties.MOXY_JSON_FEATURE_DISABLE, true);

        // Add all resources here
        register(AdminResourceApi.class);
        register(ControlResourceApi.class);

        // TODO Authentication and roles
        register(RolesAllowedDynamicFeature.class);
        register(CorsFilter.class);

        // Other filters
        register(NoCacheFilter.class);
        register(ApiExceptionMapper.class);
    }
}
