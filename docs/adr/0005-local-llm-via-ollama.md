# ADR-0005: Ollama (local LLM) instead of a cloud LLM provider

**Status:** Accepted
**Date:** 2026-07 (semantic search + clause-risk analysis), documented 2026-07-22

## Context

Two features need an LLM: clause-risk analysis (flagging risky contract clauses) and
embedding generation for semantic document search. The input in both cases is contract text —
customer names, financial terms, legal clauses — for an Italian SMB target market, which
brings GDPR squarely into the decision, not just capability/cost.

## Decision

Run Ollama locally (on the host in dev, on the deployment host in prod) as the LLM backend.
The Spring Boot backend calls it directly via Spring AI for embeddings
(`nomic-embed-text`, `SemanticSearchService`); the ML service proxies clause-risk analysis
through it. No contract text is sent to a third-party API for either feature.

## Alternatives considered

- **Cloud LLM APIs** (OpenAI, Anthropic, etc.). Rejected as the default: sending customer
  contract text — commercial terms, counterparty names, sometimes financial figures — to a
  third-party US-based processor is a real GDPR question for this target market, not a
  hypothetical one. It would require a data processing agreement, a sub-processor disclosure,
  and a defensible answer to "where does our contract data go" that "nowhere, it's local" does
  not need.
- **Cost** was a secondary factor, not the primary one: local inference has a fixed hardware
  cost rather than per-token billing, which matters for a pre-revenue solo project, but the
  data-residency argument would hold even without it.

## Consequences

- **Positive:** GDPR-safe by construction — no data processing agreement or sub-processor
  question to answer for this specific capability.
- **Positive:** no per-request cost, no rate limits imposed by a third party.
- **Negative:** local models (embedding and generation) are meaningfully behind frontier cloud
  models in quality. This is an accepted trade-off, not an oversight — see
  [ADR-0006](0006-mysql-in-memory-vector-search.md) for how the search feature's current scope
  (small per-tenant document counts) makes this acceptable for now.
- **Negative:** local inference availability depends on the deployment host having Ollama
  running and the model pulled — less reliable than a managed API's SLA. This is why
  `SemanticSearchService.generateAndStoreEmbedding` is explicitly best-effort: a failure here
  (Ollama unreachable, model not pulled) logs a warning and does not fail the request it rides
  along with.
- **Negative:** requires the deployment host to have enough CPU/GPU capacity to run inference,
  a real constraint when choosing where to deploy (Oracle Cloud Free Tier ARM instances have
  no GPU — inference runs on CPU, which is workable for the current scale but a real limit if
  usage grows).
