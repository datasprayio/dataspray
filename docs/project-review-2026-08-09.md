# DataSpray Full Project Review — 2026-08-09

Scope: entire repo at `d8d4b58` (master). Four parallel deep-dives: Java backend, core/CLI/API,
frontend, infrastructure/build/CI. Findings are prioritized; file:line references throughout.
Existing `ROADMAP.md` items are not repeated here unless the reality is worse than documented.

---

## 1. Security — fix before anything else

### 1.1 Cross-tenant access via unvalidated organization names (HIGH)
- `OrganizationResource.createOrganization` passes the caller-supplied name straight to Cognito
  (`CognitoGroupOrganizationStore.java:96-101`). Cognito group names allow punctuation/symbols.
- The authorizer *strips* disallowed characters when building the IAM policy ARN
  (`Authorizer.java:218-220` `sanitizeArnInjection`): an org named `acme*` or `acme!` sanitizes
  to `acme`, granting `.../v1/organization/acme/*`.
- Compounding it, most pre-existing `ControlResource` endpoints (deploy/activate/pause/resume/
  delete/status, topics CRUD, uploadCode, schema get/update), `AuthNzResource` API-key endpoints,
  and `OrganizationResource.invite/rateLimitAdjust` never check
  `getOrganizationNames().contains(organizationName)` — only the newer query/state/files
  endpoints do. Together: cross-tenant task/topic/API-key access.
- The raw org name also flows unescaped into SQS queue names, DynamoDB table names, Glue DB
  names, IAM role names, and S3 prefixes.
- **Fix**: strict org-name pattern (mirror `UserStore.USERNAME_VALIDATION`) at creation + OpenAPI
  `pattern` on every `organizationName` param, and add the membership check to every resource
  method (defense in depth over the API GW policy).

### 1.2 Athena tenant isolation is not real (HIGH)
- Control lambda's Athena grant is `Resource: "*"` (`ControlFunctionStack.java:241-250`); Glue
  grant wildcards all customer DBs; S3 grant covers the whole ETL bucket.
- Only scoping is `QueryExecutionContext.database` (`AthenaQueryStore.java:155-157`) — a
  *default*, not a restriction. A fully-qualified `SELECT * FROM "other-org-db"."table"` crosses
  tenants.
- The keyword blocklist (`AthenaQueryStore.java:333-349`) is bypassable: regex lacks `DOTALL`, so
  any multi-line query with the keyword on line 2+ passes (`:344`); no comment/string-literal
  handling. `DATABASE_PATTERN` (`:90-93`) was written to extract referenced DBs and is never used.
- **Fix options**: per-org Athena WorkGroups + result locations, IAM session policies scoped
  per-request, or Lake Formation grants. Minimum: use `DATABASE_PATTERN`-style parsing to reject
  fully-qualified cross-db references and fix the regex flags.

### 1.3 State API can corrupt data and touch arbitrary items (HIGH)
- `upsertState` is a full `PutItem` (`DynamoStateStore.java:167-171`) — editing one field from
  the dashboard deletes every attribute the request omitted (the runner uses incremental
  `UpdateItem`).
- User attributes are written *after* `pk`/`sk`/TTL (`:163-165`): an attribute literally named
  `pk`/`sk`/`ttlInEpochSec` overwrites the key/TTL → write to an arbitrary item in the org table.
  Spec allows it (`paths-control-state.yaml` `additionalProperties: true`).
- `unmarshalValue` (`:264-287`) uses `attr.m() != null`, true for every SDK v2 attribute →
  binary/list/null values silently become `{}`. Use `attr.type()`/`hasM()`.
- **Also broken at the IAM layer**: the state feature shipped with zero CDK changes; the control
  lambda has only control-plane actions on customer tables
  (`ControlFunctionStack.java:203-215` — Create/Describe/UpdateTable), no
  `GetItem/PutItem/Query/DeleteItem/Scan` → likely AccessDenied at runtime in deployed envs.

### 1.4 Medium-severity security items
- S3 download guard prefix confusion: `basePrefix` lacks trailing `/`
  (`FirehoseS3AthenaBatchStore.java:427-435`) — topic `foo` authorizes `topic=foobar/...`.
- `S3Presigner.create()` bypasses the CDI producer (`:441-442`) — ignores configured
  endpoint/credentials (can't work against Moto; may sign wrong in Lambda).
- `rateLimitAdjust` (`OrganizationResource.java:107-121`): any member self-upgrades to 100 RPS —
  no admin/role check. `revokeApiKey` (`AuthNzResource.java:124-130`): any member revokes any
  other member's key.
- Secrets in logs: full access token on verify failure (`CognitoJwtVerifierImpl.java:72`); raw
  API key in unauthorized reason (`Authorizer.java:121,163`).
- Customer-lambda IAM permission boundary is created but never *enforced* via
  `iam:PermissionsBoundary` condition (`ControlFunctionStack.java:75-117`, `:289-301`) —
  privilege-escalation path if the deployer ever skips attaching it.
- `lambda:CreateEventSourceMapping` on `Resource: "*"` (`ControlFunctionStack.java:265-273`).
- CORS `*` on TEST **and STAGING** (`ApiFunctionStack.java:71-79`).
- Cognito: advanced security OFF, account recovery NONE, 24 h access tokens, ADMIN_USER_PASSWORD
  auth flow (`AuthNzStack.java:90-135`). Authorizer TTL 5 min delays revocation.
- Refresh token persisted in sessionStorage in plaintext (`dashboard src/auth/store.tsx:37-58`).
- Test-only endpoint `AuthNzResource.setAccountStoreCognitoProperties` (`:449-460`) ships in the
  production class, guarded only by profile check.

---

## 2. Broken functionality (works-on-paper bugs)

### Backend
- **`CognitoGroupOrganizationStore` org-role feature is nonfunctional** — four independent bugs:
  IAM policy `Resource` built with `Map.of` instead of `List.of` → invalid policy JSON
  (`:249-253`); S3 ARN missing the bucket name (`:218-221`); role **ARN** passed where IAM
  requires a bare role name (`:163,232` → `IamUtil.java:88-107`); assume-role principal is
  `lambda.amazonaws.com` for a role attached to a Cognito group (`IamUtil.java:57`).
- Athena results land at a malformed path with a duplicated `organization=` segment
  (`FirehoseS3AthenaBatchStore.java:106-109`), and inherit topic retention instead of a short
  TTL (cost).
- Glue `updateTable` builds a `TableInput` with no name/database/catalog
  (`FirehoseS3AthenaBatchStore.java:335-343`) — cannot succeed.
- `updateTopicSchema` signals success by **throwing** `WebApplicationException(CREATED)`
  (`ControlResource.java:296`) and NO_CONTENT as an error (`:287-289`).
- Athena results header-skip heuristic drops row 0 of page 1 unconditionally
  (`AthenaQueryStore.java:225`); `getDatabaseSchema` doesn't paginate and NPEs on tables without
  a storage descriptor (`:301-310`); history is N+1 serial `GetQueryExecution` calls (`:257-281`).
- Schema inference: all fields typed `"string"`, samples only the 100 lexicographically-first S3
  keys (oldest partitions — new fields never seen), no cap on field set (OOM risk at 256 MB)
  (`FirehoseS3AthenaBatchStore.java:470-616`).

### Dashboard
- **Athena query page never shows results**: submit sets `queryStatus` to `undefined` and never
  starts the poll; the polling effect early-returns on `!queryStatus`
  (`pages/storage/lake/query.tsx:163-207`). `isExecuting` sticks forever. "Load More" *replaces*
  results instead of appending (`:120-135`, `:437-441`).
- All `e.response.data...` error-message chains are dead code — the generated `typescript-fetch`
  client throws `ResponseError` with a raw `Response` and no parsed body; users see generic
  errors everywhere (only `create-organization.tsx:68` reads status correctly).
- `detectEnv.ts:56` — stray `}` in the self-host docs URL.
- S3 browser folder navigation assumes `/`-suffixed keys that `listObjects` without a delimiter
  never returns (`S3FileBrowser.tsx:210-225`).
- Token auto-refresh uses a module-global lock + uncleared `setTimeout`
  (`auth/auth.tsx:154-206`) — breaks with multiple tabs.
- `pages/organization/create.tsx` is dead scaffolding ("Form header", empty onSubmit); dashboard
  home page renders the literal string "Add content here"; "Profile"/"Preferences" menu entries
  do nothing.

### CLI / runners
- `Deploy.java:78-103` / `UploadSchema.java:73-80`: virtual-thread executor created but
  `supplyAsync` never uses it — `--no-parallel` is a no-op and work runs on commonPool.
- `CliConfigImpl.java:83`: `profileNameOpt.get()` on an empty Optional → crash instead of the
  intended error message. `EnvLogin` default-org keyed inconsistently (`:81-83` vs
  `CliConfigImpl.java:79-113`); 6 NPE sites when `System.console()` is null (CI/pipes).
- `Query.java:161-181`: unbounded `while(true)` poll, reloading + reparsing the whole project
  file every 2 s.
- `StateManagerFactoryImpl.java:44`: cache keyed on `String[]` (identity equality) — never hits;
  new `DynamoStateManager` per call.
- TS runner: errors swallowed with no logging (`entrypoint.ts:87-90`); `web` path has no
  try/catch (Java maps to 500); template bugs — `media-typer` import that doesn't exist, invalid
  Avro decode call, binary serde returns a string; generated TS projects pin stale
  `dataspray-runner 0.0.8` (published is 0.0.9).

---

## 3. Build / CI / deploy

- **`test.yml` gating is inverted** (`:23`): on push to master, tests run only if the commit
  message contains `[skip deploy]`; a `[release]` commit runs neither tests nor deploy.
- **`deploy.yml`**: every master push auto-deploys staging (JVM lambdas), publishes all artifacts
  to OSSRH/npm as a side effect, uses long-lived static AWS keys (should be GitHub OIDC), and has
  no dependency on tests passing.
- Parent-POM `staging`/`production` profiles are commented out (`pom.xml:834-885`); the real ones
  live in `dataspray-package/pom.xml`. `macos-skipTests`/`macos-skipITs` profiles have inverted
  activation conditions (`pom.xml:952-981`) that undo the `macos` profile's phase change.
- `dataspray-package/pom.xml` hardcodes `0.0.1-SNAPSHOT` artifact versions (7×) and AWS profile
  `dataspray` on the synth execution (`:236`) — self-host deploys fail without a profile by that
  name.
- Native lambda architecture is chosen from the **build host's** `os.arch`
  (`FunctionStack.java:143-155`) — ARM Mac vs x86 CI silently changes the deployed arch.
- Usage plans: `UNLIMITED` has no throttle at all; `TEN_RPS` and `HUNDRED_RPS` are identically
  throttled at 300 rps (`ApiStack.java:216-231`, comments wrong); `GLOBAL` has burst(10) <
  rate(100), which is inverted (`:195-199`).
- No CloudWatch log-group retention on any Lambda (unbounded log cost), no alarms, no WAF, no
  budgets. Firehose error output vanishes after 1 day with no alerting.
- CDK test (`DatasprayStackTest`) deploys only 2 of ~9 stacks against Moto and has exactly one
  real assertion — all IAM-heavy stacks are never validated. No `Template.fromStack` assertions
  anywhere.
- No pnpm/node caching in CI; JVM test job realistically brushes its 30-min timeout.

---

## 4. Code health / hygiene

- **`dataspray-remote-workspace` is dead**: not in the reactor, untouched since 2024-12, manager
  methods return `null`, Python impl crashes on import (`config.py:10` — `print()` assigned, `os`
  unimported), copy-pasted "ClearFlask API" spec. Recommend deleting or archiving to a branch.
- Orphaned/stale files: `dataspray-core/src/main/resources/schema/dataspray.schema.yaml` (real
  schema is generated; this one is wrong in 5+ ways), `.node-version` pinning Node 18, stale
  Cloudscape patch `3.0.880` in `dataspray-site-dashboard/patches/`, 4 orphaned per-module
  `pnpm-lock.yaml`s, committed `.idea/` dirs in both runner modules, 4 committed `.DS_Store`s.
- Dead code: `util/cognito.ts` (+ its AWS SDK dependency), landing `Block.tsx`, 3 unused
  Cloudscape packages, `BatchRuntime`, `XmlConflictResolution`, `getCommitHash`, unused injected
  clients (`AthenaQueryStore.java:114`, `FirehoseS3AthenaBatchStore.java:143`), dead
  `ObjectMapper` field in the ingest hot path (`IngestResource.java:77`).
- Duplication: `formatBytes` ×3 and `formatTimestamp` ×2 in `Query.java`; 4× repeated ApiClient
  setup in `DataSprayClientImpl`; 7 near-identical catch-wrap blocks in `StreamRuntimeImpl`;
  state key constants duplicated between store and runner.
- Docs drift: `docs/schema-inference-design.md` says "Implemented (Custom)" but its own commit
  claims Glue-based inference (no code changed either way); `CHANGELOG.md` is empty; docs site is
  4 stub pages + a broken `_meta.json` nav entry (`web-endpoint`); landing page is a construction
  splash with 40 lines of commented-out sections.
- CLI output goes through SLF4J in some commands and `System.out` in others; `System.exit(1)`
  inside picocli bodies bypasses the exception handler.

---

## 5. Test coverage gaps (worst first)

| Area | State |
|---|---|
| `DynamoStateStore`, state endpoints | zero tests (and §1.3 bugs would have been caught) |
| `FirehoseS3AthenaBatchStore` (schema inference, S3 browser, presign) | zero tests |
| `CognitoGroupOrganizationStore` (§2 IAM bugs live here) | zero tests |
| `LambdaDeployerImpl` (1,120 lines) | zero tests |
| Cross-org denial paths | untestable today — `AbstractLambdaTest.java:82` hardcodes the org context |
| Frontend (all 3 sites + TS client + TS runner) | zero tests, no runner configured |
| `dataspray-cli` | `CliTest` is the exact zero-assertion anti-pattern CLAUDE.md forbids |
| `dataspray-common` (`GsonUtil` adapters with 2 recent date bugfixes) | zero tests |
| CDK templates | 1 assertion total; 7 of 9 stacks never validated |

Well-covered: `ApiAccessStoreTest`, `ControlBase` deploy-lifecycle flow, `IngestBase` end-to-end,
`TableFormatterTest`, `GitExcludeFileTrackerTest`, `MergeStrategiesTest`.

---

## 6. Where to go from here — recommended sequence

1. **Security sprint (1.1–1.3)**: org-name validation + membership checks on every endpoint;
   Athena isolation decision (workgroups/session policies at minimum, fix the blocklist regex);
   state store — switch to `UpdateItem`, reject reserved attribute names, fix `unmarshalValue`,
   and add the missing DynamoDB data-action IAM grant.
2. **Make shipped features actually work**: query-page polling + pagination + error surfacing in
   the dashboard; org-role IAM bug cluster; Glue `updateTable`; Athena result path/retention.
3. **CI/CD hygiene**: fix `test.yml` gating; gate deploy on tests; move to GitHub OIDC; decide
   whether master-push→staging-deploy is still wanted; add log retention + a basic alarm set.
4. **Test investment where the bugs were**: state store, batch store, org store, control-resource
   endpoints (incl. a cross-org denial harness — fix `AbstractLambdaTest`), and a frontend
   harness (vitest + RTL, or Playwright against `next dev`).
5. **Product gaps** (feature work with existing server support): CLI commands for state, topics,
   and file browsing (server + clients already exist — the CLI covers ~17 of 42 API operations);
   dashboard home page; docs-site content; landing page.
6. **Cleanup pass**: delete `dataspray-remote-workspace`, orphaned schema yaml, `.node-version`,
   stale patch + lockfiles, dead code and unused deps; dedupe `Query.java` helpers.

Suggest folding items 1–3 into `ROADMAP.md` as new P0/P1 entries so the tracker stays the single
source of truth.
