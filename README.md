# Token Pilot

Token Pilot is a lightweight Java/Spring library for tracking Spring AI token usage, calculating model cost, publishing Micrometer metrics, and enforcing budget policy.

The intended user experience is one dependency plus `token-pilot.*` configuration:

```gradle
dependencies {
    implementation 'cloud.token-pilot:token-pilot-starter'
}
```

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

## What It Does

- Normalizes AI token usage into `TokenUsage`.
- Calculates model cost from `PricingPlan` using `BigDecimal`.
- Records usage and cost through `LedgerManager`.
- Publishes token and cost metrics through Micrometer.
- Integrates with Spring AI `ChatClient` through `LedgerAdvisor`.
- Supports basic budget evaluation with allow, warn, and block decisions.
- Provides Spring Boot autoconfiguration through `token-pilot-starter`.

## Modules

| Module | Purpose | Status |
| --- | --- | --- |
| `token-pilot-core` | Core domain, pricing, cost calculation, ledger interfaces | Basic implementation complete |
| `token-pilot-spring-ai` | Spring AI usage extraction and advisor integration | Basic implementation complete |
| `token-pilot-micrometer` | Micrometer listener for token and cost metrics | Basic implementation complete |
| `token-pilot-budget` | Budget state store and budget evaluator | Basic implementation complete |
| `token-pilot-autoconfigure` | Spring Boot autoconfiguration and property binding | Basic implementation complete |
| `token-pilot-starter` | Final user dependency bundle | Basic implementation complete |
| `token-pilot-sample-app` | Local starter verification app | Basic E2E complete |
| `external-consumer-fixture` | Published starter consumer verification module | Maven local consumer verification |

## Metrics

Token Pilot publishes:

- `ai.token.usage.total`
- `ai.token.usage.distribution`
- `ai.token.cost.total`

Metric tags are filtered by an allowlist to avoid high-cardinality tags by default. The default allowed tag is:

```text
tenant_id
```

Spring Boot actuator metrics such as `jvm_*`, `application_*`, and `http_server_*` only prove that Prometheus exposure is working. Token Pilot metrics appear after a ledger event is recorded.

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

## Maven Publishing

### Maven Central Release

Target release coordinates for the next Token Pilot release:

```text
cloud.token-pilot:token-pilot-starter:0.0.1
```

After the renamed artifacts are published, consume the release from Maven Central like a normal dependency:

```gradle
repositories {
    mavenCentral()
}

dependencies {
    implementation "cloud.token-pilot:token-pilot-starter:0.0.1"
}
```

Run the repository-managed external consumer module against the Central release after the renamed coordinates are available:

```bash
./gradlew :external-consumer-fixture:bootRun -PusePublishedStarter=true
```

Verify that the external app booted from the published artifact path:

```bash
curl http://localhost:8081/test/token-pilot/published
```

Prepare a signed release build locally:

```bash
./gradlew publishToMavenLocal -PprojectVersion=0.0.1
```

Release-to-Central flow:

```bash
./gradlew publishAllPublicationsToStagingRepository -PprojectVersion=0.0.1
./gradlew jreleaserConfig -PprojectVersion=0.0.1
./gradlew jreleaserDeploy -PprojectVersion=0.0.1
```

The repository supports release-friendly version overrides with `-PprojectVersion=0.0.1`, signs published artifacts automatically when the signing key file properties are present, stages release artifacts into `build/staging-deploy`, and wires JReleaser to the Central Publisher Portal.

### Snapshot Publishing

Publish all library modules to your local Maven repository:

```bash
./gradlew publishToMavenLocal
```

Snapshot coordinates:

```text
cloud.token-pilot:token-pilot-starter:0.0.1-SNAPSHOT
```

Run the external consumer module against the published starter:

```bash
./gradlew publishToMavenLocal :external-consumer-fixture:bootRun \
  -PusePublishedStarter=true \
  -PpublishedStarterVersion=0.0.1-SNAPSHOT
```

Verify that the external app booted from the published artifact path:

```bash
curl http://localhost:8081/test/token-pilot/published
```

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

The starter and autoconfigure path is implemented at a basic level:

- `token-pilot-starter` pulls in the runtime modules.
- `token-pilot-autoconfigure` registers the core, Spring AI, Micrometer, and Budget beans conditionally.
- `token-pilot.pricing.*` is bound into pricing plans and connected to `PricingRegistry`.
- Budget beans are connected to `LedgerAdvisor` when budget is enabled.
- The sample app confirms starter classpath, bean registration, direct ledger recording, Spring AI `ChatClient` advisor flow with a fake model, budget behavior, token-pilot metrics, and Prometheus actuator exposure.
- Library modules publish through `maven-publish`.
- `external-consumer-fixture` uses a project dependency by default so the main CI build stays green.
- Published artifact verification is enabled explicitly with `-PusePublishedStarter=true`, with release `0.0.1` as the default published coordinate and `-PpublishedStarterVersion=0.0.1-SNAPSHOT` available for snapshot checks.

Remaining MVP work:

- Validate remote snapshot publishing and CI credentials flow for GitHub Packages.

## Development

Run all tests:

```bash
./gradlew test
```

Project implementation notes, roadmap, and agent instructions live in [AGENTS.md](AGENTS.md).
