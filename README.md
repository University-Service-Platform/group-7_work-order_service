# work-order-service

USM-G7 Sprint 1 - owns the `WorkOrder` entity end to end: creation against a
triaged `ServiceRequest`, technician start/progress/resolution, and the
callback that keeps the parent request's status in sync. Backend Dev 2's half
of Group 7's two-service split (see the group's *Sprint 1 Backend Developer
Guide*, particularly §§2-7, which this codebase was built from).

Its sibling service is `service-request-service` (Backend Dev 1's half).
This service calls it over REST - never a shared database, never a
cross-schema join.

## Tech stack

- Java 17, Spring Boot 3.2.5
- Spring Web, Spring Data JPA, Spring Security (stateless JWT filter)
- Spring's `RestClient` for the cross-service call (chosen over the older
  `RestTemplate` because it supports HTTP PATCH out of the box)
- MySQL 8.0 (Flyway-versioned schema, no Hibernate auto-DDL)
- springdoc-openapi (Swagger UI)
- JUnit 5 + Mockito + AssertJ for tests

## Running locally

**Run `service-request-service` first (or alongside) - work order creation,
start, and resolution all call it.** See its README for how to start it; by
default it's expected at `http://localhost:8081` (see `services.service-request.base-url`
in `application.yml`).

1. Start the local MySQL container (see the guide, §14 Phase 0 step E):

   ```
   docker run --name g7-workorder-db -e MYSQL_ROOT_PASSWORD=devpassword \
     -e MYSQL_DATABASE=work_order_db -p 3307:3306 -d mysql:8.0
   ```

2. Run the service:

   ```
   mvn spring-boot:run
   ```

3. Confirm it's up:

   ```
   curl http://localhost:8082/actuator/health
   ```

4. Swagger UI: http://localhost:8082/swagger-ui.html

### Environment / config summary (for QA/DevOps's Dockerfile + Compose)

| Item | Value |
| --- | --- |
| Port | 8082 |
| Health path | `/actuator/health` |
| DB connection | `spring.datasource.*` in `application-dev.yml` |
| Migrations | Flyway, runs automatically on boot |
| Active profile | `dev` |
| Depends on | `service-request-service` reachable at `services.service-request.base-url` |
| Build command | `mvn clean package` |
| Test command | `mvn test` (Mockito - no live DB or live sibling service required) |
| Run command | `java -jar target/work-order-service-0.1.0-SPRINT1.jar` |

### Minting a test JWT (dev profile only)

Same mechanism as `service-request-service` - see that service's README.
`role` must be one of `STUDENT`, `ACADEMIC_STAFF`, `ADMIN_STAFF`,
`SERVICE_DESK_OFFICER`, `TECHNICIAN`, `SERVICE`. For this service you'll
mostly want `SERVICE_DESK_OFFICER` (to create work orders) and `TECHNICIAN`
(to start/progress/resolve them) - and the `assignedTechnicianId` you create
a work order with must match the `userId` you mint the technician's token
with, or the ownership checks (below) will reject it.

## API summary

Guide §4.2 has the full story/FR/role mapping. Full request/response schemas
are in Swagger.

| Method & path | Purpose | Who |
| --- | --- | --- |
| `POST /api/work-orders` | Create a work order for a triaged request | Service Desk Officer |
| `GET /api/work-orders?technicianId=&status=` | Technician workspace / queue | Technician (own), Service Desk (all) |
| `GET /api/work-orders/{id}` | Detail | Assigned technician or Service Desk |
| `PATCH /api/work-orders/{id}/start` | Sets In Progress + start time | Assigned technician only |
| `PATCH /api/work-orders/{id}/progress` | Append action note | Assigned technician only |
| `PATCH /api/work-orders/{id}/resolution` | Record resolution, sets Resolved, calls back to service-request-service | Assigned technician only |
| `GET /api/work-orders/summary?groupBy=` | Workload summaries | Service Desk Officer, Admin Staff |
| `GET /api/work-orders/by-request/{requestId}` *(internal)* | Lets service-request-service or Group 8 check completion status | Service-to-service only - **exclude from the API gateway route table** |

## Business rules enforced in code (guide §5)

- **BR-06** - `create()` calls `ServiceRequestClient.fetchRequest(...)` first; refuses to create a work order unless the parent request's status is `ACKNOWLEDGED` or `ESCALATED` (i.e. triaged, not rejected/cancelled/new/already resolved). Also refuses a second *active* work order for the same request (an extra guard beyond the guide's explicit list - flag/adjust with your Tech Lead if the team wants to allow concurrent work orders per request).
- **BR-07** - `/progress` always targets one specific `workOrderId` and requires the order to already be `IN_PROGRESS` (i.e. `/start` was called first) - never a bare status flip.
- **BR-08** - `/resolution` rejects a blank `resolution` twice (DTO `@NotBlank` *and* a defensive service-layer check).
- **BR-09** - every status change and its timestamp are set together in one `WorkOrder` method (`start`, `resolve`) - never a nullable column filled in later.
- **Ownership** (this service's equivalent of BR-10) - only the *assigned* technician can `/start`, `/progress`, or `/resolution` their own work order; Service Desk Officer cannot act on a technician's behalf on those three endpoints, matching guide §4.2's "who can call it" column exactly.

## The cross-service integration (guide §10 step 3)

`create()`, `start()`, and `resolve()` all call back into
`service-request-service` via `ServiceRequestClient`:

- `create` -> validates the request is triaged, then pushes `status=ASSIGNED`.
- `start` -> pushes `status=IN_PROGRESS`.
- `resolve` -> pushes `status=RESOLVED`.

This is genuinely the first real cross-service test in the whole sprint - run
both services locally at once and exercise the full flow (guide §10 step 3).
If the push call fails after this service's own state already changed, the
current code surfaces an error (`UpstreamServiceException` -> HTTP 502) but
does **not** roll back the local change or retry automatically - there's no
saga/compensation logic in Sprint 1. That's a known, honest limitation worth
naming in your architecture walkthrough, not a bug to silently work around.

### Service-to-service auth

`ServiceRequestClientImpl` mints its own short-lived JWT (`role=SERVICE`)
signed with the **same placeholder secret** as `service-request-service`,
and `service-request-service`'s internal `/status` endpoint (and its regular
GET-by-id, used implicitly during validation) accepts that role. This is a
Sprint 1 simplification, not a real trust boundary - both services currently
share one secret. Once Group 5 or the API Gateway team defines real
service-to-service auth, only `JwtProperties`/`JwtTokenService`/
`ServiceRequestClientImpl` here (and the equivalent files in
`service-request-service`) need to change.

## Placeholder-and-swap items (guide §7 / §13)

| Placeholder today | File(s) | Swap when |
| --- | --- | --- |
| JWT signing key + claim names | `application.yml` (`usm.jwt.*`), `JwtTokenService` | Group 5 confirms real claim names/signing config |
| `service-request-service` base URL = `localhost:8081` | `application.yml` (`services.service-request.base-url`) | QA/DevOps's Docker Compose gives it a service name on the shared network |
| Status names as plain strings in `ServiceRequestClient`/`WorkOrderServiceImpl` | `TRIAGEABLE_REQUEST_STATUSES` constant | Tech Lead locks the final `RequestStatus` names |
| Response envelope / error shape | `GlobalExceptionHandler`, `ApiError` | API Gateway team agrees a shared shape |

## Tests

```
mvn test
```

`WorkOrderServiceImplTest` covers BR-06/07/08/09 and the technician-ownership
rule using Mockito (the `ServiceRequestClient` is mocked - no live sibling
service needed to run tests). `JwtTokenServiceTest` covers the JWT round trip.

Add tests for every new endpoint/rule as you build it (guide §15 Phase 3).

## AI assistance disclosure

Initial project scaffolding, entity/DTO/repository/controller/service
skeletons, the JWT filter pattern, the `ServiceRequestClient` cross-service
call, first-draft unit tests, and this README were drafted with AI
assistance from a prompt built on this project's own Sprint 1 BA report and
Backend Developer Guide, then reviewed, run, and adapted here. Update this
section (and your PR descriptions) with specifics as you extend the code.

## What's still open (do these yourself - see guide §14)

- Confirm real JWT claim names with Group 5.
- Get the final status enum names signed off by your Tech Lead (this service
  and `service-request-service` must agree on the exact strings exchanged
  over `pushStatusUpdate`).
- Decide, with your Tech Lead, what (if anything) drives `WorkOrderStatus.CLOSED`
  / `closure_time` in a later sprint - no endpoint builds it yet.
- Agree the API Gateway base path / response envelope.
- Wire this service into the real Jira board and open your first PR - see
  guide §15 Phase 4.
