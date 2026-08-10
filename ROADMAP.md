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

**Status**: Removed 2026-08-10
**Location**: was `dataspray-remote-workspace/` (recoverable from git history)

**Context**: The module was never part of the Maven reactor, untouched since 2024-12, and its
manager/Python implementation were non-functional stubs. Deleted during the 2026-08 review to
avoid confusion; the CDK stacks (EFS + Lambda container + function URL) were the most complete
part and can be resurrected from history if the feature is revived.

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

## New Items (from 2026-08-10 full review)

### P1 - GitHub OIDC for deploys
`deploy.yml` uses long-lived static AWS keys. Set up an OIDC provider + deploy role in AWS, then
switch to `aws-actions/configure-aws-credentials` role assumption. Requires AWS-side changes.

### P1 - Decouple artifact publishing from infra deploys
`mvn clean deploy` both publishes every artifact to OSSRH/npm and deploys infrastructure; every
master push does both. Split into separate lifecycle steps or workflows.

### P1 - Cognito hardening decisions
`AuthNzStack`: advanced security OFF (costs money to enable - needs a call), account recovery NONE,
24h access tokens, ADMIN_USER_PASSWORD auth flow (passwords transit the control lambda). Also the
dashboard persists the refresh token in sessionStorage. Revisit deliberately.

### P2 - Native lambda architecture from build host
`FunctionStack.detectNativeArch()` picks the deployed Lambda architecture from the build machine's
`os.arch` - an ARM Mac vs x86 CI runner silently changes the artifact. Make it explicit.

### P2 - Frontend test harness
Zero JS tests and no runner configured (Maven only runs `pnpm lint`). Add vitest + RTL or
Playwright, starting with `errorUtil`, auth flows, and the Athena query page.

### P2 - CDK template assertions
`DatasprayStackTest` deploys only dns + singletable against Moto and asserts only stack names.
Add `Template.fromStack(...)` assertions for the IAM-heavy stacks.

### P2 - CLI parity with API
The CLI covers ~17 of 42 API operations. Missing: state (list/get/upsert/delete), topics CRUD,
S3 file browsing, auth/apikey and organization commands - all already implemented server-side.

### P2 - Schema inference quality
All fields are typed `string` and sampling reads the lexicographically-first (oldest) S3 keys.
Infer real types and sample recent partitions. `docs/schema-inference-design.md` status label is
also stale relative to the commit that added it.

### P3 - Predictable API Gateway key values
Usage-plan `ApiKey` values are deterministic strings. Safe today only because the key source is
`AUTHORIZER`; randomize before ever switching key source to `HEADER`.

### P3 - Authorizer policy cache
API Gateway caches authorizer policies for 5 minutes; org membership/key revocation lags by up to
that. Reduce TTL or accept and document.

### P3 - Publish dataspray-runner 0.0.9 to npm
Local TS runner version is 0.0.9 but npm has 0.0.8; the generated-project template pins 0.0.8 on
purpose. After publishing, bump `package.json.merge.mustache`.

---

## Recently Completed

_This section tracks items that have been addressed. Move items here when completed._

### 2026-08-10 full review fixes
- Tenant isolation: organization name validation (creation + OpenAPI patterns), org-membership
  checks on every org-scoped endpoint, Athena cross-database rejection + SQL blocklist fixes,
  S3 download prefix guard, owner-scoped API key revocation, author-only rate limit adjustment
- State store: UpdateItem-based upsert, reserved attribute rejection, type-correct unmarshalling,
  paged listState, missing DynamoDB data-action IAM grant for the control lambda
- Org role IAM bug cluster fixed (invalid policy JSON, missing bucket in ARN, role ARN vs name,
  Cognito federation trust policy)
- CDK: Athena scoped to workgroup, CreateEventSourceMapping conditioned on customer functions,
  permission boundary enforced via IAM condition, usage-plan throttle tiers corrected, staging
  CORS locked down, 1-month log retention, S3 encryption + enforceSSL
- CI: test.yml gating inverted-logic fix; macos-skipTests/skipITs activation fix; dataspray-package
  version/AWS-profile hardcodes removed
- Dashboard: Athena query polling + pagination, real error message parsing, S3 browser folder
  navigation, auth refresh timer cleanup, home page, dead code/deps removal
- CLI: parallel deploy executor fix, env/profile config bugs, bounded query polling, meaningful
  CliTest; core: dead code + orphaned schema removal; TS runner error handling
- Tests: DynamoStateStore, SQL validation, S3 prefix guard, org name validation (15 new tests)
- Removed dead `dataspray-remote-workspace` module (was not in the reactor; stubs only)

---

## Contributing

To work on any of these items:
1. Check if there's an existing GitHub issue
2. Comment on the issue to claim it
3. Create a branch from `master`
4. Submit a PR referencing the issue

For questions, reach out via GitHub issues.
