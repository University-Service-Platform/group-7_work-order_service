# ADR-0001: Sprint 1 shared-secret JWT trust model between service-request-service and work-order-service

**Status:** Accepted (interim), pending Group 5 identity-access-service integration

## Context
The assignment's cross-team dependency table requires Group 7 to consume user/role/department validation from Group 5's identity-access-service. At the time this Sprint 1 backend was built, Group 5's real identity-access-service and published API contract were not yet available to integrate against.

## Decision
service-request-service and work-order-service issue and verify their own JWTs locally using a shared placeholder secret (`usm.jwt.secret`) and a local `/api/dev/token` endpoint (gated by `usm.dev-tools.enabled`) for minting test tokens. This lets both services demonstrate full role-based authorization (STUDENT, ACADEMIC_STAFF, ADMIN_STAFF, SERVICE_DESK_OFFICER, TECHNICIAN, and an internal SERVICE role) end to end without depending on Group 5's delivery schedule.

## Alternatives considered
- Blocking Sprint 1 backend work until Group 5 publishes a stable identity API — rejected, since it would leave Group 7 with no demonstrable, testable backend by the deadline.
- Building a local mock of Group 5's service — considered as a future step (see Consequences) but not required for Sprint 1's internal-only trust boundary.

## Consequences
- Both services already isolate all JWT logic behind `JwtTokenService`/`JwtProperties`, so swapping to Group 5's real issuer only requires changing configuration (issuer, public key/secret source) and removing the local `/api/dev/token` endpoint — no controller or business-rule code needs to change.
- Until that swap happens, this is a known, documented limitation for the architecture write-up and demo: role-based access is fully enforced, but the identity behind each token is locally-issued, not federated from Group 5.
- Tracked as an open cross-team dependency, not a defect.
