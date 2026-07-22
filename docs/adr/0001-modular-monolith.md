# ADR-0001: Modular monolith for the core app, not microservices

**Status:** Accepted
**Date:** 2025 (initial rewrite), documented retroactively 2026-07-22

## Context

BCM is built and operated by a single developer. The core domain (contracts, documents,
invoices, users, workflow, notifications) is a classic CRUD-heavy business application with
strong consistency requirements between its parts — e.g. deleting a contract must also
consistently handle its documents, invoices, and audit trail in one transaction.

## Decision

Ship the core application as a single Spring Boot deployable, organized by technical layer
(`controller/`, `service/`, `repository/`, `entity/`, `mapper/`) rather than split into
per-domain microservices (a `contracts-service`, `users-service`, `billing-service`, etc.).

The one deliberate exception is the ML/AI functionality, which *is* a separate service —
see [ADR-0004](0004-separate-ml-service.md) for why that specific slice was carved out
despite this default.

## Alternatives considered

- **Microservices per domain** (contracts, users, notifications as separate deployables).
  Rejected: at one developer and the current traffic profile, the operational cost
  (service discovery, distributed transactions or eventual consistency, N deployment
  pipelines, N sets of monitoring) is disproportionate to any actual scaling need. It would
  also make transactions that currently span "contract + its documents + its audit log" in
  one `@Transactional` service method into a distributed-transaction problem for no benefit.
- **Modulith with enforced module boundaries** (e.g. Spring Modulith, package-private
  cross-module access). Considered, not adopted yet — the current layer-by-technical-concern
  packaging (`service/`, `repository/`, ...) is simpler to navigate solo but doesn't enforce
  domain boundaries the way a modulith would. Worth revisiting if the codebase grows a team.

## Consequences

- **Positive:** one JVM to run locally, real ACID transactions across what would otherwise be
  service boundaries, one CI pipeline, one thing to deploy and monitor.
- **Negative:** the whole application shares a fate and a scaling profile — a slow query in
  one feature can affect request latency for unrelated features sharing the same JVM/thread
  pool. There's no way to scale a hot path (e.g. document upload) independently of a cold one
  (e.g. business area management) without scaling the whole app.
- **Negative:** package-by-layer instead of package-by-feature means a single feature's code
  (e.g. everything about budgets) is scattered across `controller/`, `service/`, `dto/`,
  `entity/`, `repository/` instead of colocated — a real navigability cost as the codebase
  grows (currently ~230 main source files).
