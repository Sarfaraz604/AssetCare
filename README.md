# AssetCare — Asset Maintenance & Work Order Manager

A portfolio modular monolith for keeping an equipment inventory and taking maintenance requests through assignment, repair and completion. Built for learning and explaining Java/Spring, Vue, PostgreSQL, security and container delivery in an interview.

## Users and features

| Role | Permissions |
| --- | --- |
| ADMIN | Read all assets/orders; create/edit/retire assets; submit requests; assign/unassign/reassign technicians; cancel active orders |
| TECHNICIAN | Read the asset catalogue; read **assigned** orders/history; start and complete assigned work |
| VIEWER | Read the asset catalogue; submit requests; read **their own** orders/history |

The backend enforces these rules in services. The UI only offers permitted actions. Dashboard asset totals cover the shared asset catalogue; all order totals and recent orders use the same role scope as the order list.

- Search, filters, stable sorting and server pagination (maximum 100 records per page).
- Soft retirement preserves records and is blocked while an asset has active orders.
- Explicit status transitions, completion notes, timestamped actor-linked history.
- Session login, BCrypt, CSRF tokens, JSON 401/403 errors, no public registration.
- Bean Validation, consistent errors/request IDs, optimistic concurrency conflicts.
- Database counts and recent work; no hard-coded dashboard analytics.
- Responsive Vue screens with loading/empty/error states, confirmations and input preservation.

## Stack and pinned versions

Java **21**, Spring Boot **3.5.16** (manages Spring Security, Hibernate, Flyway, JUnit 5, Mockito and Testcontainers versions), Maven **3.9.11** via Wrapper **3.3.4**, PostgreSQL **17.7**, Vue **3.5.46**, Vue Router **4.6.5**, Vite **7.3.8**, Vue Vite plugin **6.0.11**, Prettier **3.8.3**. The npm lockfile pins transitive frontend dependencies. See `pom.xml` and `frontend/package-lock.json` for resolved build inputs. Node **22.12+** is required; CI/container builds use Node 22. Local verification used Node 25.0.0.

Compatibility references: [Spring Boot 3.5 system requirements](https://docs.spring.io/spring-boot/3.5/system-requirements.html), [Vite requirements](https://vite.dev/guide/). No Lombok, H2, microservices or frontend Composition API.

## Start with Docker Compose

Requires Docker Engine/Desktop with Compose, network access for public images/dependencies, and free ports 8080/5432.

```powershell
Copy-Item .env.example .env
# Edit .env: replace every REPLACE_ value with your own local values.
docker compose up --build
```

Open **http://localhost:8080**. Sign in as `admin`, `technician` or `viewer` using the corresponding password you set. Compose explicitly selects the `dev` profile. The seed runs only when **both** the dev profile and `DEMO_SEED_ENABLED=true` are set. It creates three accounts and three assets, never fictional work-order metrics. Disable seeding after initial setup if desired.

```sh
docker compose down        # Keep database data
```

Database data lives in the `postgres_data` named volume. Existing seeded accounts are never overwritten; changing seed environment variables does not reset their passwords. There is no password-reset or account-management UI in this version. Do not commit `.env`.

## Run with a local JDK and PostgreSQL

Install JDK 21 and Node 22.12+; set `JAVA_HOME` to the JDK. Maven is downloaded by the wrapper. Start a PostgreSQL 17 database first (or run `docker compose up -d db`). Create an `assetcare` database and a database user allowed to create tables in its schema. Flyway applies the migration; Hibernate validates it. No automatic schema recreation is used.

In PowerShell, set environment variables in the terminal that runs Spring:

```powershell
$env:DB_URL = 'jdbc:postgresql://localhost:5432/assetcare'
$env:DB_USERNAME = 'assetcare'
$env:DB_PASSWORD = '<your database password>'
$env:SPRING_PROFILES_ACTIVE = 'dev'
$env:DEMO_SEED_ENABLED = 'true'
$env:DEMO_ADMIN_PASSWORD = '<your own 12-72 character password>'
$env:DEMO_TECHNICIAN_PASSWORD = '<your own 12-72 character password>'
$env:DEMO_VIEWER_PASSWORD = '<your own 12-72 character password>'
.\mvnw.cmd spring-boot:run
```

In a second terminal:

```powershell
cd frontend
npm.cmd ci
npm.cmd run dev
```

Open **http://localhost:5173**. Vite proxies `/api` to port 8080, including session cookies. The app uses hash-based routing so Spring needs no catch-all HTML controller. Use `npm` instead of `npm.cmd` on macOS/Linux and `./mvnw` instead of `mvnw.cmd` (or `sh mvnw` if executable permissions were not preserved).

To build a single runnable application (run from the repository root):

```powershell
npm.cmd --prefix frontend ci
npm.cmd --prefix frontend run build
.\mvnw.cmd verify
java -jar target/assetcare-1.0.0.jar
```

The Maven resource step copies `frontend/dist` into the JAR. Build the frontend **before** Maven when packaging the UI. `mvnw test` alone can run backend unit/security tests without a database; `mvnw verify` adds the Docker-based PostgreSQL tests. The Dockerfile packages with tests skipped; CI runs the tests separately first.

## Environment variables

| Name | Meaning / default |
| --- | --- |
| `DB_URL` | JDBC URL; defaults to local `assetcare` database |
| `DB_USERNAME` | Database user; defaults to `assetcare` |
| `DB_PASSWORD` | Required; no committed default |
| `SPRING_PROFILES_ACTIVE` | Use `dev` for local demo seeding; omit in production |
| `DEMO_SEED_ENABLED` | `false` by default; explicit local opt-in |
| `DEMO_ADMIN_PASSWORD` | Initial `admin` password; required only when creating that account |
| `DEMO_TECHNICIAN_PASSWORD` | Initial `technician` password |
| `DEMO_VIEWER_PASSWORD` | Initial `viewer` password |
| `COOKIE_SECURE` | `false` for local HTTP; set `true` with production HTTPS |

Seed passwords must be 12–72 characters and must not begin with `REPLACE_`. Use ASCII demo passwords to stay within BCrypt's 72-byte limit. They are BCrypt-hashed before persistence, and never logged. Session cookies are HttpOnly, SameSite=Lax, with a 30-minute idle timeout. Set Secure=true in HTTPS environments. Accounts and sessions stay server-side; there are no browser-stored passwords or bearer tokens.

## Business rules and API

New orders are OPEN. An assigned technician moves OPEN → IN_PROGRESS → COMPLETED, with nonblank completion notes for completion. Only admins assign technicians or cancel OPEN/IN_PROGRESS orders. Assignments can be cleared or changed on active orders, including in-progress orders. Terminal orders are immutable. Admins do not impersonate technicians to complete work. Only admins and viewers create requests. Retired assets are read-only and cannot receive requests. Retirement never deletes data and requires no OPEN/IN_PROGRESS orders.

All timestamps are UTC instants. Due dates are date-only values interpreted on the **UTC calendar**, not at a browser-local midnight. Overdue means `dueDate < LocalDate.now(UTC Clock)` and status OPEN/IN_PROGRESS. A due date of today is not overdue. Backdated requests are allowed and appear overdue immediately when appropriate. Asset maintenance status is manually managed by admins; completing an order does not automatically change it.

See [API documentation](docs/API.md) for endpoints, allowed sort fields, examples and error semantics, and [architecture](docs/ARCHITECTURE.md) for transactions and concurrency.

## Tests and verification

```powershell
.\mvnw.cmd test       # JUnit/Mockito and Spring MVC/security tests
.\mvnw.cmd verify     # Also PostgreSQL Testcontainers tests; Docker required
npm.cmd --prefix frontend ci
npm.cmd --prefix frontend run build
```

Integration tests are named `*IT`, run by Maven Failsafe, and use real PostgreSQL, never H2. Testcontainers explicitly skips those tests when Docker is unavailable. CI first runs `docker info`, so missing Docker fails CI rather than silently accepting a skipped database suite. Reports are in `target/surefire-reports` and `target/failsafe-reports`.

Actual results and environment limits are recorded in [docs/VERIFICATION.md](docs/VERIFICATION.md). The GitHub Actions workflow is **authored only**, not executed on GitHub. Neither the container image nor an OpenShift deployment has been verified in this environment. This project is not claimed to be production-ready.

## OpenShift

Examples are in `deploy/openshift/`. Prerequisites: access to an OpenShift cluster/namespace, a registry image pullable by the cluster, an external PostgreSQL database with schema permissions, network connectivity to it, a trusted database TLS certificate/CA configuration, and permission to create Deployments, Services, Routes, ConfigMaps and Secrets. The JDBC URL template uses `sslmode=verify-full`; configure PostgreSQL trust for your real provider. The manifests do not provision a database or any paid resources.

Build/push an image to **your own registry**, replace the image and database placeholders, and supply the secret through your secret manager. Review and apply the files yourself only after supplying these prerequisites:

```sh
oc apply -f deploy/openshift/configmap.yaml
# Create assetcare-secrets securely, using secret.template.yaml as the schema.
oc apply -f deploy/openshift/deployment.yaml
oc apply -f deploy/openshift/service.yaml
oc apply -f deploy/openshift/route.yaml
oc rollout status deployment/assetcare
```

The Route redirects HTTP to HTTPS. The app listens on 8080, supports arbitrary nonroot UIDs, and writes only to mounted `/tmp`. Liveness checks the process; readiness also checks the database. There is one replica because sessions live in memory. A restart/deploy logs users out. Demo seeding is disabled in the production ConfigMap; production account provisioning is deliberately outside this version, so do not expect a first production login until an administrator provisions accounts through a reviewed process.

## Limitations and next improvements

- Database integration tests require Docker and are unverified here. No live PostgreSQL application run, real-browser full-stack test, container build or cluster deployment has been claimed.
- No account management, password reset, rate limiting, attachments, email, recurring maintenance, multi-tenancy or automatic status synchronization.
- Asset catalogue is shared across all roles. Search uses case-insensitive substring matching; ordinary indexes support foreign keys/status/dates, not arbitrary substring search. Add measured indexing/query optimizations if data volume warrants them.
- DTOs are built within read transactions with OSIV disabled. Lazy loading is explicit in service DTO construction, but list views can issue extra selects (N+1); entity graphs/projections are a documented future improvement.
- Request IDs identify errors, but there is no persistent audit viewer across assets/users or external observability system.
- Write failures preserve form input. A stale conflict requires reloading/reviewing the latest record; there is no automatic merge.
- The Docker base tags pin supported major lines rather than immutable digests. Pin reviewed digests before a real release.

## Read these five files first

1. `README.md` — scope, roles and running the application.
2. `docs/ARCHITECTURE.md` — the flow and database relationships.
3. `src/main/java/com/assetcare/service/WorkOrderService.java` — permissions, transitions, history and dashboard queries.
4. `src/main/java/com/assetcare/config/SecurityConfig.java` — sessions, BCrypt and CSRF.
5. `frontend/src/api.js` — fetch, token refresh and error handling.

Then follow [the interview guide](docs/INTERVIEW_GUIDE.md). [AI assistance](docs/AI_ASSISTANCE.md) and [resume points](docs/RESUME_POINTS.md) distinguish implementation from verification.
