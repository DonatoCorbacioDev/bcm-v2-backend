# ADR-0007: Two-tier test strategy — fast H2 unit suite + Testcontainers/MySQL integration lane

**Status:** Accepted
**Date:** 2026-07-22

## Context

The unit suite (~1,230 tests) runs against H2 in "MySQL mode" for speed and to avoid requiring
Docker for the routine inner dev loop. That's the right trade-off for most of the suite, but
H2 is not MySQL: it has papered over real dialect differences before — V27 exists specifically
because a native MySQL `ENUM` column was never actually widened, a mismatch H2's more lenient
handling let slip past the unit suite entirely.

## Decision

Two tiers, run by two different Maven goals:

- **`mvn test`** (Surefire, default `*Test.java`/`*Tests.java`): the fast H2 suite, no Docker
  required. This is what gates routine development and CI's main feedback loop.
- **`mvn verify`** (Failsafe, `*IT.java`, added 2026-07-22): a small number of integration
  tests against a real MySQL 8.0 instance via Testcontainers
  (`support.AbstractMySQLIntegrationTest`), with Flyway actually enabled and
  `ddl-auto=validate` instead of `create-drop`. Currently two classes:
  `FlywayMigrationIT` (do all 31 migrations apply cleanly, and does every entity mapping
  still match the real schema) and `CrossTenantIsolationIT` (does tenant scoping hold against
  a real query plan and real foreign keys, not a mocked repository).

## Alternatives considered

- **H2 for everything.** Rejected: structurally can't catch real-MySQL-only issues. Confirmed
  the day this was written — wiring up real MySQL for the first time immediately surfaced a
  genuine Spring Boot circular-dependency issue (`spring.jpa.defer-datasource-initialization`
  conflicting with Flyway once actually enabled) and confirmed a real `NOT NULL` constraint
  (`business_areas.organization_id`) that the H2-backed unit tests never exercised.
- **Real MySQL for every test.** Rejected: would slow the routine `mvn test` inner loop from
  seconds to however long container startup takes per class, and would make Docker a hard
  requirement for running any test locally — too high a cost for the vast majority of tests,
  which don't touch anything MySQL-specific.

## Consequences

- **Positive:** the fast feedback loop that makes TDD/routine development pleasant is
  preserved untouched — `mvn test` timing and Docker-independence didn't change.
- **Positive:** the specific class of bug H2 can't catch (real schema/dialect drift, real
  constraint behavior) now has *some* coverage, and CI already runs `mvn verify`, so these
  execute automatically on every push without any CI config change.
- **Negative:** two test styles to maintain, and a Docker daemon is now a real local
  prerequisite for anyone who wants to run the full suite (documented in `CLAUDE.md`).
- **Negative:** only two integration tests exist so far — Flyway/schema validity and
  cross-tenant isolation, chosen as the highest-value slice to prove the pattern. An
  end-to-end test of a full user journey (create contract → upload document → approve →
  notify) is the natural next addition to this lane, not yet done.
