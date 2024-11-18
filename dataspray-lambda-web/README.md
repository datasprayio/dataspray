# OpenApi serverless backend stack

This project allows you to deploy an OpenApi spec with a Quarkus Lambda application easily with AWS CDK.

## Howto

### 1. Import library

```xml
<dependency>
    <groupId>io.dataspray</groupId>
    <artifactId>dataspray-base-lambda</artifactId>
    <version>${project.version}</version>
</dependency>
```

### 2. Generate OpenApi resources

```xml
<plugin>
    <groupId>org.openapitools</groupId>
    <artifactId>openapi-generator-maven-plugin</artifactId>
    <version>4.1.3</version>
    <executions>
        <execution>
            <id>generate-server</id>
            <goals>
                <goal>generate</goal>
            </goals>
            <configuration>
                <inputSpec>${project.build.directory}/openapi/api-ingest.yaml</inputSpec>
                <generatorName>jaxrs-cxf</generatorName>
                <ignoreFileOverride>
                    ${project.build.directory}/openapi/template/jaxrs-cxf/.openapi-generator-ignore
                </ignoreFileOverride>
                <templateDirectory>${project.build.directory}/openapi/template/jaxrs-cxf
                </templateDirectory>
                <typeMappings>OffsetDateTime=Instant</typeMappings>
                <importMappings>java.time.OffsetDateTime=java.time.Instant</importMappings>
                <additionalProperties>
                    <additionalProperty>java8=true</additionalProperty>
                    <additionalProperty>modelPackage=io.dataspray.stream.server.model</additionalProperty>
                    <additionalProperty>apiPackage=io.dataspray.stream.server</additionalProperty>
                    <additionalProperty>invokerPackage=io.dataspray.stream.server</additionalProperty>
                    <additionalProperty>groupId=io.dataspray</additionalProperty>
                    <additionalProperty>artifactId=dataspray</additionalProperty>
                    <additionalProperty>dateLibrary=java8</additionalProperty>
                    <additionalProperty>disableHtmlEscaping=true</additionalProperty>
                    <additionalProperty>generateApiTests=false</additionalProperty>
                    <additionalProperty>generateApiDocumentation=false</additionalProperty>
                    <additionalProperty>generateModelTests=false</additionalProperty>
                    <additionalProperty>generateModelDocumentation=false</additionalProperty>
                    <additionalProperty>generateSupportingFiles=false</additionalProperty>
                    <additionalProperty>hideGenerationTimestamp=true</additionalProperty>
                    <additionalProperty>addConsumesProducesJson=true</additionalProperty>
                    <additionalProperty>useBeanValidation=true</additionalProperty>
                    <additionalProperty>removeEnumValuePrefix=false</additionalProperty>
                    <additionalProperty>sourceFolder=src/main/java</additionalProperty>
                </additionalProperties>
                <output>${project.build.directory}/generated-sources/server</output>
                <configOptions>
                    <sourceFolder>src/main/java</sourceFolder>
                </configOptions>
            </configuration>
        </execution>
    </executions>
</plugin>
```

### 3. Implement API

Here is the API implementation

```java
@Slf4j
@ApplicationScoped
public class PingResource extends AbstractResource implements PingApi {

    @Override
    public void ping() {
        log.trace("Received ping");
    }
}
```

Here are the tests

`PingTest.java`:

```java
@QuarkusTest
public class PingTest {

    @Test
    public void testPing() {
        RestAssured.when().get("/api/ping")
                .then().statusCode(200);
    }
}
```

An integration test that runs unit tests against the final function code. Usually it is important to ensure code
contains all required classes.

`PingIT.java`:

```java
@QuarkusIntegrationTest
public class PingIT extends PingTest { }
```

### 4. Build Quarkus app

```xml
<plugins>
    <plugin>
        <groupId>io.quarkus</groupId>
        <artifactId>quarkus-maven-plugin</artifactId>
        <executions>
            <execution>
                <goals>
                    <goal>build</goal>
                </goals>
            </execution>
        </executions>
    </plugin>
</plugins>
```

### 5. Bundle Lambda function zip file

```xml
<plugin>
    <groupId>org.codehaus.mojo</groupId>
    <artifactId>build-helper-maven-plugin</artifactId>
    <executions>
        <execution>
            <id>attach-artifacts</id>
            <phase>package</phase>
            <goals>
                <goal>attach-artifact</goal>
            </goals>
            <configuration>
                <artifacts>
                    <artifact>
                        <file>${project.build.directory}/function.zip</file>
                        <type>zip</type>
                        <classifier>lambda</classifier>
                    </artifact>
                </artifacts>
            </configuration>
        </execution>
    </executions>
</plugin>
```

### 6. Deploy using CDK

Create a CDK endpoint to create Lambda

```java
public static void main(String... args) {
    App app = new App();
    new LambdaBaseStack(app, Options.builder()
            .openapiYamlPath("target/openapi/api-ingest.yaml")
            .build());
    app.synth();
}
```

Call CDK maven plugin

```xml
<plugin>
    <groupId>io.dataspray</groupId>
    <artifactId>aws-cdk-maven-plugin</artifactId>
    <executions>
        <execution>
            <id>run-cdk</id>
            <goals>
                <goal>synth</goal>
                <goal>bootstrap</goal>
                <goal>deploy</goal>
            </goals>
            <configuration>
                <app>my.app.MyStack</app>
            </configuration>
        </execution>
    </executions>
</plugin>
```

## Native builds

_TODO:_

- _Triggered using a "native" profile_
- _Build native container (Either GH Actions on linux or docker image)_
- _Integration tests using container_
- _Deploy Lambda custom runtime_
