![Language](https://img.shields.io/badge/language-Java%2017-blue.svg)
![Framework](https://img.shields.io/badge/framework-Spring_Boot_3.3-green.svg)
![Build](https://img.shields.io/badge/build-Maven-red.svg)
![Container](https://img.shields.io/badge/container-Docker-blue.svg)
![CI](https://img.shields.io/badge/CI-GitHub_Actions-2088FF.svg)
![Security](https://img.shields.io/badge/scan-Trivy_%2B_SBOM-critical.svg)
![Registry](https://img.shields.io/badge/registry-Amazon_ECR-orange.svg)
![License](https://img.shields.io/badge/license-MIT-lightgrey.svg)

# Expenses Tracker Web Application

A Spring Boot 3 / Java 17 expense-tracking service with server-rendered Thymeleaf views, Spring Security authentication, and a MySQL backing store. Beyond the application code, this repo is set up as a small but complete **delivery pipeline**: quality-gated builds, a hardened multi-stage container image, vulnerability scanning with an SBOM, a smoke-tested promotion flow into Amazon ECR, and a `docker-compose` stack for local parity with how the app runs in CI.

This README is written from an operational point of view — what runs, how it's built, how it's shipped, and what to check when something breaks.

## Table of Contents
- [Architecture at a Glance](#architecture-at-a-glance)
- [Tech Stack](#tech-stack)
- [Repository Layout](#repository-layout)
- [CI/CD Pipeline](#cicd-pipeline)
- [Container Image](#container-image)
- [Local Development](#local-development)
  - [Docker Compose (parity with CI)](#docker-compose-parity-with-ci)
  - [Bare Maven (fast inner loop)](#bare-maven-fast-inner-loop)
- [Configuration Reference](#configuration-reference)
- [Operational Notes](#operational-notes)
- [Screenshots](#screenshots)
- [Contributing](#contributing)
- [License](#license)

## Architecture at a Glance
```
 developer push/PR
        │
        ▼
 ┌─────────────────────┐   spotless / checkstyle / pmd
 │  Build, Test & Scan  │   unit + integration tests (Testcontainers, H2)
 └─────────┬────────────┘   package jar, upload test + coverage artifacts
           │
           ▼
 ┌─────────────────────┐   docker buildx (local daemon load)
 │  Build Image & Scan  │   CycloneDX SBOM + Trivy scan (gate on CRITICAL)
 └─────────┬────────────┘   push sha-tagged image to ECR (OIDC, no static keys)
           │  (main branch / manual dispatch only)
           ▼
 ┌─────────────────────┐   pull sha-tagged image from ECR
 │ Smoke Test & Publish │   spin up app + MySQL on a throwaway network
 └─────────┬────────────┘   poll health endpoint, tear down
           │
           ▼
   promote sha tag → `latest` (ECR manifest copy, no re-push of bytes)
```

## Tech Stack
| Layer | Technology |
|---|---|
| Language / Runtime | Java 17 |
| Framework | Spring Boot 3.3, Spring MVC |
| Security | Spring Security |
| Persistence | Spring Data JPA / Hibernate |
| Database | MySQL |
| Templating | Thymeleaf + Bootstrap |
| Build | Maven, Maven Wrapper |
| Code Quality | Spotless (Google Java Format), Checkstyle (Google checks), PMD |
| Testing | JUnit, Spring Security Test, Testcontainers (MySQL), H2 |
| Containerization | Docker (multi-stage build) |
| CI/CD | GitHub Actions (reusable workflows) |
| Supply Chain Security | Trivy (image scan), CycloneDX SBOM |
| Registry / Cloud | Amazon ECR, AWS IAM (OIDC role assumption) |

## Repository Layout
```
├── src/main/java/com/SpringBootMVC/ExpensesTracker
│   ├── controller/     # Login, signup, and expense/filter controllers
│   ├── entity/         # User, Client, Expense, Category, Role
│   ├── repository/     # Spring Data JPA repositories
│   ├── service/        # Business logic
│   ├── security/       # Spring Security configuration
│   └── DTO/            # Data transfer objects
├── src/main/resources
│   ├── templates/      # Thymeleaf views
│   ├── static/          # CSS, JS, images
│   └── application.properties
├── .github/workflows/
│   ├── ci.yml                  # Orchestrator: triggers on push/PR/dispatch
│   ├── build-and-scan.yml      # Test, quality gates, image build, SBOM, Trivy, ECR push
│   └── verify-and-publish.yml  # Smoke test + promotion to `latest`
├── Dockerfile           # Multi-stage build: Maven builder -> Eclipse Temurin JRE (non-root)
├── docker-compose.yml   # App + MySQL, used for local dev and CI smoke testing
└── sql_script.sql       # Reference schema
```

## CI/CD Pipeline
The pipeline is split into three reusable workflows chained together by `ci.yml`, so each stage can also be invoked or tested independently.

**1. Build, Test & Scan** (`build-and-scan.yml`)
- Compiles and packages with Maven, running **Spotless**, **Checkstyle**, and **PMD** as hard gates — a failing check fails the build, not just a warning.
- Runs the test suite (unit tests plus **Testcontainers**-backed integration tests against a real MySQL instance) and uploads Surefire and JaCoCo reports as build artifacts.
- Builds the Docker image straight into the local daemon (`load: true`) rather than round-tripping a tarball through artifact storage, so it's immediately available for scanning.
- Generates a **CycloneDX SBOM** and runs a **Trivy** scan gated on `CRITICAL` severity.
- Authenticates to AWS via **OIDC** (`id-token: write`, no long-lived static credentials) and pushes the image to **Amazon ECR**, tagged with the commit SHA — immutable, traceable artifacts.

**2. Smoke Test & Publish** (`verify-and-publish.yml`)
- Only runs on `main` or manual dispatch, after the build/scan stage succeeds.
- Pulls the exact sha-tagged image from ECR (not a rebuild — testing what will actually ship), boots it alongside a MySQL container on an isolated Docker network, and polls the app's root endpoint until healthy or timeout.
- Tears down the throwaway network and containers unconditionally (`if: always()`), so a failed smoke test doesn't leave orphaned infrastructure.
- On success, promotes the verified sha tag to `latest` via an ECR manifest copy — no image bytes are re-pushed, so the promoted tag is byte-for-byte what was already scanned and smoke-tested.

**Design choices worth noting:**
- The pipeline only triggers on changes to files that actually affect the build or runtime — `src/**`, `pom.xml`, `Dockerfile`, `docker-compose.yml`, `sql_script.sql`, and the workflows themselves (`.github/workflows/**`). Edits to docs, screenshots, or the README don't burn a CI run. `workflow_dispatch` is always available for a manual run regardless of what changed.
- `concurrency` is scoped per branch/ref with `cancel-in-progress` on non-main branches, so redundant pushes don't queue up CI runs.
- Every job has an explicit `timeout-minutes`, so a hung build or stuck container doesn't silently eat runner minutes.
- The scan step uses `exit-code: 0` for the human-readable table output but the real gate is the earlier severity-filtered check — worth double-checking before assuming a red Trivy step will hard-fail the pipeline.

## Container Image
The `Dockerfile` is a two-stage build:
1. **Builder** — `maven:3.8.3-openjdk-17`, resolves dependencies separately from the source copy to maximize layer cache hits, then runs `mvn clean install -DskipTests=true` (tests are already covered by the CI job before this stage runs).
2. **Runtime** — `eclipse-temurin:17-jre-alpine`, a minimal JRE-only base. The image creates a dedicated `appgroup`/`appuser` and runs as that non-root user, not root.

The image listens on port `8080` internally (see `EXPOSE 8080` in the Dockerfile), but the application itself binds to `SERVER_PORT` (default `8081`) — when wiring up port mappings or health checks, use the `SERVER_PORT` value, not the Dockerfile's `EXPOSE` declaration.

## Local Development

### Docker Compose (parity with CI)
Mirrors the topology the smoke-test stage exercises in CI: the app container plus a MySQL container on a shared bridge network, gated by a MySQL healthcheck before the app starts.

1. Clone and enter the repo:
   ```bash
   git clone https://github.com/balaji-bodkekar/Expenses-Tracker-Web_Application.git
   cd Expenses-Tracker-Web_Application
   ```
2. Create a `.env` file:
   ```
   DB_NAME=expenses_tracker
   DB_USER=root
   DB_PASSWORD=your_password
   ```
3. Build and start:
   ```bash
   docker compose up --build
   ```
4. App is reachable at `http://localhost:8081`.

### Bare Maven (fast inner loop)
Faster for iterating on code without rebuilding images.

1. Stand up a local MySQL instance and create a database (`sql_script.sql` has the reference schema).
2. Export the connection settings (or edit `application.properties` directly):
   ```
   DB_URL=jdbc:mysql://localhost:3306/expenses_tracker?useSSL=false&serverTimezone=UTC
   DB_USER=root
   DB_PASSWORD=your_password
   ```
3. Run the same quality gates CI enforces before pushing:
   ```bash
   ./mvnw clean package spotless:check checkstyle:check pmd:check
   ```
4. Run the app:
   ```bash
   ./mvnw spring-boot:run
   ```

## Configuration Reference
All runtime configuration is externalized via environment variables — no rebuild required to point at a different database.

| Variable | Description | Default |
|---|---|---|
| `SERVER_PORT` | Port the application binds to | `8081` |
| `DB_URL` | JDBC URL for MySQL | `jdbc:mysql://mysql:3306/expenses_tracker?useSSL=false&serverTimezone=UTC` |
| `DB_USER` | Database username | `root` |
| `DB_PASSWORD` | Database password | *(required, no default — the app will not start without it)* |

CI-specific secrets (not needed for local runs): `CI_DB_PASSWORD` for the smoke-test MySQL instance, plus the AWS OIDC role (`github-actions-ecr-role`) used for ECR authentication.

## Operational Notes
- **Image provenance:** every image in ECR is tagged with the commit SHA it was built from; `latest` only ever points at a SHA that has passed quality gates, a CRITICAL-severity scan, and a smoke test — treat `latest` as the current known-good build, not a moving target to distrust.
- **Rollback:** because tags are immutable and sha-addressed, rolling back is a matter of re-pointing the deployment at a previous sha tag rather than rebuilding.
- **SBOM:** a CycloneDX SBOM is generated per build and retained for 30 days as a workflow artifact — pull it from the Actions run if you need to audit dependencies for a specific release.
- **Health checks:** the MySQL container in both `docker-compose.yml` and the CI smoke test uses `mysqladmin ping`; the app's own readiness is currently inferred by polling `/` rather than a dedicated actuator health endpoint — factor that in if you're wiring up external monitoring.

## Screenshots
![Screenshot](screenshots/1.png)
![Screenshot](screenshots/2-2.png)
![Screenshot](screenshots/3-3.png)
![Screenshot](screenshots/4-4.png)
![Screenshot](screenshots/5-5.png)
![Screenshot](screenshots/6-6.png)
![Screenshot](screenshots/7.png)
![Screenshot](screenshots/8.png)

## Contributing
Contributions are welcome. Please make sure `spotless:check`, `checkstyle:check`, and `pmd:check` pass locally before opening a PR — these are hard gates in CI, not suggestions. Open an issue or PR for bugs and improvements.

## License
This project is licensed under the MIT License. See [LICENSE](LICENSE) for details.