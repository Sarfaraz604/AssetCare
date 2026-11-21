# Verification record

Date: **21 November 2026**, Asia/Calcutta. Workspace initially empty. No deployment or paid resource was created.

## Confirmed results

| Check | Actual outcome |
| --- | --- |
| Clean Java 21 Maven Wrapper `verify` | Passed; executable application JAR produced |
| `AssetServiceTest` | 4 passed |
| `WorkOrderServiceTest` | 19 passed, including parameterized transition cases |
| `SecurityTest` | 11 passed |
| All unit/security tests | **34 passed, 0 failures, 0 errors** |
| `PostgreSqlIT` | **11 skipped**, Docker unavailable; no database assertions executed |
| Frontend dependency install / production build | Passed with npm and Vite 7.3.8 |
| Browser UI smoke using fixture APIs | Passed in headless Microsoft Edge on desktop (1440px) and mobile (390px) |

The browser check used an ignored local Playwright script, not an application runtime mode. It intercepted HTTP responses for login/session, dashboard and assets; it verified navigation, a 409 preserving asset-form input, absence of uncaught browser errors and no document overflow on a 390px dashboard. Desktop/mobile screenshots were reviewed. Fixture counts are not application analytics. This check does not verify server integration, PostgreSQL or actual authorization.

## Commands and environment

Host tools: Windows PowerShell, Node 25.0.0, npm 11.19.0. The host's initial Java was 17.0.17. Maven 3.9.11 and Temurin **21.0.13.1+2** were downloaded into ignored `.tools/`; the final clean build used that Java 21 with the POM's unchanged Java 21 target.

The final backend check used the generated Maven Wrapper offline after dependencies were cached:

```powershell
$env:JAVA_HOME = (Resolve-Path '.tools/jdk-21.0.13.1+2').Path
$env:MAVEN_USER_HOME = (Join-Path (Get-Location).Path '.tools/wrapper-home')
.\mvnw.cmd -B -o '-Dmaven.repo.local=.tools/m2' clean verify
```

For another machine with JDK 21, use `mvnw.cmd verify` or `sh mvnw verify` instead. `.tools/` is a local checking cache and is not required application source. Maven Wrapper is version 3.3.4, configured for Maven 3.9.11. A build can report success while integration tests are skipped: inspect the Failsafe report, not just the exit status.

Frontend commands:

```powershell
npm.cmd --prefix frontend ci
npm.cmd --prefix frontend run format
npm.cmd --prefix frontend run build
```

Local reports: `target/surefire-reports/*.txt` and `target/failsafe-reports/*.txt`. The executable is `target/assetcare-1.0.0.jar`. Build products/reports/tools are ignored; reproduce them with the documented commands. No credentials were embedded to run the checks. Test login credentials are synthetic test fixtures only.

## Blocked or not run

- **PostgreSQL integration (11 tests):** Testcontainers reported no valid Docker environment. Tests for schema/uniqueness, queries, role-scoped counts, UTC overdue behavior, atomic history rollback and database optimistic-lock races are authored and compiled but did not execute. No H2 fallback was used.
- **Real application with PostgreSQL:** no available database/container runtime. Database startup and schema compatibility are not yet empirically verified.
- **Docker image / Compose:** configuration authored; Docker is absent, so no image build, arbitrary-UID runtime or health check verification.
- **OpenShift:** no cluster; prerequisites and placeholder manifests only.
- **GitHub Actions:** workflow authored only; no GitHub run was initiated.
- **Full-stack browser workflow:** requires the real backend/database. The fixture UI smoke does not count as E2E.

Before claiming a complete local full-stack run, install/start Docker, fill in `.env` with local values, run `docker compose up --build`, run `mvnw verify` with Docker available and exercise viewer request → admin assignment → technician start/complete → admin retirement. Check the actual database history and the three role-specific dashboards. Before deployment claims, build/run the image and apply/review the cluster configuration in a real authorized environment.

Additional artifact checks confirmed Java class-file major version **65** (Java 21) and the presence of the built Vue index, hashed JavaScript/CSS, favicon and V1 Flyway migration inside the executable JAR. Maven Wrapper bootstrap reported Maven 3.9.11 on the downloaded JDK 21.

After the final frontend success-message changes, the Vue build and fixture browser smoke passed again. The unchanged backend was repackaged with `mvnw.cmd -B -o -Dmaven.repo.local=.tools/m2 -DskipTests package`; only the current hashed frontend resources were included. That packaging step skipped tests and does not replace the earlier clean verification result.
