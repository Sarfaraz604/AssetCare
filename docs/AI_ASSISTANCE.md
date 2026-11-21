# AI assistance and validation

AI assisted with the initial implementation of the backend, migration, frontend, tests, local/container/cluster configuration and documentation. The workspace was initially empty; there was no existing application to reuse and no applicable AGENTS.md was found. The design followed the requested Java classes, constructor injection, Vue Options API and modular monolith structure.

This is assisted code, not evidence that the portfolio owner independently designed every detail. Read, run and explain the methods before presenting them in an interview. Configuration examples are not deployment results.

## Actual checks used

- Inspected the workspace, tools and applicable parent instruction paths.
- Checked stable dependency availability against Maven Central/npm metadata and official Spring/Vite documentation; pinned versions in the build and lockfile.
- Downloaded a local JDK 21 because the host initially supplied Java 17. A clean Java 21 `verify` build compiled application and tests, packaged the executable JAR and ran 34 unit/security tests successfully. The Java 17 preliminary check was not used as a substitute for final Java 21 verification.
- Ran the PostgreSQL Testcontainers phase: all 11 integration tests were explicitly skipped after Docker detection failed. No H2 substitution was made.
- Ran npm installation/production compilation and formatted the Vue/Java sources.
- Used installed Microsoft Edge through Playwright for a local browser check with **intercepted fixture API responses**. Checked login, navigation, an asset-form 409 retaining its inputs, mobile horizontal overflow and browser runtime exceptions. Inspected desktop/mobile screenshots. This was not a real backend/database E2E test.
- Checked the official Maven Wrapper and packaged resources; see VERIFICATION.md for exact results.

## Real issues found and corrected

- Windows PowerShell introduced a UTF-8 BOM when rewriting a Java test file. The compiler reported the illegal character; rewriting the file as UTF-8 without BOM fixed it.
- A test's `delete(any())` Mockito assertion was ambiguous because JpaSpecificationExecutor and CrudRepository overload `delete`. Giving the matcher the explicit `Asset.class` type resolved the compilation error.
- Wildcard imports made the test's Role enum ambiguous with Spring's `@Role`. Explicit imports removed the collision.
- Review found incorrectly escaped Java backslashes in the literal LIKE search helper generated through Python. Corrected the escaping before the successful compile. PostgreSQL behavior of these searches remains covered by a skipped integration test.
- Review found that conditionally rendering two different RouterView branches on session expiry could remount a form and lose input. The shell now keeps a single RouterView instance while the session state changes.
- Browser screenshots revealed question marks where a PowerShell/Python text round trip had replaced navigation symbols. HTML numeric entities repaired those symbols. The UI now uses local system fonts and requires no third-party font requests.
- The original status bar gave zero-count segments a tiny nonzero flex value. This was removed so zero values contribute no visible portion.

Build setup also encountered interrupted public downloads. Those were retried; Java and Maven were stored under ignored `.tools/`. One browser-smoke locator did not include the arrow in a back-link's accessible name; the test locator was corrected. Neither issue was presented as an application/database failure.

## What is still unverified

The PostgreSQL suite, real database application startup, full-stack browser workflow, Docker build/Compose health checks, OpenShift manifests on a cluster and GitHub Actions execution remain unverified. No real users, deployment, measured speedups or production-readiness claims are made. See README for known functional limitations.
