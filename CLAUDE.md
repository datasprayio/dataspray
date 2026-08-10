# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

DataSpray is a stream processing developer toolkit (work in progress, v0.0.1). Multi-module Maven
reactor: Java 21 (GraalVM/Quarkus) Lambda backends, TypeScript/React frontends in a pnpm workspace,
AWS CDK infrastructure, a `dst` CLI, and published runtime SDKs for user-written processors.

**Known issues and planned work are tracked in `ROADMAP.md`** (P0–P3 with file:line references).
Check it before filing/fixing "new" gaps — many are already catalogued.

## CRITICAL: Deployment side effects

- **Every push to `master` auto-deploys to staging** (`.github/workflows/deploy.yml`), with JVM
  (non-native) lambdas by default. Escape hatches: include `[skip deploy]` or `[release]` in the
  commit message. Production deploys are manual via `workflow_dispatch`.
- `mvn clean deploy` does **two** things: publishes all artifacts to OSSRH/npm (needs GPG + creds)
  AND deploys AWS infrastructure via the `aws-cdk-maven-plugin` in `dataspray-package`.
- The CDK synth/bootstrap execution hardcodes AWS profile `dataspray`
  (`dataspray-package/pom.xml`, `run-cdk-synth` execution); the deploy execution uses
  `${awsProfile}` set per profile.
- Deploy profiles (`production`, `staging`, `selfhost`) live in **`dataspray-package/pom.xml`**,
  not the parent POM — the parent's `staging`/`production` profiles are commented out. They set
  `stackEnv`, DNS domain/subdomain, and SES email.

## Environment Setup

Requires GraalVM 21, Maven 3.9.6, Node.js 22.17.0, pnpm 8.6.10. See `.sdkmanrc` and `.nvmrc`
(ignore the stale `.node-version`). Node + pnpm are auto-installed by the Maven build
(`frontend-maven-plugin`) into the repo-root `node/` directory.

Additional hard requirements for a clean `mvn install`:
- **Docker** — Testcontainers/Moto for tests, and container-based native builds on macOS
  (auto-activated `macos` profile).
- **ImageMagick 7, scour, ghostscript** — `dataspray-resources` shells out to them during
  `process-resources` (`dataspray-resources/src/main/sh/convert.sh`); fails hard on ImageMagick 6.

```bash
sdk env install   # Java/Maven via SDKMAN
nvm install       # Node.js via NVM
```

## Build Commands

```bash
mvn install                                    # full build with tests
mvn install -Pnative                           # native executables (requires GraalVM; Docker on macOS)
mvn install -pl dataspray-stream-control -am   # one module + its dependencies
mvn install -DskipTests

# Makefile wraps the canonical CI entry points:
make action-test-jvm / action-test-native      # what test.yml runs
make deploy-<module> / install-cli             # mvn shorthands

# Frontend dev (from a site module, e.g. dataspray-site-parent/dataspray-site-dashboard):
pnpm dev            # next dev on :3000; ?env=STAGING switches backend
pnpm build          # static export (NEXTJS_OUTPUT=export)
pnpm lint           # what Maven's test phase runs for JS modules
```

## Testing

```bash
mvn test                                                   # all tests
mvn test -pl dataspray-store -Dtest=CognitoUserStoreTest   # single class
mvn test -pl dataspray-store -Dtest=CognitoUserStoreTest#testMethodName
mvn verify -Pnative                                        # integration tests (native)
```

- Java tests use Moto via Testcontainers (`dataspray-common-test`, `MotoLifecycleManager`) —
  Docker required. Cross-org authorization paths are untestable through `AbstractLambdaTest`
  (it hardcodes the org context) — a known gap.
- **There are no frontend tests and no JS test runner configured.** Maven only runs `pnpm lint`
  for JS modules.

### Test Quality Standards

**CRITICAL: Write meaningful tests only. Do not inflate test counts with useless tests.**

- Every test must assert actual behavior: output/results, state changes, API responses, specific
  error messages. A test that only checks exit code 0 (e.g. `@Launch(value = {"query"},
  exitCode = 0)`) verifies nothing and should be deleted, not imitated.
- CLI tests must capture and verify stdout/stderr.
- Never create tests just to increase coverage numbers.

## Architecture

### Backend (Java/Quarkus, deployed as Lambdas)

- **dataspray-stream-control** — Control plane REST API. Resources: `ControlResource` (tasks,
  topics, topic schemas, S3 file browser, DynamoDB state get/set), `QueryResource` (Athena),
  `AuthNzResource` (API keys + Cognito auth flows), `OrganizationResource`, `HealthResource`.
- **dataspray-stream-ingest** — Ingest API. Single hot path `IngestResource.message()`: fan-out
  to SQS stream(s), Firehose→S3 batch, and per-customer DynamoDB, on virtual threads.
- **dataspray-authorizer** — API Gateway Lambda authorizer. Parses `Authorization: cognito <jwt>`
  or `apikey <key>`, emits an execute-api IAM policy scoped by organization, returns the usage-plan
  key for metering. Policies are cached by API GW (~5 min).
- **dataspray-store** — Data access AND the AWS control-plane layer: DynamoDB stores, plus
  `LambdaDeployerImpl` (customer Lambda deploy + IAM), `CognitoGroupOrganizationStore`
  (org = Cognito group), `FirehoseS3AthenaBatchStore` (Firehose/S3/Glue + schema inference),
  `AthenaQueryStore`, `SqsStreamStore`, `DynamoStateStore`.
- **dataspray-lambda-web** / **dataspray-lambda-web-test** — Shared Quarkus REST-on-Lambda glue
  (`AbstractResource` reads username/orgs from authorizer context; Gson message body; exception
  mapper) and test harness.
- **dataspray-common** / **dataspray-common-test** — AWS client CDI producers (overridable
  endpoints for Moto), `GsonUtil`, `DeployEnvironment`; Moto test lifecycle.

### API & Clients (`dataspray-api-parent/`)

- **dataspray-api** — OpenAPI specs in `src/main/openapi/`: roots `api.yaml` (Control) and
  `api-ingest.yaml` (Ingest) `$ref` shared path files (`paths-authnz.yaml`, `paths-organization.yaml`,
  `paths-control-task.yaml`, `paths-control-topic.yaml`, `paths-control-query.yaml`,
  `paths-control-state.yaml`, `paths-ingest.yaml`, `paths-health.yaml`). Tags: Health, AuthNZ,
  Organization, Ingest, Control, Query.
- **dataspray-client-java** — generated Java client + hand-written `DataSprayClient` facade.
- **dataspray-client-typescript** — npm package `dataspray-client` (`typescript-fetch`).
  Generated code in `src/main/typescript/client/` is **gitignored** — run the Maven build to
  regenerate before local `tsc`.
- Server stubs are generated in the consuming modules (`jaxrs-cxf` in
  `dataspray-stream-control`/`-ingest` poms), not in dataspray-api-parent.

### Runtime SDKs (`dataspray-runner-parent/`) — the user-facing processor libraries

- **dataspray-runner-java** — published `io.dataspray:dataspray-runner`. `Entrypoint` dispatches
  SQS batches / Function URL requests to user `stream()`/`web()`; `RawCoordinator` sends to the
  Ingest API; `DynamoStateManager` for state.
- **dataspray-runner-typescript** — published npm `dataspray-runner`, mirrors the Java API.
- Env-var names and the DynamoDB state key format (`pk`/`sk`/`ttlInEpochSec`) are string-coupled
  to `dataspray-store` (`LambdaDeployerImpl`, `DynamoStateStore`) — keep them in sync.

### CLI & Core

- **dataspray-cli** — the `dst` binary (Quarkus Picocli). Commands: init, generate, clean, install,
  deploy, upload-schema, schema get/update, activate, pause, resume, delete, list, status,
  query (execute/status/results/history/schema), env / env login. Config: INI at `~/.dst`,
  profiles, `DST_PROFILE` env var.
- **dataspray-core** — project definition + codegen. The project file is **`ds_project.yml`**
  (discovered by walking up from CWD; the sub-dir you ran from becomes the active task/processor).
  Parsing: SnakeYAML → Gson → Lombok `Definition` model; JSON Schema generated from the model at
  build time (victools). The checked-in `src/main/resources/schema/dataspray.schema.yaml` is
  orphaned/stale — do not edit it expecting effect.

#### Template system (read before touching `dataspray-core/src/main/resources/template/`)

- Engine is **JMustache**. File suffix determines behavior: `.template.mustache` (always
  regenerated, written read-only), `.sample.mustache` (only if absent), `.include.mustache`
  (partial), `.merge.mustache` (merged; only `.json`/`.gitignore` strategies exist). Path names
  themselves are Mustache; `{{_` means `{{/`.
- Resources can't be walked in native/fat jars, so a `*-tree.json` manifest is generated at build
  time — after adding/removing template files, rebuild `dataspray-core` or the file is invisible.
- Generated files are tracked via `.git/info/exclude` in the *user's* project
  (`GitExcludeFileTracker`), not a manifest.

### Frontend (`dataspray-site-parent/`, pnpm workspace)

- Workspace root `pnpm-workspace.yaml` + root `pnpm-lock.yaml` cover the 3 sites, the TS client,
  and the TS runner. Maven builds sites via `frontend-maven-plugin`: `pnpm install` →
  `pnpm run lint` (test phase) → `pnpm run build` static export (prepare-package) → assembly zip.
- **dataspray-site-dashboard** — Next.js 15 Pages Router + React 19 + Cloudscape. State: zustand
  (+ sessionStorage persist for auth tokens), SWR, Formik+yup. Auth goes through the backend
  `AuthNZApi` (not the AWS SDK); org selection from `cognito:groups` claim. Pages: auth flows,
  tasks, topics (+ S3 file browser), state browser, Athena query UI, API keys.
- **dataspray-site-landing** (MUI splash placeholder), **dataspray-site-docs** (nextra 2,
  Next 14 — mostly stubs plus Maven-generated OpenAPI markdown that IS committed).
- **CRITICAL: `@cloudscape-design/components` is exact-pinned to `3.0.1164` and patched**
  (`patches/@cloudscape-design__components@3.0.1164.patch`, declared in BOTH root `package.json`
  and `pnpm-workspace.yaml` — keep in sync). The patch adds `onChangeNative`/`onBlurNative` so
  Formik can bind to Cloudscape inputs. Upgrading the package without re-authoring the patch
  silently breaks every form.

### Infrastructure

- **dataspray-package** — AWS CDK stacks, entry `io.dataspray.cdk.DatasprayStack` (invoked by
  `aws-cdk-maven-plugin` with 7 positional args: env + 3 lambda zips + 3 site dirs). Stacks:
  DNS (Route53), 3× static site (S3+CloudFront via nextjs-export-cdk), SingleTable (DynamoDB),
  AuthNz (Cognito), Ingest (Lambda + Firehose→S3 ETL bucket), Control (Lambda + IAM + code-upload
  bucket), ApiStack (SpecRestApi from the OpenAPI spec + authorizer + usage plans). Glue/Athena
  resources are created at runtime by the control plane, not by CDK.
- **dataspray-remote-workspace** — orphaned module, NOT in the Maven reactor, largely
  unimplemented stubs. Don't build it; don't model new code on it.
- **dataspray-resources** — build-time logo/asset pipeline (see tool requirements above).

## Key Patterns

- **DI**: Quarkus CDI (`@Inject`, `@ApplicationScoped`); AWS clients come from producers in
  `dataspray-common` so Moto/test endpoints work — never construct AWS SDK clients/presigners
  directly.
- **JSON**: Gson via `GsonUtil.get()` for app data (Jackson exists in `dataspray-common` but Gson
  is the default choice).
- **DB**: platform data in one DynamoDB single-table (`io.dataspray:single-table` +
  `SingleTableProvider`); customer/task state lives in **per-organization tables**
  (`CustomerDynamoStoreImpl`) — two distinct models.
- **Tenancy/Auth**: organization = Cognito group; JWTs + DynamoDB-backed API keys; API Gateway
  usage plans keyed by authorizer-returned usage key. Resource methods must verify
  `getOrganizationNames().contains(organizationName)` — do not rely on the API GW policy alone.
- **Native**: GraalVM native-image for cold starts; `FunctionStack` auto-detects native vs JVM
  zip at synth time. CI currently deploys JVM by default.
- **Data**: Lombok (`@Value`, `@Builder`), Guava immutables.
