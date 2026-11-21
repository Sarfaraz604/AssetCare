# Resume points

Four bullets based on implemented code:

- Built AssetCare, a Java 21/Spring Boot and Vue 3 maintenance manager with PostgreSQL/Flyway, REST DTOs and server-side search, filtering and pagination.
- Implemented session authentication, BCrypt password hashing, CSRF token handling and role/record permissions for admins, technicians and requesters.
- Added controlled work-order transitions, assignment/status history, transactional updates and optimistic locking with HTTP 409 conflict handling.
- Wrote JUnit/Mockito/Spring security tests and PostgreSQL Testcontainers tests, plus a multi-stage Docker build, Compose, CI and example OpenShift manifests.

## Verified locally

34 backend unit/security tests passed under Java 21. Vue's production build passed. A browser UI check with mocked API responses passed. This is not evidence of a live database workflow.

## Authored, not operationally verified

All 11 PostgreSQL integration tests were skipped because Docker is unavailable. Container and OpenShift configuration has not been executed. The GitHub Actions workflow was authored but not run on GitHub. Use “added container/cluster configuration,” not “deployed to OpenShift.” Do not add users, traffic, uptime, performance percentages or production-readiness claims without new evidence.
