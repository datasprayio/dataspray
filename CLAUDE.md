# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

DataSpray is a stream processing developer toolkit. It's a multi-module Maven project with Java 21 (GraalVM) backend services, TypeScript/React frontends, and AWS CDK infrastructure deployed to AWS Lambda.

## Build Commands

```bash
# Full build with tests
mvn install

# Build with native executables (requires GraalVM)
mvn install -Pnative

# Build specific module with dependencies
mvn install -pl dataspray-stream-control -am

# Skip tests
mvn install -DskipTests

# Deploy to staging/production
mvn clean deploy -Pstaging
mvn clean deploy -Pproduction

# Self-hosted deployment
mvn clean deploy -Pselfhost -DdnsDomain=example.com
```

## Testing

```bash
# Run all tests
mvn test

# Run single test class
mvn test -pl dataspray-store -Dtest=CognitoUserStoreTest

# Run single test method
mvn test -pl dataspray-store -Dtest=CognitoUserStoreTest#testMethodName

# Integration tests (require native profile)
mvn verify -Pnative
```

### Test Quality Standards

**CRITICAL: Write meaningful tests only. Do not inflate test counts with useless tests.**

❌ **BAD - Useless test that verifies nothing:**
```java
@Test
@Launch(value = {"query"}, exitCode = 0)
public void testQueryHelp() throws Exception {
    // Should display help message
}
```
This test only checks exit code 0, doesn't verify output, behavior, or correctness. It's test count inflation.

✅ **GOOD - Tests that verify actual behavior:**
- Assert on actual output/results (captured stdout, returned values, etc.)
- Verify state changes (database records, file contents, API responses)
- Test error conditions with specific error messages
- Validate business logic with concrete inputs and expected outputs

**Requirements:**
1. Every test must assert something meaningful
2. Tests without assertions that verify behavior should be deleted
3. CLI tests must capture and verify output when testing help/formatting
4. Never create tests just to increase coverage numbers
5. Test real functionality, edge cases, and error handling

## Environment Setup

Requires GraalVM 21, Maven 3.9.6, Node.js 22.17.0. See `.sdkmanrc` and `.nvmrc`.

```bash
# Java/Maven via SDKMAN
sdk env install

# Node.js via NVM
nvm install
```

## Architecture

### Backend (Java/Quarkus)
- **dataspray-stream-control** - Control plane REST API (Lambda). Manages tasks, users, API keys
- **dataspray-stream-ingest** - Ingest API (Lambda). Handles data ingestion to streams
- **dataspray-authorizer** - API Gateway Lambda authorizer for JWT/API key auth
- **dataspray-store** - Data access layer using DynamoDB (single-table design via `single-table` library)
- **dataspray-lambda-web** - Shared Lambda web utilities (Quarkus REST)

### API & Clients
- **dataspray-api** - OpenAPI specs in `src/main/openapi/`. Control API (`api.yaml`) and Ingest API (`api-ingest.yaml`)
- **dataspray-client-java** - Generated Java client
- **dataspray-client-typescript** - Generated TypeScript client (workspace: `dataspray-client`)

### CLI & Core
- **dataspray-cli** - CLI tool (`dst` binary) using Quarkus Picocli
- **dataspray-core** - Project definition parsing, template generation, code scaffolding

### Frontend (Next.js/React)
- **dataspray-site-dashboard** - React dashboard with Cloudscape Design components
- **dataspray-site-landing** - Landing page
- **dataspray-site-docs** - Documentation site

### Infrastructure
- **dataspray-package** - AWS CDK stacks for deployment. Entry point: `io.dataspray.cdk.DatasprayStack`

## Code Generation

OpenAPI specs generate both server stubs and clients:
- Server stubs: `openapi-generator-maven-plugin` with `jaxrs-cxf` generator
- TypeScript client: Generated in `dataspray-client-typescript`
- Java client: Generated in `dataspray-client-java`

## Key Patterns

- **DI**: Quarkus CDI (`@Inject`, `@ApplicationScoped`)
- **Data**: Lombok (`@Value`, `@Builder`), Gson for JSON
- **DB**: DynamoDB single-table pattern via `io.dataspray:single-table`
- **Auth**: Cognito JWTs, API keys stored in DynamoDB
- **Native**: GraalVM native-image for Lambda cold start optimization
