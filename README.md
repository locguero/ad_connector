# AD Connector Module

Java/Spring Boot connector for Active Directory provisioning and deprovisioning,
designed as a pluggable module for a multi-trigger IAM integration framework.

## Architecture

```
upstream framework
      │  (Service DTOs only — no LDAP types cross this line)
      ▼
AdUserService ─ AdGroupService ─ AdOrgUnitService
      │                │
      │                └─ GroupDeactivationStrategy (STRIP_MEMBERSHIP default,
      │                   CONVERT_TO_DISTRIBUTION, MOVE_TO_QUARANTINE_OU)
      ▼
DnResolver / PagedSearchExecutor / EntryMappers / ReplicationRetrySupport
      ▼
AdConnectionRegistry ── routes by AdDomain (QA_ENT / DEV_ENT / AD_ENT)
      ▼
AdConnectionFactory ── one LDAPS/STARTTLS LDAPConnectionPool per domain,
                       per-domain JKS/PKCS12 truststore from mounted secrets
```

Wire it into a host application with:

```java
@Import(com.example.iam.ad.config.AdConnectorConfiguration.class)
```

## Key design decisions

- **Typed exceptions** — every UnboundID `LDAPException` passes through
  `LdapExceptionMapper`; callers only ever see `IamIntegrationException` and
  its subtypes (`ObjectNotFoundException`, `ObjectAlreadyExistsException`,
  `InsufficientAccessException`, `OperationTimeoutException`,
  `SizeLimitExceededException`).
- **objectGUID as durable key** — every DTO carries the canonical GUID string;
  `ObjectGuidConverter` handles AD's mixed-endian byte layout in both
  directions (string form is also usable in search filters via `AdObjectRef.byGuid`).
- **userAccountControl bitmask** — enable/disable is a read-modify-write of the
  `ACCOUNT_DISABLED` bit only; flags like `DONT_EXPIRE_PASSWORD` are never clobbered.
- **Paged searches** — all searches use `SimplePagedResultsControl` (default page
  size 500) and pin the page loop to a single pooled connection, because AD
  paging cookies are not valid across connections.
- **Replication lag** — `ReplicationRetrySupport` (Resilience4j) retries only
  `ObjectNotFoundException` with exponential backoff (500 ms → 8 s, 5 attempts).
  Applied to create-then-read and group-membership sequences.
- **Lazy pools** — a domain that is down never blocks startup; the Actuator
  health component `adConnectionPools` reports each domain separately
  (pool utilization + live RootDSE read).
- **Secrets** — bind credentials and truststore paths/passwords are resolved
  from environment variables (see `application.yml`); the factory refuses to
  start a domain without an explicit truststore (no trust-all fallback).

## Usage examples

```java
// Provision a user in QA, then grant a birthright group (lag-safe)
AdUser user = adUserService.createUser(new ProvisionUserRequest(
        AdDomain.QA_ENT, "Jordan Diaz", "jdiaz", "jdiaz@qa-ent.example.com",
        "Jordan", "Diaz", "Jordan Diaz", "jdiaz@example.com",
        "OU=Staff,DC=qa-ent,DC=example,DC=com",
        initialPassword, true, Map.of("department", List.of("IAM"))));

adUserService.addUserToGroup(new GroupMembershipRequest(
        AdDomain.QA_ENT,
        AdObjectRef.byGuid(user.objectGuid()),
        AdObjectRef.bySamAccountName("app-birthright")));

// Disable without touching other UAC flags
adUserService.disableUser(AdDomain.AD_ENT, AdObjectRef.bySamAccountName("jdiaz"));

// Paginated search — never truncated at AD's 1,000-object limit
List<AdUser> engineers = adUserService.searchUsers(
        UserSearchRequest.byFilter(AdDomain.DEV_ENT, "(department=Engineering)"));

// Deactivate a group with a non-default strategy
adGroupService.deactivateGroup(new DeactivateGroupRequest(
        AdDomain.AD_ENT, AdObjectRef.bySamAccountName("legacy-app-admins"),
        GroupDeactivationMode.MOVE_TO_QUARANTINE_OU));
```

## REST API & Swagger

The module ships a thin REST layer over the services ([web/](src/main/java/com/example/iam/ad/web/))
documented with springdoc-openapi:

- **Swagger UI:** `http://localhost:8080/swagger-ui.html`
- **OpenAPI JSON:** `http://localhost:8080/v3/api-docs`

Run standalone with `./gradlew bootRun` (connection pools are lazy, so the
API is browsable without reachable domain controllers). Endpoints are rooted
at `/api/ad/{domain}/...` where `{domain}` is `qa-ent`, `dev-ent`, or `ad-ent`.
Objects are addressed by `sAMAccountName` by default; pass
`refType=OBJECT_GUID` (recommended for automation) or `refType=DN`.

| Method | Path | Operation |
|--------|------|-----------|
| POST | `/api/ad/{domain}/users` | Provision user |
| GET | `/api/ad/{domain}/users` | Search users (paginated) |
| GET | `/api/ad/{domain}/users/{value}` | Get user (GUID + memberships) |
| PATCH | `/api/ad/{domain}/users/{value}/attributes` | Update arbitrary attributes |
| POST | `/api/ad/{domain}/users/{value}/enable` / `disable` | Flip only the UAC disabled bit |
| DELETE | `/api/ad/{domain}/users/{value}` | Deprovision user |
| POST/DELETE | `/api/ad/{domain}/groups/{value}/members/{userValue}` | Add/remove membership (idempotent, lag-safe) |
| POST | `/api/ad/{domain}/groups` | Create group |
| GET | `/api/ad/{domain}/groups` | Search groups (paginated) |
| POST | `/api/ad/{domain}/groups/{value}/deactivate` | Deactivate via strategy |
| DELETE | `/api/ad/{domain}/groups/{value}` | Delete group |
| GET | `/api/ad/{domain}/ous/exists?dn=...` | Verify OU exists |
| POST | `/api/ad/{domain}/objects/move` | Move object between OUs |

`RestExceptionHandler` maps the typed exceptions to problem-detail responses:
not-found → 404, already-exists → 409, insufficient-access → 403,
timeout → 504, size-limit → 400, other directory failures → 502.

## Configuration

See [application.yml](src/main/resources/application.yml). Per domain:
`host`, `port`, `use-start-tls`, `bind-dn`, `bind-password`, `base-dn`,
`quarantine-ou`, `truststore.{path,password,type}`, and pool sizing/timeouts.
Domain keys `qa-ent`, `dev-ent`, `ad-ent` bind to the `AdDomain` enum.

## Logging

Default output is Spring Boot's standard console pattern. Activating the
`json-logs` profile switches to structured JSON via `logstash-logback-encoder`
(an *optional* dependency of this module):

```bash
./gradlew bootRun --args='--spring.profiles.active=json-logs'
```

Connector logs pass user/group/domain as discrete arguments, which the
encoder emits as `arg0..argN` JSON fields alongside the formatted message.
Additionally, every operation emits one INFO completion event whose MDC
fields become first-class JSON fields — ideal for Splunk/ELK dashboards:

```json
{"message":"Operation getUser in domain QA_ENT completed in 5.390 ms (outcome=success)",
 "ad_operation":"getUser", "ad_domain":"QA_ENT", "ad_outcome":"success",
 "ad_duration_ms":"5.390", ...}
```
Host applications wanting JSON must declare the encoder themselves:

```xml
<dependency>
  <groupId>net.logstash.logback</groupId>
  <artifactId>logstash-logback-encoder</artifactId>
  <version>8.1</version>
  <scope>runtime</scope>
</dependency>
```

A host app with its own `logback-spring.xml` keeps full control — its file
takes precedence over the one shipped in this jar.

## Metrics

Every service-level operation is timed with Micrometer as
`ad.connector.operation`, tagged with `domain`, `operation` (`createUser`,
`searchGroups`, `moveObject`, ...) and `outcome` (`success` or the typed
exception's simple name, e.g. `ObjectNotFoundException`). Durations cover the
whole call as the caller experiences it — DN resolution, paging, and
replication-lag retries included — with p50/p95/p99 percentiles published.
The same duration and dimensions are also logged per call as MDC fields
(`ad_duration_ms` etc. — see [Logging](#logging)) for log-based dashboards.

Inspect standalone via the Actuator:

```
GET /actuator/metrics/ad.connector.operation
GET /actuator/metrics/ad.connector.operation?tag=domain:QA_ENT&tag=operation:createUser
```

Host apps that define a `MeterRegistry` bean (e.g. Actuator plus a Prometheus/
Datadog registry) get the timers there automatically; without one, recordings
fall back to Micrometer's `Metrics.globalRegistry`. REST-layer request timings
are additionally captured by Spring Boot's standard `http.server.requests`.

## Build & test

```bash
./gradlew test        # unit tests (GUID codec, UAC bitmask, operation timer)
./gradlew build       # plain jar (library module — no Spring Boot repackaging)
```

## Notes / possible next steps

- Integration tests against an in-memory directory (`InMemoryDirectoryServer`
  from the UnboundID SDK) or a containerized Samba AD.
- `memberOf` returns direct memberships only; use a matching-rule-in-chain
  filter (`1.2.840.113556.1.4.1941`) if nested resolution is needed.
