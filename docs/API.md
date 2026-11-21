# REST API

Base path `/api`, same-origin JSON responses. Login uses form encoding. Except for login/CSRF and minimal health, endpoints require a session. Every unsafe request, including login and logout, requires CSRF. No CORS configuration is needed for the one-origin production app or Vite proxy.

## Session sequence

1. `GET /api/auth/csrf` → 200 `{"token":"<session token>","headerName":"X-CSRF-TOKEN"}`. Keep the returned JSESSIONID cookie.
2. `POST /api/auth/login`, `Content-Type: application/x-www-form-urlencoded`, `X-CSRF-TOKEN: <token>`, body `username=admin&password=<your local password>` → 204 and authenticated session. Invalid credentials → 401.
3. Fetch `/api/auth/csrf` again after login because authentication invalidates the old token.
4. `GET /api/auth/me` → `{"id":1,"username":"admin","displayName":"Alex Morgan","role":"ADMIN"}`. Hashes are never included.
5. Send the latest token header on subsequent POST/PUT requests with the same cookie.
6. `POST /api/auth/logout` → 204; invalidates the session. Never use GET to log out.

For curl examples, use a cookie jar (`-c cookies.txt -b cookies.txt`) across requests. Obtain tokens from the response JSON; do not copy the example token literally. Keep temporary cookie jars out of source control.

## Endpoint table

| Method / path | Permission | Response |
| --- | --- | --- |
| GET `/assets` | All signed-in roles | Paged assets |
| GET `/assets/{id}` | All signed-in roles | Asset DTO |
| POST `/assets` | Admin | 201 asset DTO |
| PUT `/assets/{id}` | Admin, active/nonretired asset | 200 asset DTO |
| POST `/assets/{id}/retire` | Admin, no active work | 200 asset DTO |
| GET `/work-orders` | Role-scoped | Paged order DTOs |
| GET `/work-orders/{id}` | Admin / assigned tech / own requester-viewer | Order DTO |
| GET `/work-orders/{id}/history` | Same record scope as detail | History DTO array, oldest first |
| POST `/work-orders` | Admin or viewer | 201 order DTO, status OPEN |
| POST `/work-orders/{id}/assignment` | Admin, active order | Updated order DTO |
| POST `/work-orders/{id}/status` | Assigned tech for work progression; admin for cancel | Updated order DTO |
| GET `/technicians` | Admin | Technician user DTO array |
| GET `/dashboard` | Role-scoped orders, shared assets | Counts and 5 most recent visible orders |

Prefixes above are relative to `/api`. No deletion, public registration, password-reset or user-management endpoint exists.

## Lists and pagination

`GET /api/assets?search=pump&status=ACTIVE&category=Mechanical&page=0&size=15&sort=code&direction=asc`

Asset search matches name, code or location, ignoring case. Category is an exact, case-sensitive filter. Status/category/search filters combine. SQL LIKE metacharacters are escaped so `%` and `_` are literal search text.

Allowed asset sort fields: `id`, `code`, `name`, `category`, `location`, `status`, `createdAt`, `updatedAt`. Default `code,asc`.

`GET /api/work-orders?search=seal&status=OPEN&priority=HIGH&assetId=1&page=0&size=15&sort=createdAt&direction=desc`

Order search matches title, ignoring case. Allowed order sort fields: `id`, `title`, `priority`, `status`, `dueDate`, `createdAt`, `updatedAt`. Default `createdAt,desc`. Priority/status sorting follows stored enum text, not a special business rank. Null due dates follow PostgreSQL's normal sorting behavior. An ID tie-breaker makes pagination stable when primary sort values tie.

Page numbers are zero-based; size is 1–100. Negative page, invalid direction, oversized page and disallowed sort → 400 INVALID_PAGE. Unknown enum/date/number → 400 INVALID_INPUT. An out-of-range valid page returns empty content.

```json
{"content":[{"id":1,"code":"PUMP-1","name":"Water pump","category":"Mechanical","location":"Plant room","status":"ACTIVE","version":0,"createdAt":"2026-03-10T12:00:00Z","updatedAt":"2026-03-10T12:00:00Z"}],"totalElements":1,"totalPages":1,"number":0}
```

## Asset writes

`POST /api/assets`:

```json
{"code":"PUMP-1","name":"Water pump","category":"Mechanical","location":"Plant room","status":"ACTIVE"}
```

Code allows letters/digits/underscore/hyphen, max 40; saved uppercase. Name/location max 120; category max 80; required fields must not be blank. Create supports ACTIVE or UNDER_MAINTENANCE. Duplicate normalized code → 409 DATA_CONFLICT, enforced by PostgreSQL.

`PUT /api/assets/1` sends the same fields plus `"version":0`. ACTIVE/UNDER_MAINTENANCE can be edited. A retired asset is read-only. Retirement is a separate confirmed action:

`POST /api/assets/1/retire` with `{"version":0}`. Reject if an OPEN/IN_PROGRESS work order exists. The asset and its order/history records remain in the database.

## Work-order writes

`POST /api/work-orders`:

```json
{"assetId":1,"title":"Replace pump seal","description":"Water is leaking near the coupling.","priority":"HIGH","dueDate":"2026-03-15"}
```

Asset ID must be positive and refer to a nonretired asset. Title max 160, description max 4000, both nonblank. Priority LOW/MEDIUM/HIGH is required. `dueDate` can be null. The backend sets requester, OPEN, timestamps and version; clients cannot select a requester, status or role through this input.

Example response (IDs/times illustrative, not a claim about a running database):

```json
{"id":5,"assetId":1,"assetCode":"PUMP-1","assetName":"Water pump","title":"Replace pump seal","description":"Water is leaking near the coupling.","priority":"HIGH","status":"OPEN","requester":{"id":3,"username":"viewer","displayName":"Jordan Lee","role":"VIEWER"},"technician":null,"dueDate":"2026-03-15","completionNotes":null,"completedAt":null,"version":0,"createdAt":"2026-03-10T12:00:00Z","updatedAt":"2026-03-10T12:00:00Z"}
```

`POST /api/work-orders/5/assignment` with `{"technicianId":2,"version":0}`. Only an admin can do this, and user 2 must have TECHNICIAN role. `technicianId:null` unassigns. Active orders can be reassigned, including when IN_PROGRESS; the new assignee can complete that work. A repeated identical assignment returns a business-rule error. Terminal orders cannot be assigned.

`POST /api/work-orders/5/status` examples (always use the version returned by the most recent response):

```json
{"status":"IN_PROGRESS","version":1}
```

```json
{"status":"COMPLETED","completionNotes":"Replaced the seal and checked for leaks.","version":2}
```

Only the assigned technician can use those transitions. Admin cancellation: `{"status":"CANCELLED","version":1}`. Cancel requires OPEN or IN_PROGRESS. Completion notes max 4000 and must be nonblank; completion stores UTC completedAt. Completed/cancelled orders never reopen.

Every creation/status/assignment change writes history with the authenticated actor. No freeform request description or completion notes are copied into logs/history text. History entry example:

```json
{"id":8,"actor":"Sam Rivera","kind":"STATUS","oldValue":"OPEN","newValue":"IN_PROGRESS","createdAt":"2026-03-10T12:00:00Z"}
```

## Dashboard and errors

Dashboard shape: `{"totalAssets":3,"open":2,"inProgress":1,"overdue":1,"completed":4,"recent":[]}` (illustrative). Counts execute database queries with role scope. Overdue excludes null/today/future dates and all terminal orders; dates use the UTC calendar.

All application/security errors use:

```json
{"code":"VALIDATION","message":"Please correct the highlighted fields.","fieldErrors":{"title":"must not be blank"},"requestId":"<server-generated UUID>"}
```

| Status | Meaning |
| --- | --- |
| 400 | Validation, malformed JSON/type or invalid pagination |
| 401 | Login required or invalid credentials |
| 403 | Role/record permission denied or invalid/missing CSRF |
| 404 | Missing record/endpoint |
| 405 | Unsupported HTTP method |
| 409 | BUSINESS_RULE, DATA_CONFLICT, STALE_UPDATE or RECORD_BUSY |
| 500 | Unexpected error; no sensitive server details returned |

`STALE_UPDATE` means another change invalidated the submitted version. Keep your input, load the latest record and review before resubmitting. Never simply increment the version and blindly retry. Request IDs also appear in the `X-Request-ID` response header. Infrastructure/proxy failures may not have the application's JSON shape; the frontend handles those with a generic error.

Public operational endpoints: `/actuator/health`, `/actuator/health/liveness`, `/actuator/health/readiness`. They expose status only. Other Actuator paths are denied and not exposed.
