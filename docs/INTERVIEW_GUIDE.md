# Interview guide

## A factual 60-second introduction

“AssetCare is an asset maintenance and work-order manager I built as a portfolio project. Admins maintain the equipment catalogue and assign repairs. Viewers submit requests, and technicians work only on assigned orders. The frontend is Vue 3 with the Options API, and the backend is a Java 21 Spring Boot application using PostgreSQL. I kept controllers, business rules, DTOs and persistence separate. The interesting parts are session authentication with CSRF, permissions on individual records, valid status transitions, and recording changes in the same transaction as their history. Version fields prevent lost updates. I also added Docker and OpenShift configuration. Locally, 34 unit and security tests passed; the 11 PostgreSQL integration tests need Docker and were skipped here. I have not deployed this project or verified the container/cluster configuration.”

## Main folders

Read `src/main/java/com/assetcare/model` for stored data, `dto` for input/output shapes, `repository` for Spring Data database access, `service` for business rules and transactions, `web` for HTTP and errors, and `config` for security and the development seed. SQL is in `src/main/resources/db/migration`. Vue screens live in `frontend/src/views`; shared displays live in `components`. Tests mirror the service/web layers; `PostgreSqlIT.java` is the real database suite. Deployment examples live in `deploy/openshift` and CI in `.github/workflows/ci.yml`.

## Walk through one complete request

Use “Start work” as the example:

1. An assigned technician opens `frontend/src/views/OrderDetail.vue`. The API only returns an order if the user is allowed to read it.
2. `changeStatus('IN_PROGRESS')` calls `update()`, sending the visible version and new status.
3. `frontend/src/api.js:api()` uses fetch, includes the session cookie and adds the current CSRF header.
4. `config/SecurityConfig.java:security()` makes Spring Security authenticate the session and reject a bad token.
5. `web/WorkOrderController.java:status()` validates `StatusInput` and passes the ID/input to the service.
6. `service/WorkOrderService.java:changeStatus()` checks record access, version, active state and the actor's technician role/assignment. It permits OPEN → IN_PROGRESS.
7. It updates the status and UTC timestamp and calls `record()` to save the actor-linked history. Both happen inside the same transaction.
8. Hibernate includes the old version in its update condition. A concurrent write can cause an optimistic-lock exception instead of overwriting data.
9. A DTO is built before the transaction closes. JSON returns to Vue, which updates the detail and loads the history.
10. If it fails, `ExceptionAdvice` returns a structured error; Vue keeps the local notes/selection and explains the failure.

## Important code to read and explain

| File | Methods / concepts |
| --- | --- |
| `service/WorkOrderService.java` | `accessible`, `visible`, `create`, `assign`, `changeStatus`, `record`, `dashboard` |
| `service/AssetService.java` | `list`, `update`, `retire`; retired-asset rules |
| `repository/AssetRepository.java` | `findForUpdate`; why two different write paths lock the same parent row |
| `service/Problem.java` | `checkVersion` and clear service errors |
| `service/Paging.java` | maximum page size, sort allowlist and escaping LIKE wildcards |
| `config/SecurityConfig.java` | `userDetailsService`, `security`, `passwordEncoder`, `clock` |
| `web/ExceptionAdvice.java` | validation, conflict and unexpected-error mapping |
| `web/RequestIdFilter.java` | server-generated UUID and safe structured logging |
| `model/WorkOrder.java` | foreign keys, lazy relationships and `@Version` |
| `dto/Views.java` | explicit response mapping and no password hashes |
| `frontend/src/api.js` | `refreshCsrf`, `login`, `api`, `logout` |
| `frontend/src/views/OrderDetail.vue` | Options API, computed permission controls and async updates |

All backend paths above are under `src/main/java/com/assetcare/` unless the path starts with `frontend/`.

## Why these choices

**PostgreSQL:** Assets, users, orders and audit entries form related records. Foreign keys, uniqueness and transactions fit that problem. PostgreSQL is also the actual deployment database, so database tests should exercise PostgreSQL rather than approximate it with H2.

**DTOs:** A database entity includes internal concerns such as password hashes and lazy associations. A DTO gives the API a deliberate shape, validates external input and prevents clients from setting requester, version internals or completion timestamps through mass assignment.

**Transactions:** A status update without its history would leave an incomplete audit trail. A single transaction commits both or neither. A PostgreSQL integration test simulates a history write failure to check rollback; that test is authored but skipped in the current environment.

**Indexes:** Foreign-key joins, status/due-date counts and role-scoped lists are recurring query shapes. Indexes reduce the amount of data PostgreSQL may need to scan. They consume storage and increase write work; no performance improvement has been measured or claimed. Existing B-tree indexes do not accelerate every substring search.

**Optimistic locking:** Two users can load version 2, but both should not overwrite each other. The request carries version 2; the first successful change stores a newer version. The stale request gets 409. Hibernate also guards the database update if the race occurs after the service's version comparison.

**Parent row lock:** Version checks on the order cannot stop a different transaction from retiring its asset. Creation and retirement lock the same asset, so the later operation sees the earlier committed state. This lock is narrow and used only for the asset-related invariant.

## Unit versus integration tests

`WorkOrderServiceTest` uses Mockito repositories/current user and a fixed Clock. It tests transitions, required notes and record permissions quickly without a database. `AssetServiceTest` checks retirement/admin/page rules. `SecurityTest` loads Spring MVC and the real security filter chain with mocked persistence; it tests login, unauthorized access, CSRF rotation, viewer denial, validation and HTTP 409. Those are 34 passing tests in this implementation.

`PostgreSqlIT` starts PostgreSQL 17.7 through Testcontainers and loads the full Spring application. It checks Flyway/schema validation, unique codes, real search/filter/paging queries, visibility, overdue dates, state/history commits, rollback on audit failure and version conflicts between separate EntityManagers. Its 11 tests were skipped because Docker is absent. A green local Maven result therefore does not prove database integration. Explain this distinction clearly.

## Docker versus OpenShift

Docker builds/runs a container image. The multi-stage Dockerfile builds Vue, packages Spring Boot and copies just the runnable JAR into a Java runtime image. Compose connects that application to a separate PostgreSQL container with persistent data and health checks.

OpenShift orchestrates containers in a cluster. Deployment manages the app pod, Service gives it an internal endpoint, Route supplies external HTTPS, ConfigMap carries nonsecret settings and Secret carries the database password. Probes tell the cluster whether to send traffic or restart a stuck process. An arbitrary nonroot UID must be able to read the JAR; the application writes temporary files only in `/tmp`. The manifests are examples and have not been applied to a cluster.

## 15 likely questions and factual answers

1. **Why a modular monolith?** One app keeps deployment, debugging and transactions understandable. This scope has no need for distributed services.
2. **Why the Vue Options API?** The brief prioritizes code that is easy to explain. Each component clearly separates data, computed properties, lifecycle work and methods.
3. **Why server sessions instead of JWTs?** One-origin browser traffic fits session cookies well. Spring handles the session lifecycle, and logout invalidates the server session. No browser token-storage scheme is needed.
4. **Does HttpOnly prevent CSRF?** No. The browser still sends cookies on requests. The server therefore also checks the session-backed CSRF token on unsafe requests.
5. **Why fetch CSRF twice around login?** Authentication invalidates the old token. The client fetches the new one before another mutation.
6. **Can a viewer call admin APIs with developer tools?** They can try, but services resolve the authenticated actor and reject the role. UI hiding is not the enforcement mechanism.
7. **Can one technician read another technician's order?** No. Detail, history, list and dashboard scope use assignment. Reassignment removes access from the old technician.
8. **Who can complete an order?** Only its assigned technician, from IN_PROGRESS, with nonblank notes. Admins assign/cancel; they do not complete technician work.
9. **Why not delete retired assets?** Old work and actor history must remain linked to the asset. Retirement changes status and blocks new requests.
10. **What counts as overdue?** A nonnull due date strictly before the current UTC date, with OPEN or IN_PROGRESS status. Today is not overdue; terminal orders never count.
11. **What if saving history fails?** The service transaction should roll back both writes. There is an integration test for this guarantee, currently unverified because Docker is unavailable.
12. **How are simultaneous edits handled?** DTO version checks and JPA `@Version` return 409 instead of losing another user's changes. The UI preserves input and asks for review/reload.
13. **Why turn OSIV off?** It keeps database loading within service transactions and prevents serialization from silently running queries. DTO construction makes required access explicit, although list loading can still be optimized.
14. **What is actually verified?** Java 21 clean Maven verification with 34 passing unit/security tests; frontend production build; a mocked-API browser UI smoke check. PostgreSQL tests were skipped. No Docker/OpenShift/GitHub execution is claimed.
15. **What would you improve next?** First run the database tests and full-stack flow with Docker. Then add reviewed user provisioning/password reset, query projections for list efficiency, pagination/accessibility automation and a suitable session strategy if multiple replicas become necessary.

## Limitations to volunteer when relevant

This is a portfolio project without real users, measured performance improvements or a deployment. It has no account management, attachments, notifications, preventive scheduling or tenant separation. Sessions are in memory. All roles share the asset catalogue. Search/filter behavior and date semantics are intentionally simple. Database/container/cluster checks remain outstanding. Do not imply that configuration files alone prove operational behavior.

## Learning order

1. Read README and run the frontend build/unit tests.
2. Read the enums, `Asset` and `WorkOrder`; match their fields to V1 SQL.
3. Follow a GET from a controller to a service, repository and DTO.
4. Read `CurrentUser` and security; trace CSRF/login with the browser network panel.
5. Follow creation, assignment, start and completion through the services and history.
6. Read the tests beside each rule and change one rule temporarily to see a meaningful failure.
7. Run `mvnw verify` with Docker, then exercise three accounts end to end.
8. Read the Dockerfile/Compose, then the OpenShift examples and verification limitations.

Do not memorize annotations before you understand the data flow. Explain what a method protects and demonstrate it with a real request or test.
