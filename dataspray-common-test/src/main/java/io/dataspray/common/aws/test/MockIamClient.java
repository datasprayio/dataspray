package io.dataspray.common.aws.test;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import io.quarkus.arc.Priority;
import io.quarkus.arc.properties.IfBuildProperty;
import io.quarkus.test.junit.QuarkusTestProfile;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;
import org.mockito.Mockito;
import software.amazon.awssdk.services.iam.IamClient;
import software.amazon.awssdk.services.iam.model.CreateRoleRequest;
import software.amazon.awssdk.services.iam.model.CreateRoleResponse;

import javax.annotation.Nonnull;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Alternative;
import javax.inject.Singleton;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;

import static org.mockito.Mockito.when;

@ApplicationScoped
public class MockIamClient {

    @Alternative
    @Priority(1)
    @Singleton
    @IfBuildProperty(name = "aws.iam.mock.enable", stringValue = "true")
    public IamClient getIamClient() {
        IamClient mock = Mockito.mock(IamClient.class);

        ConcurrentMap<String, Role> roles = Maps.newConcurrentMap();

        when(mock.createRole(Mockito.<CreateRoleRequest>any()))
                .thenAnswer(invocation -> {
                    CreateRoleRequest request = invocation.getArgument(0, CreateRoleRequest.class);
                    Role role = new Role(request.roleName(), Lists.newArrayList());
                    roles.put(role.getName(), role);
                    return CreateRoleResponse.builder()
                            .role(role.toAwsRole()).build();
                });

        // TODO mock out all other used methods

        return mock;
    }

    @Value
    @Builder(toBuilder = true)
    @AllArgsConstructor
    private static class Role {
        @Nonnull
        String name;
        @Nonnull
        List<Policy> policies;

        public software.amazon.awssdk.services.iam.model.Role toAwsRole() {
            return software.amazon.awssdk.services.iam.model.Role.builder()
                    .roleName(getName())
                    .build();
        }
    }

    @Value
    @Builder(toBuilder = true)
    @AllArgsConstructor
    private static class Policy {
        @Nonnull
        String name;
    }

    public static class TestProfile implements QuarkusTestProfile {

        public Map<String, String> getConfigOverrides() {
            return ImmutableMap.of("aws.iam.mock.enable", "true");
        }
    }
}
