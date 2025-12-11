# DataSpray Roadmap

This document tracks all known areas needing attention, incomplete features, and planned improvements for the DataSpray project.

**Current Status**: Work in Progress (v0.0.1)

## Priority Legend

- **P0 - Critical**: Blocking features or broken functionality
- **P1 - High**: Important for production readiness
- **P2 - Medium**: Feature gaps and improvements
- **P3 - Low**: Nice-to-have enhancements

---

## P0 - Critical

### Native Lambda Builds

**Status**: Not implemented
**Location**: `dataspray-lambda-web/README.md:193-200`

**Context**: GraalVM native image compilation for Lambda functions is planned but completely unimplemented. The README documents the intended workflow but all items are marked TODO.

**Impact**: Lambda cold start times remain high (several seconds with JVM). Production deployments suffer from poor initial response times, especially for infrequently invoked functions.

**Required Work**:
1. Implement `-Pnative` Maven profile activation
2. Configure container-based native image builds for cross-compilation
3. Set up integration tests against native executables
4. Deploy Lambda using custom runtime (provided.al2)

**Related Files**:
- `pom.xml` (parent) - Native profile configuration at line 891-917
- `dataspray-stream-control/pom.xml` - Quarkus native build setup
- `dataspray-stream-ingest/pom.xml` - Quarkus native build setup

---

### Subdomain Support for SSR Sites

**Status**: Throws NotImplementedException
**Location**: `dataspray-package/src/main/java/io/dataspray/cdk/site/SsrNextSiteStack.java:53`

**Context**: Server-side rendered Next.js sites cannot be deployed to subdomains. The code explicitly throws an exception when a subdomain is configured.

**Impact**: Cannot deploy staging environments or multi-tenant setups to subdomains (e.g., `staging.dataspray.io`). Forces all environments to use separate domains.

**Required Work**:
1. Create Route53 subdomain records
2. Handle ACM certificate validation for subdomains
3. Configure CloudFront distribution for subdomain routing
4. Update OpenNext CDK construct configuration

**Related Files**:
- `dataspray-package/src/main/java/io/dataspray/cdk/site/SsgNextSiteStack.java` - Static site version (may have similar issues)
- `dataspray-package/src/main/java/io/dataspray/cdk/dns/DnsStack.java` - DNS infrastructure

---

### Remote Workspace Feature

**Status**: Multiple components unimplemented
**Location**: `dataspray-remote-workspace/src/main/java/io/dataspray/devenv/DevEnvManagerImpl.java:35-48`

**Context**: The remote development workspace feature is designed to provide cloud-based development environments but is largely unimplemented.

**Impact**: Users cannot spin up remote development environments. The entire `dataspray-remote-workspace` module is non-functional.

**Required Work**:
1. **EFS Storage Setup** (line 38) - Create and configure EFS filesystem for persistent storage
2. **Lambda Container Image** (line 35) - Build and push container image for Lambda
3. **Function Endpoint Creation** (line 41) - Set up Lambda function URL or API Gateway endpoint
4. **CloudFront Updates** (line 44) - Configure CDN distribution for the workspace
5. **Environment Lifecycle Management** - Start, stop, snapshot operations

**Related Files**:
- `dataspray-remote-workspace/src/main/java/io/dataspray/devenv/DevEnvRunnerStack.java` - CDK stack
- `dataspray-remote-workspace/src/main/java/io/dataspray/devenv/DevEnvImageRepoStack.java` - ECR repository stack
- `dataspray-remote-workspace/src/main/container/Dockerfile` - Container definition
- `dataspray-remote-workspace/Makefile` - Build targets (also has TODOs)

---

## P1 - High Priority

### CloudFormation Template Distribution

**Status**: Not available
**Location**: `README.md:28`

**Context**: The README mentions CloudFormation template deployment for self-hosting but it's marked as "Not yet available".

**Impact**: Users cannot easily self-host DataSpray using pre-built CloudFormation templates. Must build from sources.

**Required Work**:
1. Package CDK output as CloudFormation templates
2. Parameterize templates for customization
3. Create deployment documentation
4. Publish templates to S3 or GitHub releases

---

### API Rate Limiting

**Status**: Not configured
**Location**: `dataspray-package/src/main/java/io/dataspray/cdk/api/ApiStack.java:257`

**Context**: API Gateway method-level throttling is planned but not implemented. Only default account-level throttling applies.

**Impact**: Cannot protect individual endpoints from abuse. High-traffic endpoints may consume quota needed by critical operations.

**Required Work**:
1. Define throttling limits per API method
2. Configure API Gateway usage plans
3. Implement per-customer rate limiting if needed
4. Add monitoring/alerting for throttled requests

---

### Lambda Source Function Validation

**Status**: Missing IAM condition
**Location**: `dataspray-authorizer/src/main/java/io/dataspray/authorizer/Authorizer.java:208`

**Context**: The authorizer generates IAM policies but doesn't include `lambda:SourceFunctionArn` condition. This would restrict API access to specific Lambda sources.

**Impact**: Security gap - API endpoints not restricted to intended Lambda function sources. Could allow unintended access paths.

**Required Work**:
1. Add `Condition` block to generated IAM statements
2. Include `lambda:SourceFunctionArn` with appropriate ARN patterns
3. Test with cross-account and same-account scenarios

---

## P2 - Medium Priority

### Runtime Inference from Project Files

**Status**: Manual configuration required
**Location**: `dataspray-core/src/main/java/io/dataspray/core/StreamRuntimeImpl.java:131`

**Context**: The processor runtime (Java, TypeScript, etc.) must be manually specified in the project definition instead of being auto-detected.

**Impact**: User experience friction - developers must manually specify what could be inferred. Potential for configuration drift.

**Required Work**:
1. Parse `pom.xml` to detect Java projects
2. Parse `package.json` to detect Node.js/TypeScript projects
3. Check `.nvmrc`/`.sdkmanrc` for version information
4. Fall back to manual specification if detection fails

---

### TypeScript Lambda Handler Path

**Status**: Needs verification
**Location**: `dataspray-core/src/main/java/io/dataspray/core/StreamRuntimeImpl.java:137`

**Context**: Lambda handler for TypeScript processors is hardcoded to `index.js`. This may not align with AWS Lambda's expected handler format.

**Impact**: TypeScript Lambda deployments may fail or behave unexpectedly if handler path is incorrect.

**Required Work**:
1. Verify correct handler format against AWS Lambda documentation
2. Support configurable handler paths
3. Handle compiled vs bundled TypeScript scenarios

---

### Stream Processing Target Extensibility (Samza, Flink)

**Status**: Only DATASPRAY implemented
**Location**: `dataspray-core/src/main/java/io/dataspray/core/definition/model/Processor.java:58-59`

**Context**: The processor definition supports a `target` field with DATASPRAY as the only option. SAMZA and FLINK are mentioned in comments as future targets.

**Impact**: Users locked into DataSpray's processing model. Cannot migrate to or integrate with Samza or Flink ecosystems.

**Required Work**:
1. Define processor adapter interface
2. Implement Samza target adapter
3. Implement Flink target adapter
4. Add deployment mechanisms for each target

---

### Missing Data Schema Formats

**Status**: Limited format support
**Location**: `dataspray-core/src/main/resources/schema/dataspray.schema.yaml:68`

**Context**: The `dataFormat` enum supports JSON, Avro, and Protobuf. Several other formats are mentioned but not implemented.

**Missing Formats**:
- XML
- Thrift
- Cap'n Proto
- FlatBuffers
- SBE (Simple Binary Encoding)

**Impact**: Users with existing data in unsupported formats cannot use DataSpray without conversion.

**Required Work**:
1. Add format to schema enum
2. Implement serialization/deserialization for each format
3. Add code generation templates for each format
4. Test with sample data

---

### Missing Authentication Methods

**Status**: Limited auth support
**Location**: `dataspray-core/src/main/resources/schema/dataspray.schema.yaml:110`

**Context**: Authentication for stream sources/sinks is limited. Several common methods are not supported.

**Missing Methods**:
- SASL (various mechanisms)
- mTLS (mutual TLS)
- HTTP Basic Auth

**Impact**: Cannot connect to data sources requiring these authentication methods (e.g., Kafka with SASL).

**Required Work**:
1. Add auth methods to schema
2. Implement credential handling for each method
3. Secure credential storage
4. Test against real systems

---

### Dashboard Organization Form Error Handling

**Status**: Incomplete
**Location**: `dataspray-site-parent/dataspray-site-dashboard/src/pages/organization/create.tsx:65`

**Context**: The organization creation form's `onSubmit` handler has a TODO for using `setError` to display validation errors.

**Impact**: Users don't see form validation errors. Poor UX for organization creation flow.

**Required Work**:
1. Implement `setError` calls for validation failures
2. Display error messages in form UI
3. Handle API error responses
4. Add success/failure notifications

---

## P3 - Low Priority / Infrastructure

### Docker ARM64 Architecture

**Status**: TODO
**Location**: `dataspray-remote-workspace/src/main/container/Dockerfile:1`

**Context**: Container is built for x86_64. ARM64 (Graviton) support would improve cost efficiency on AWS.

**Required Work**:
1. Update base image to multi-arch
2. Test build on ARM64
3. Update CI to build both architectures

---

### ECS Deployment Infrastructure

**Status**: Not implemented
**Location**: `dataspray-remote-workspace/Makefile:4-5`

**Context**: Makefile has TODOs for ECS repository deployment via CDK and image push to ECR.

**Required Work**:
1. Create CDK stack for ECR repository
2. Add `make` targets for image build and push
3. Add ECS service deployment

---

### Generated Code Placeholders

**Status**: Template limitation
**Location**: `dataspray-common/src/main/openapi/template/jaxrs-cxf/apiServiceImpl.mustache:77`

**Context**: OpenAPI-generated service implementations contain `// TODO: Implement...` placeholder comments that remain in generated code.

**Impact**: Minor - serves as reminder to implement generated stubs. No functional impact.

---

## Testing Gaps

### MFA/Authentication Tests

**Status**: Disabled
**Location**: `dataspray-stream-control/src/test/java/io/dataspray/stream/control/AuthNzBase.java:194-211`

**Context**: Several authentication tests are disabled due to Moto (AWS mock) limitations:
- SOFTWARE_TOKEN_MFA challenge handling
- Email alias attribute tests
- TOTP submission (Moto bug #7136)

**Impact**: Auth flows not tested in CI. Potential for regressions in MFA functionality.

**Required Work**:
1. Monitor Moto releases for bug fixes
2. Consider alternative mocking approaches (LocalStack)
3. Add integration tests against real Cognito in staging

---

### CDK Infrastructure Tests

**Status**: Multiple stacks skipped
**Location**: `dataspray-package/src/test/java/io/dataspray/cdk/DatasprayStackTest.java:119-142`

**Context**: Several CDK stack tests are skipped due to missing Moto support:
- AuthN/Z stack
- API Gateway stack
- Dashboard, docs, landing sites
- Control and ingest services

**Impact**: Infrastructure changes not validated in tests. Risk of deployment failures.

**Required Work**:
1. Evaluate LocalStack for better API Gateway support
2. Add snapshot testing for CloudFormation output
3. Create integration test environment

---

### CLI Test Coverage

**Status**: Minimal
**Location**: `dataspray-cli/src/test/java/io/dataspray/cli/CliTest.java:36`

**Context**: CLI tests only verify exit code. No comprehensive command testing per Quarkus guide recommendations.

**Impact**: CLI commands not tested. Potential for broken user-facing commands.

**Required Work**:
1. Add command-specific test cases
2. Test argument parsing
3. Test output formatting
4. Mock external dependencies (API calls)

**Reference**: https://quarkus.io/guides/command-mode-reference#testing-command-mode-applications

---

## Recently Completed

_This section tracks items that have been addressed. Move items here when completed._

---

## Contributing

To work on any of these items:
1. Check if there's an existing GitHub issue
2. Comment on the issue to claim it
3. Create a branch from `master`
4. Submit a PR referencing the issue

For questions, reach out via GitHub issues.
