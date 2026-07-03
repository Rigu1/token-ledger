# Token Pilot Agent Guide

## Project Summary

Token Pilot is a multi-module Java/Spring library for tracking Spring AI token usage, calculating model costs, publishing Micrometer metrics, and enforcing budget policy.

Primary goal: users should eventually add one dependency, `token-pilot-starter`, configure `token-pilot.*`, and get automatic cost tracking for Spring AI calls.

## Agent Rules

- Update this `AGENTS.md` whenever a meaningful feature, module, roadmap, or architectural decision changes.
- Prefer interface-first design across module boundaries.
- Keep core domain code precise and dependency-light.
- Use `BigDecimal` for monetary calculations.
- Avoid high-cardinality Micrometer tags by default.
- Do not place business logic in `token-pilot-starter`; keep starter as a thin user entrypoint.
- If implementation classes stay under `internal`, expose them to other modules through deliberate public factories or public configuration APIs.
- Commit messages must be written in Korean unless the user explicitly requests another language.

## Architecture

| Layer | Modules | Responsibility |
| --- | --- | --- |
| API & Domain | `token-pilot-core` | Core models, pricing, cost calculation interfaces, ledger interfaces |
| Adapter | `token-pilot-spring-ai`, `token-pilot-micrometer`, `token-pilot-budget`, `token-pilot-notification` | Integrate with Spring AI, Micrometer, budget policy, and notification event publishing |
| Infrastructure | `token-pilot-autoconfigure`, `token-pilot-starter` | Spring Boot auto-configuration and final user dependency |
| Demo | `token-pilot-sample-app`, `external-consumer-fixture` | Local verification app for starter/autoconfigure integration and published artifact consumption |

## Architecture Decision: Notification

라이브러리는 알림 이벤트를 발행하고 실제 메일/Slack/Webhook 발송은 사용자 애플리케이션이 담당한다.

- `token-pilot-notification`은 알림 이벤트 발행과 중복 방지 로직만 담당한다.
- 실제 메일/Slack/Webhook 전송은 사용자 애플리케이션의 `BudgetNotificationHandler` 구현체가 담당한다.
- 라이브러리 내부에서 SMTP 설정이나 외부 메일 서비스를 기본 흐름으로 포함하지 않는다.

## Module Status

| Module | Status | Notes |
| --- | --- | --- |
| `token-pilot-core` | Basic implementation complete | Domain records, pricing, calculator, registry, ledger manager |
| `token-pilot-spring-ai` | Basic implementation complete | `UsageExtractor`, `LedgerAdvisor`, response usage recording |
| `token-pilot-micrometer` | Basic implementation complete | Tag whitelist and metric metadata implemented; options object is next |
| `token-pilot-budget` | Basic implementation complete | Needs richer policy/window/store support |
| `token-pilot-notification` | Basic implementation complete | Event-based notification API; handler interface, in-memory state store, window-based deduplication |
| `token-pilot-autoconfigure` | Basic implementation complete | Bean registration, property binding, pricing/budget/notification wiring, and ChatClient customizer implemented |
| `token-pilot-starter` | Basic implementation complete | Thin final user entrypoint that brings runtime modules together |
| `token-pilot-sample-app` | Basic E2E complete | Direct ledger metrics, budget, and fake Spring AI advisor E2E implemented |
| `external-consumer-fixture` | Basic implementation complete | Verification module that consumes the published starter from Maven Central by default and can target snapshots explicitly |

## Current Work Focus

The current MVP workstream is packaging and external consumer validation.

MVP tasks:

- Keep sample app dependent on `project(':token-pilot-starter')`.
- Keep local Maven publishing healthy for snapshot verification and keep the external consumer fixture ready for Maven Central release verification after renamed coordinates are published.
- Validate GitHub Packages snapshot publishing before public release.
- Keep published POM metadata aligned with Maven Central promotion requirements.
- Keep Gradle signing and release property wiring ready for Central release work.
- Keep JReleaser Central Portal staging and deploy wiring aligned with the target `cloud.token-pilot` namespace.

Autoconfigure basic implementation has landed. Future autoconfigure work should be incremental hardening rather than first implementation.

Gradle dependency cleanup has landed. Library modules should not regain app-only Spring Boot plugin, actuator, or Prometheus dependencies from the root build.

Current Micrometer status:

- `MetricsOptions` exists as the small Micrometer options object.
- Default allowed tag keys remain `tenant_id`.
- The existing `MicroCostMetricsPublisher(MeterRegistry)` constructor is preserved.
- Tests cover null/empty tags and multiple allowed tags.
- Metric descriptions and base units should remain stable.

## Starter Contract

Expected final user setup:

```gradle
dependencies {
    implementation 'cloud.token-pilot:token-pilot-starter'
}
```

In this repository, sample app verification uses:

```gradle
dependencies {
    implementation project(':token-pilot-starter')
}
```

Starter should include the modules users need at runtime, especially `token-pilot-autoconfigure`. The starter should not create beans itself.

## Autoconfigure Contract

The autoconfigure module provides:

- `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- `TokenPilotAutoConfiguration`
- `TokenPilotProperties`
- Pricing property binding
- Budget property binding
- Notification property binding
- Metrics/tag whitelist property binding
- Conditional beans for:
  - `CostCalculator`
  - `PricingRegistry`
  - `LedgerManager`
  - `UsageExtractor`
  - `LedgerAdvisor`
  - `BudgetEvaluator`
  - `BudgetStateStore`
  - `MicroCostMetricsPublisher`
  - `ChatClientCustomizer`
  - `NotificationStateStore`
  - `BudgetNotificationService`

Shared configuration prefix:

```yaml
token-pilot:
  enabled: true
```

## Autoconfigure Implementation Notes

Autoconfigure is responsible for wiring the starter dependency graph into a Spring Boot application. It should not implement provider API calls, sample-app-only beans, or production Redis/JDBC budget stores.

Bean registration principles:

- Use `@ConditionalOnMissingBean` so user beans win over defaults.
- Use `@ConditionalOnClass` for optional adapter integrations.
- Use `@ConditionalOnProperty` for feature flags under `token-pilot.*`.
- Register beans by public interface type whenever possible.
- Keep `token-pilot-starter` free of business logic and bean creation.

Default bean graph:

| Bean | Condition | Purpose |
| --- | --- | --- |
| `CostCalculator` | missing bean | Core cost calculation |
| `PricingRegistry` | missing bean | Pricing plan lookup |
| `LedgerManager` | missing bean | Cost and usage recording |
| `UsageExtractor` | Spring AI classpath + missing bean | Spring AI response usage extraction |
| `LedgerAdvisor` | Spring AI classpath + missing bean | ChatClient advisor |
| `MicroCostMetricsPublisher` | Micrometer classpath + `token-pilot.metrics.enabled` | Cost/token metrics listener |
| `BudgetStateStore` | `token-pilot.budget.enabled` + missing bean | Default in-memory budget state |
| `BudgetEvaluator` | `token-pilot.budget.enabled` + missing bean | Default budget evaluator |
| `ChatClientCustomizer` | Spring AI classpath + `LedgerAdvisor` bean | Adds advisor to ChatClient builders |
| `NotificationStateStore` | `token-pilot.notification.enabled` + missing bean | Window-based notification deduplication state |
| `BudgetNotificationService` | `token-pilot.notification.enabled` + `BudgetNotificationHandler` bean | Publishes budget notification events to user-defined handler |

`core.internal` implementation classes should remain package-private. Cross-module construction should go through `LedgerComponents` or another deliberate public factory/API. Do not make internal implementation classes public just to satisfy autoconfigure access.

Autoconfigure tests should use `ApplicationContextRunner` and verify:

- context starts with default settings
- pricing properties flow into `PricingProvider`, `PricingRegistry`, and `LedgerManager` cost calculation
- user-defined beans are not overridden
- Micrometer publisher registers only when `MeterRegistry` is available and metrics are enabled
- budget beans do not register by default
- budget beans register when budget is enabled
- budget-enabled `LedgerAdvisor` calls `BudgetEvaluator`
- Spring AI classpath registers `UsageExtractor`, `LedgerAdvisor`, and `ChatClientCustomizer`
- notification beans do not register by default
- notification beans register when notification is enabled and `BudgetNotificationHandler` bean exists

## Notification Contract

사용자는 `BudgetNotificationHandler`를 Spring Bean으로 구현하여 알림을 받을 수 있다.

```java
@Component
class MailBudgetNotificationHandler implements BudgetNotificationHandler {
    @Override
    public void handle(BudgetNotificationEvent event) {
        mailService.sendBudgetAlert(event);
    }
}
```

- `BudgetNotificationHandler` 빈이 없으면 `BudgetNotificationService`는 등록되지 않는다 (no-op).
- `token-pilot.notification.enabled=true` 설정 시에만 notification 빈이 등록된다.
- 알림 중복 방지는 `(targetId, budgetWindow)` 조합으로 처리된다.
- 같은 window 안에서는 낮거나 같은 threshold 재발송이 방지된다.
- 새 window에서는 50/80/100% 알림이 다시 가능하다.

## Recommended Configuration Shape

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
      - model
  budget:
    enabled: false
    monthly-limit: 10.00
  notification:
    enabled: false
```

## Sample App Direction

`token-pilot-sample-app` should be a starter integration verification app.

Current endpoints:

- `GET /test/token-pilot/smoke`: app is running and starter is on classpath.
- `GET /test/token-pilot/beans`: reports whether expected autoconfigure beans exist.
- `GET /test/token-pilot/record`: records a deterministic token usage event through `LedgerManager`.
- `GET /test/token-pilot/budget`: exercises budget enabled/limit behavior when budget beans are present.
- `GET /actuator/prometheus`: validates actuator/prometheus exposure.

Test-only E2E endpoint:

- `GET /test/token-pilot/chat`: exercises the Spring AI `ChatClient` advisor path with a fake/mock provider or documented real provider setup.

The direct ledger E2E test verifies that `/actuator/prometheus` contains token-pilot metrics after a ledger event is recorded. The fake ChatClient E2E test verifies that Spring AI `ChatClient` calls flow through `LedgerAdvisor` into token-pilot metrics without requiring a real provider API key.

## Maven Publishing Direction

MVP publishing should proceed in this order:

1. Add Gradle `maven-publish` configuration.
2. Confirm artifact ids, versions, generated POM metadata, and runtime dependency scopes.
3. Run `publishToMavenLocal`.
4. Create or maintain an external consumer verification module that depends on the published artifact coordinates.
5. Verify the consumer can use only `implementation 'cloud.token-pilot:token-pilot-starter:0.0.1-SNAPSHOT'`.
6. Publish snapshots to GitHub Packages.
7. Document consumer credentials and CI publish flow before public release.
8. Verify Maven Central release consumption with `mavenCentral()` only after the renamed coordinates are published.

## Roadmap

1. Maven Central release consumption regression coverage.
2. GitHub Packages snapshot publishing flow and CI credentials setup.
3. Real provider Spring AI smoke verification behind an opt-in profile.
4. Micrometer options object for autoconfigure integration.
5. Budget policy expansion.
6. Streaming usage aggregation and fallback token estimation.

## Known Risks

- `core.internal` implementation classes are package-private by design. Cross-module construction should continue through public factory/configuration APIs.
- Micrometer publisher filters tags, but the configuration is still constructor-level and should be wrapped in an options object before autoconfigure integration.
- Sample app E2E uses a fake Spring AI `ChatModel`; real provider API behavior is not yet verified.
- Maven Central release consumption must be re-verified after the Token Pilot rename and `cloud.token-pilot` coordinate publication.

## Verification

Run all tests:

```bash
./gradlew test
```

Run sample app after implementation work:

```bash
./gradlew :token-pilot-sample-app:bootRun
```

Check Prometheus metrics:

```bash
curl http://localhost:8080/actuator/prometheus
```

Verify the published starter from the external consumer module:

```bash
./gradlew :external-consumer-fixture:bootRun -PusePublishedStarter=true
curl http://localhost:8081/test/token-pilot/published
```

Use this Central verification path only after the renamed `cloud.token-pilot` artifacts are published.

Verify the snapshot path explicitly:

```bash
./gradlew publishToMavenLocal
./gradlew :external-consumer-fixture:bootRun -PusePublishedStarter=true -PpublishedStarterVersion=0.0.1-SNAPSHOT
curl http://localhost:8081/test/token-pilot/published
```

Publish snapshots to GitHub Packages:

```bash
./gradlew publish \
  -PmavenRepoUrl=https://maven.pkg.github.com/tokenpliot/tokenpilot \
  -PmavenRepoUsername="$GITHUB_ACTOR" \
  -PmavenRepoPassword="$GITHUB_TOKEN"
```

Prepare a signed release build locally:

```bash
./gradlew publishToMavenLocal -PprojectVersion=0.0.1
```

Stage and deploy a Central release:

```bash
./gradlew publishAllPublicationsToStagingRepository -PprojectVersion=0.0.1
./gradlew jreleaserDeploy -PprojectVersion=0.0.1
```

## Update History

### 2026-07-03

- Renamed project branding to Token Pilot.
- Renamed Gradle modules to the `token-pilot-*` pattern.
- Moved Java packages to `io.tokenpilot`.
- Changed the Spring configuration prefix to `token-pilot.*`.
- Updated GitHub repository metadata to `tokenpliot/tokenpilot` and Maven coordinates to the target `cloud.token-pilot:*` namespace.

### 2026-06-08

- Added `token-pilot-notification` as an event-based notification API with user-provided `BudgetNotificationHandler` implementations.
- Kept notification delivery channels such as SMTP, Slack, and Webhook outside the default library flow; applications own concrete delivery.
- Added autoconfigure wiring for notification state and service beans behind `token-pilot.notification.enabled=true` and a user handler bean.
- Removed Redis budget store work from the MVP notification path so `token-pilot-budget` remains dependency-light.

### 2026-05-23

- Hardened Spring AI usage extraction for null responses, missing metadata, missing usage, and native provider usage preservation.
- Added `MetricsOptions` for Micrometer publisher configuration while preserving existing constructors and default `tenant_id` tag behavior.
- Added Micrometer tests for null tags, empty tags, and multiple allowed tag keys.

### 2026-05-11

- Added Gradle `maven-publish` configuration for library modules with shared POM metadata and optional remote repository credentials.
- Added `external-consumer-fixture` as a repository-managed verification module that depends on published `cloud.token-pilot:token-pilot-starter:0.0.1-SNAPSHOT` from `mavenLocal()`.
- Chose GitHub Packages as the first remote snapshot repository target and documented the publish command in `README.md`.
- Added GitHub Packages consumer examples and expanded published POM metadata for later Maven Central promotion.
- Switched `external-consumer-fixture` to use `project(':token-pilot-starter')` by default and require `-PusePublishedStarter=true` for published artifact verification so CI builds do not fail before publish.
- Promoted `cloud.token-pilot:token-pilot-starter:0.0.1` to Maven Central and switched `external-consumer-fixture` to use the Central release by default when published artifact verification is enabled.
- Added Gradle `signing` integration and `projectVersion` override support so release builds can be produced with local GPG material before Central Portal upload wiring is finalized.
- Prefer `signingKeyFile` over inline `signingKey` for local release signing because multiline armored keys are less error-prone when loaded from a file.
- Added JReleaser Gradle integration targeting the Central Publisher Portal with `build/staging-deploy` staging repositories and `cloud.token-pilot` namespace wiring.

### 2026-05-04

- Merged basic autoconfigure implementation for property binding, conditional bean registration, pricing registry wiring, budget-aware advisor creation, and ChatClient customization.
- Added sample app direct ledger, budget, and fake Spring AI ChatClient E2E verification for starter endpoints, `LedgerManager.record(...)`, `LedgerAdvisor`, Micrometer listener wiring, Prometheus `ai.token.*` metrics, and budget block behavior.
- Cleaned Gradle dependencies so app-only Spring Boot plugin, actuator, and Prometheus dependencies are scoped to the sample app instead of every library module.
- Reframed MVP roadmap around Maven publishing and external consumer validation.

### 2026-04-30

- Renamed project guidance from `GEMINI.md` to `AGENTS.md`.
- Added README roadmap for current project gaps.
- Added starter-focused workstream guidance.
- Clarified that `token-pilot-starter` is the current user-entrypoint task while autoconfigure is owned separately.
- Added a README autoconfigure implementation guide covering bean registration, property binding, internal factory options, and test expectations.
- Implemented Micrometer tag whitelist support and metric description/base unit metadata; documented the next options-object step.

### 2026-04-19

- Moved core domain records into `io.tokenpilot.core.domain`.
- Tightened visibility of core internal default implementations.
- Updated dependent modules to use the new domain package structure.
- Verified tests after the package refactor.

### 2026-04-14

- Added token type support including prompt, completion, reasoning, and cached tokens.
- Added token-type-specific pricing fallback logic.
- Updated Spring AI integration for Spring AI 1.1.4 module split.
- Added usage extraction and advisor tests.
