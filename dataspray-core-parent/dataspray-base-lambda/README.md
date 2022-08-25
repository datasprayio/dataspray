# OpenApi serverless backend stack

This project allows you to deploy an OpenApi spec with a Quarkus Lambda application easily with AWS CDK.

## Howto

### 1. Import library

```xml
<dependency>
    <groupId>io.dataspray.core</groupId>
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
                <additionalProperties>java8=true
                    ,modelPackage=io.dataspray.stream.server.model
                    ,apiPackage=io.dataspray.stream.server
                    ,invokerPackage=io.dataspray.stream.server
                    ,groupId=io.dataspray
                    ,artifactId=dataspray
                    ,dateLibrary=java8
                    ,disableHtmlEscaping=true
                    ,generateApiTests=false
                    ,generateApiDocumentation=false
                    ,generateModelTests=false
                    ,generateModelDocumentation=false
                    ,generateSupportingFiles=false
                    ,hideGenerationTimestamp=true
                    ,addConsumesProducesJson=true
                    ,useBeanValidation=true
                    ,removeEnumValuePrefix=false
                    ,sourceFolder=src/main/java
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

### 4. Build Quarkus app

```xml
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
```

### 5. Deploy using CDK

Create a CDK endpoint to create Lambda

```java
public static void main(String[] args) {
    new LambdaBaseStack(Options.builder()
            .openapiYamlPath("target/openapi/api-ingest.yaml")
            .build());
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