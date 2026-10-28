# BCM API tests (Bruno)

A self-contained [Bruno](https://www.usebruno.com) collection that exercises
auth, role-based access control, and multi-tenant isolation over real HTTP
requests against a running backend — the same guarantees `CrossTenantIsolationIT`
and the role-based `@PreAuthorize` tests verify in Java, exercised here at
the HTTP layer instead.

It needs no pre-seeded data: it registers its own throwaway organizations
on the fly, so it runs against any backend with an empty database.

## Prerequisites

The backend running locally on `http://localhost:8090` (the `local`
environment's `baseUrl`) — either `mvn spring-boot:run` or the `backend`
service from `bcm-v2-docker`'s `docker compose up`.

## Running it

Open this folder (`bruno/`) as a collection in the Bruno app, select the
**local** environment (top-right dropdown), then run requests **in this
exact order**:

1. **Auth** — `Register Org A` → `Confirm Org A` → `Me` → `Refresh` → `Logout`
2. **Roles** — `Create Manager Profile` → `Invite Manager` →
   `Complete Invite` → `Login Manager A` →
   `Manager Forbidden Create Counterparty` (expect **403** —
   `@PreAuthorize("hasRole('ADMIN')")` blocking a MANAGER) →
   `Admin Creates Counterparty` (expect **201**)
3. **Tenant Isolation** — `Register Org B` → `Confirm Org B` →
   `Org B Cannot Read Org A Counterparty` (expect **404** — proves
   `TenantContext` scoping holds, not just a leaked 403)

## Registration needs the emailed code

Registration is two-step: `Register Org A`/`B` only sends a 6-digit code,
and `Confirm Org A`/`B` creates the organization with it. With the `dev`
profile no email leaves the machine — the backend log prints the code
(`Fake registration code email sent to: ... with code: NNNNNN`). Copy it into
the `registrationCodeOrgA` / `registrationCodeOrgB` variable before sending
the confirm request; the code is valid for 15 minutes.

## Known limitation: registration is one-time

`Register Org A` / `Register Org B` create a fresh organization + admin.
Re-running either a second time against the same database fails with a
duplicate-email/slug conflict — expected, not a bug. From then on, use
`Login Admin A` (Auth folder) / `Login Admin B` (Tenant Isolation folder)
instead to get a fresh access token for the same test accounts.

## No secrets committed

Access tokens are never written to disk: `script:post-response` blocks
store them with `bru.setVar(...)`, a runtime variable that only exists in
memory while Bruno is open. The `{{tokenAdminA}}`-style placeholders in
these files are literal text, not resolved values. Test accounts
(`*.bruno-test.local`, password `BrunoTest#123`) exist only in a local dev
database and are meaningless outside of `localhost`.
