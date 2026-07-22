# Architecture Decision Records

Short records of the non-obvious architectural decisions in this project: what was chosen,
what else was considered, and why. These exist so the reasoning survives past the commit
that made the decision — useful for anyone reading the code later (including future me),
and for explaining trade-offs in a technical interview without reconstructing them from memory.

Each ADR is a snapshot as of the date it was written or last revised — if a decision changes,
add a new ADR that supersedes the old one rather than editing history away.

| ADR | Decision |
|---|---|
| [0001](0001-modular-monolith.md) | Modular monolith for the core app, not microservices |
| [0002](0002-multi-tenancy-strategy.md) | Shared-schema multi-tenancy with explicit `organization_id` scoping |
| [0003](0003-jwt-refresh-token-rotation.md) | Short-lived JWT + hashed, rotating refresh token in an `HttpOnly` cookie |
| [0004](0004-separate-ml-service.md) | Separate FastAPI service for ML/AI, not embedded in the Java monolith |
| [0005](0005-local-llm-via-ollama.md) | Ollama (local LLM) instead of a cloud LLM provider |
| [0006](0006-mysql-in-memory-vector-search.md) | MySQL + in-memory cosine similarity instead of a vector database |
| [0007](0007-two-tier-test-strategy.md) | Two-tier test strategy: fast H2 unit suite + Testcontainers/MySQL integration lane |
