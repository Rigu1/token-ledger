# Token Pilot

**Preflight control and post-call accounting for Java LLM services.**

Token Pilot is evolving into a framework-independent Java control and accounting layer for LLM calls. The core is designed to estimate request size, check context and spend policies before a call, and reconcile provider-reported usage and cost afterward. Spring AI is the first optional adapter, not a required dependency of the product identity.

> **Project status — 0.x:** post-call usage normalization, cost calculation, Micrometer publishing, basic budget evaluation, and Spring AI integration exist today. Conservative preflight estimation, context admission, atomic budget reservation, and reconciliation are the 30-day MVP target; they are not yet released capabilities.

## Why Token Pilot

Java teams can already call LLMs through Spring AI or provider SDKs. The remaining operational logic is often rebuilt inside each service.

| Operational problem | Token Pilot responsibility |
| --- | --- |
| A request exceeds a model context window | Use a documented safe upper bound and reject before the provider call |
| Concurrent requests overspend a shared budget | Reserve estimated spend atomically, then commit or release it |
| Estimated and billed usage differ | Reconcile provider-reported usage and final cost |
| Framework-specific types leak into policy code | Keep token, pricing, budget, and ledger contracts framework-independent |
| Telemetry says what happened but cannot enforce policy | Add admission and accounting decisions without replacing standard traces |

The product boundary is deliberately narrow:

> Spring AI observes what happened. Token Pilot decides whether a call may proceed and how it is settled.

Token Pilot benchmarks LiteLLM's operational model, but it is not a Java port of LiteLLM and does not target provider-count parity. A standalone LLM gateway is a later evolution built on the same core contracts.

## Distribution Model

Both entry paths are built from this repository and should share one release version. They are separate Maven artifacts, not separate editions or branches.

| Entry path | Dependency | Spring AI required | Intended user |
| --- | --- | --- | --- |
| Core | `cloud.token-pilot:token-pilot-core:<version>` | No | Plain Java, custom framework adapters, future gateway runtime |
| Spring AI starter | `cloud.token-pilot:token-pilot-starter:<version>` | Yes | Spring Boot and Spring AI applications |

These are the two primary entry paths. Existing supporting artifacts such as `token-pilot-budget` and `token-pilot-micrometer` remain available for manual composition; the starter brings the supported Spring runtime graph together transitively.

The repository currently uses the compatibility artifact name `token-pilot-starter`. `token-pilot-spring-ai-starter` is the clearer target name, but a rename must include an alias or deprecation decision before release documentation advertises it as available.

Core-only usage:

```gradle
dependencies {
    implementation 'cloud.token-pilot:token-pilot-core:<version>'
}
```

Current Spring AI convenience dependency:

```gradle
dependencies {
    implementation 'cloud.token-pilot:token-pilot-starter:<version>'
}
```

The dependency direction is one-way:

```text
Spring AI Starter -> Spring AI Adapter -> Token Pilot Core
                                      \-> Budget / Metrics adapters

Token Pilot Core -X-> Spring AI
```

## Current Configuration

```yaml
token-pilot:
  enabled: true
  pricing:
    plans:
      - model-id: gpt-4o-mini
        currency: USD
        rates:
          PROMPT: 0.00015
          COMPLETION: 0.00060
  metrics:
    enabled: true
    tag-whitelist:
      - tenant_id
  budget:
    enabled: false
    monthly-limit: 10.00
```

This configuration describes the current starter path. The 30-day MVP will extend it with explicit estimator, context-budget, pricing-miss, and reservation policies.

## Capability Status

| Capability | Status | MVP decision |
| --- | --- | --- |
| Provider usage -> `TokenUsage` normalization | Basic implementation | Fix total/breakdown invariants first |
| `BigDecimal` cost calculation and pricing registry | Basic implementation | Define precision, rounding, and missing-price policy |
| `LedgerManager` event publication | Basic implementation | Add idempotent estimate/actual reconciliation contract |
| Spring AI `LedgerAdvisor` | Basic implementation | Keep as an adapter; enforce preflight decisions before the call |
| Micrometer metrics | Basic implementation | Avoid duplicating Spring AI token/latency telemetry by default |
| Budget evaluation | Basic, non-atomic implementation | Replace check-then-add with reserve/commit/release semantics |
| Heuristic token estimation and context check | Planned for 30-day MVP | Report `exact=false`; admission uses a documented safe upper bound |
| Exact byte-level BPE and JMH optimization | Post-MVP | Keep the estimator SPI so it can be added without API churn |
| Provider routing, retry, fallback, gateway runtime | Future | Build only after accounting correctness is demonstrated |

## Modules

| Module | Purpose | Status |
| --- | --- | --- |
| `token-pilot-core` | Core domain, pricing, cost calculation, ledger interfaces | Basic implementation complete |
| `token-pilot-spring-ai` | Spring AI usage extraction and advisor integration | Basic implementation complete |
| `token-pilot-micrometer` | Micrometer listener for token and cost metrics | Basic implementation complete |
| `token-pilot-budget` | Budget state store and budget evaluator | Basic implementation complete |
| `token-pilot-notification` | Budget events and notification de-duplication | Basic implementation complete |
| `token-pilot-autoconfigure` | Spring Boot autoconfiguration and property binding | Basic implementation complete |
| `token-pilot-starter` | Current Spring AI convenience dependency | Basic implementation complete |
| `token-pilot-sample-app` | Local starter verification app | Basic E2E complete |

Planned tokenizer, model catalog, benchmark, routing, and gateway modules are roadmap items and are intentionally absent from this table.

## Request Lifecycle Target

```text
request
  -> estimate input tokens and a safe upper bound
  -> check context window and reserved output
  -> reserve a conservative cost bound
  -> invoke provider through an optional adapter
  -> read provider-reported usage
  -> reconcile actual cost
  -> commit/release reservation
  -> publish ledger events and Token Pilot-specific metrics
```

## Metrics

The current Micrometer adapter publishes:

- `ai.token.usage.total`
- `ai.token.usage.distribution`
- `ai.token.cost.total`

Metric tags are filtered by an allowlist to avoid high-cardinality tags by default. The default allowed tag is:

```text
tenant_id
```

Spring Boot actuator metrics such as `jvm_*`, `application_*`, and `http_server_*` only prove that Prometheus exposure is working. Token Pilot metrics appear after a ledger event is recorded.

When Spring AI Observability is present, its standard latency, trace, and input/output/total token telemetry should be reused. Token Pilot-owned telemetry will focus on cost, preflight decisions, budget reservations, missing pricing, and estimate/actual reconciliation. See [Spring AI Observability와 Token Pilot의 차별화 경계](docs/SPRING_AI_OBSERVABILITY_DIFFERENTIATION.md).

The repository currently compiles against Spring AI 1.1.4, while the boundary report also evaluates 2.0.0. The supported-version and metric-suppression policy is an explicit MVP decision and must be verified per supported version.

## Sample App

In this repository the sample app uses the local starter project:

```gradle
dependencies {
    implementation project(':token-pilot-starter')
}
```

Run it:

```bash
./gradlew :token-pilot-sample-app:bootRun
```

Check the starter smoke endpoint:

```bash
curl http://localhost:8080/test/token-pilot/smoke
```

Check autoconfigured beans:

```bash
curl http://localhost:8080/test/token-pilot/beans
```

Expected shape:

```json
{
  "ledgerManager": true,
  "ledgerAdvisor": true,
  "pricingRegistry": true,
  "microCostMetricsPublisher": true
}
```

Record a deterministic ledger event:

```bash
curl http://localhost:8080/test/token-pilot/record
```

When budget is enabled, check budget behavior:

```bash
curl http://localhost:8080/test/token-pilot/budget
```

Check Prometheus exposure:

```bash
curl http://localhost:8080/actuator/prometheus
```

Sample deployment and local Docker helper infrastructure lives in the separate
[tokenpilot-demo-infra](https://github.com/tokenpliot/tokenpilot-demo-infra) repository.

## Maven Publishing

### Maven Central Release

The repository currently defaults to `0.0.1-SNAPSHOT`. The 30-day MVP release candidate is `0.1.0`; neither the version nor the proposed starter rename should be presented as published until external-consumer verification passes.

Target release paths:

```text
cloud.token-pilot:token-pilot-core:0.1.0
cloud.token-pilot:token-pilot-starter:0.1.0
```

After the artifacts are published, consume the required entry path from Maven Central:

```gradle
repositories {
    mavenCentral()
}

dependencies {
    implementation "cloud.token-pilot:token-pilot-starter:0.1.0"
}
```

Published artifact consumption should be verified from CI with a temporary consumer project after the `0.1.0` coordinates are available.

Prepare a signed release build locally:

```bash
./gradlew publishToMavenLocal -PprojectVersion=0.1.0
```

Release-to-Central flow:

```bash
./gradlew publishAllPublicationsToStagingRepository -PprojectVersion=0.1.0
./gradlew jreleaserConfig -PprojectVersion=0.1.0
./gradlew jreleaserDeploy -PprojectVersion=0.1.0
```

The repository supports release-friendly version overrides, signs published artifacts automatically when the signing key file properties are present, stages release artifacts into `build/staging-deploy`, and wires JReleaser to the Central Publisher Portal.

### Snapshot Publishing

Publish all library modules to your local Maven repository:

```bash
./gradlew publishToMavenLocal
```

Snapshot coordinates:

```text
cloud.token-pilot:token-pilot-starter:0.0.1-SNAPSHOT
```

Snapshot consumption should be verified from CI with a temporary consumer project.

Current remote snapshot target is GitHub Packages. Publish with explicit repository credentials:

```bash
./gradlew publish \
  -PmavenRepoUrl=https://maven.pkg.github.com/tokenpliot/tokenpilot \
  -PmavenRepoUsername="$GITHUB_ACTOR" \
  -PmavenRepoPassword="$GITHUB_TOKEN"
```

Consume the snapshot from GitHub Packages in another Gradle project:

```gradle
repositories {
    maven {
        url = uri("https://maven.pkg.github.com/tokenpliot/tokenpilot")
        credentials {
            username = findProperty("gpr.user") ?: System.getenv("GITHUB_ACTOR")
            password = findProperty("gpr.key") ?: System.getenv("GITHUB_TOKEN")
        }
    }
    mavenCentral()
}

dependencies {
    implementation "cloud.token-pilot:token-pilot-starter:0.0.1-SNAPSHOT"
}
```

Example `~/.gradle/gradle.properties` for consumers:

```properties
gpr.user=your-github-id
gpr.key=ghp_xxx
```

Example `~/.gradle/gradle.properties` for release signing and Central Portal credentials:

```properties
signingPublicKeyFile=/absolute/path/to/public.asc
signingKeyFile=/absolute/path/to/secring.asc
signingPassword=your-gpg-passphrase
centralUsername=your-central-portal-token-username
centralPassword=your-central-portal-token-password
```

Create the signing key file once:

```bash
mkdir -p ~/.gradle
gpg --armor --export 1198AD22D5C72EAB > ~/.gradle/token-pilot-public.asc
gpg --armor --export-secret-keys 1198AD22D5C72EAB > ~/.gradle/token-pilot-signing.asc
chmod 600 ~/.gradle/token-pilot-public.asc
chmod 600 ~/.gradle/token-pilot-signing.asc
```

## Current Status

The existing baseline is usable for local integration experiments:

- `token-pilot-starter` pulls in the runtime modules.
- `token-pilot-autoconfigure` registers the core, Spring AI, Micrometer, and Budget beans conditionally.
- `token-pilot.pricing.*` is bound into pricing plans and connected to `PricingRegistry`.
- Budget beans are connected to `LedgerAdvisor` when budget is enabled.
- The sample app confirms starter classpath, bean registration, direct ledger recording, Spring AI `ChatClient` advisor flow with a fake model, budget behavior, token-pilot metrics, and Prometheus actuator exposure.
- Library modules publish through `maven-publish`.
- Published artifact verification should move to CI with a temporary external consumer project.

The 30-day MVP is a correctness and control milestone, not a gateway milestone:

- Fix token usage, money precision, missing-pricing, and budget-blocking invariants.
- Add a framework-independent UTF-8 heuristic estimator with an explicit non-exact result, scope, and safe upper bound.
- Add versioned model/context/pricing metadata and `TokenBudget.check()`.
- Introduce atomic budget reservation and estimate/actual reconciliation.
- Integrate the new contracts through the optional Spring AI starter.
- Publish Token Pilot-specific cost, preflight, budget, and reconciliation metrics.
- Demonstrate context rejection, budget rejection, successful reconciliation, and plain-Java core use.
- Validate snapshot/release consumption from an external project.

Exact byte-level BPE, JMH optimization, provider routing, retry/fallback, and a standalone gateway are explicitly outside this MVP.

## Documentation

| Document | Purpose |
| --- | --- |
| [Documentation index](docs/README.md) | Document roles and status terminology |
| [30-day MVP execution report](docs/30_DAY_MVP_REPORT.md) | Scope, four-week plan, deliverables, success criteria, and cutline |
| [Evolution plan](docs/EVOLUTION_PLAN.md) | Long-term workstreams and gateway roadmap after the MVP |
| [Spring AI Observability boundary](docs/SPRING_AI_OBSERVABILITY_DIFFERENTIATION.md) | Official-source analysis and the telemetry/control split |
| [Agent guide](AGENTS.md) | Repository rules, architecture decisions, and verification commands |

## Development

Run all tests:

```bash
./gradlew test
```

Project implementation notes, roadmap, and agent instructions live in [AGENTS.md](AGENTS.md).
