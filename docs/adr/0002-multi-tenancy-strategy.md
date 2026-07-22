# ADR-0002: Shared-schema multi-tenancy with explicit `organization_id` scoping

**Status:** Accepted
**Date:** V7/V8 migrations (2025), hardened 2026-06-11 and 2026-06-26, documented 2026-07-22

## Context

BCM is multi-tenant SaaS: each `Organization` must never see another organization's contracts,
documents, users, or financial data. This has to hold both for normal application use and
under active attempt (an authenticated user of org A guessing IDs belonging to org B).

## Decision

Single shared MySQL schema. Every tenant-scoped table carries an `organization_id` foreign
key (retrofitted via V8, after the app initially had no multi-tenancy at all — see V7/V8).
Scoping is enforced explicitly in application code, not via an implicit ORM-level filter:

- `TenantContext` (a `ThreadLocal`) is populated from the JWT's `orgId` claim by
  `JwtAuthenticationFilter` on every authenticated request.
- Repositories expose explicit org-scoped query methods (`findByOrganization_Id`,
  `findByIdAndOrganization_Id`, ...) rather than relying on a global Hibernate `@Filter`.
- `ContractAccessGuard` (and equivalents for other resources) is the single funnel services
  use to look up a resource by ID, so the org check happens in one reviewable place rather
  than being re-implemented per service.

## Alternatives considered

- **Database-per-tenant.** Strongest isolation, rejected as operationally disproportionate for
  a solo-maintained app: 31 Flyway migrations would need to fan out to N tenant databases, and
  connection routing/pooling gets meaningfully more complex.
- **Schema-per-tenant** (one MySQL schema per org, same server). A middle ground; still
  rejected for the same migration fan-out problem, with less isolation benefit than
  database-per-tenant to justify the cost.
- **Implicit scoping via a Hibernate `@Filter`** enabled per-session from `TenantContext`.
  Rejected deliberately: an implicit, invisible filter is harder to verify by reading a service
  method (you have to know the filter exists and is enabled), and a misconfiguration or a
  native/JPQL query that bypasses the filter fails silently. Explicit `findByOrganization_Id`
  calls are visible in the diff, greppable, and directly unit-testable — see
  `CrossTenantIsolationIT` for real-database proof this scoping actually holds.

## Consequences

- **Positive:** one database to operate and back up, one migration path, tenant-scoping code
  is explicit and auditable in code review rather than implicit configuration.
- **Positive:** directly testable — both with mocked repositories (fast, e.g.
  `ContractAccessGuardTest`) and against a real MySQL instance
  (`CrossTenantIsolationIT`, added 2026-07-22).
- **Negative:** all tenants share one set of tables — a large tenant's data volume or query
  load can affect others sharing the same rows/indexes. There is no per-tenant scaling lever
  short of read replicas or eventually splitting out the largest tenants.
- **Negative:** correctness relies on every new query that touches tenant data going through
  an org-scoped method. `docs/SECURITY.md` documents the known fallback behavior when
  `TenantContext` is null (unscoped queries, intentional for scheduled batch jobs that iterate
  all organizations — would be a bug if it ever happened on an authenticated HTTP request).
