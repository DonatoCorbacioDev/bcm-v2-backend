# ADR-0006: MySQL + in-memory cosine similarity instead of a vector database

**Status:** Accepted, expected to be revisited (see Consequences)
**Date:** 2026-07-20 (semantic document search introduced), documented 2026-07-22

## Context

Semantic search over contract documents needs to store an embedding per document and find the
most similar ones to a query at search time. The project is already standardized on MySQL 8.0
([ADR-0002](0002-multi-tenancy-strategy.md) assumes one relational database for everything),
and per-tenant document volume is currently small — dozens to low hundreds, not millions.

## Decision

Store each document's embedding as a JSON-serialized float array in a MySQL column
(`contract_documents.embedding`, V29). At search time, load every embedding for the
authenticated tenant (`findByOrgIdAndEmbeddingIsNotNull`), compute cosine similarity against
the query embedding in Java, and sort. No dedicated vector store, no approximate-nearest-
-neighbor index.

## Alternatives considered

- **pgvector.** Would mean adding PostgreSQL to the stack for one feature, when the rest of
  the application is MySQL. Rejected as a large blast-radius decision (a second database
  engine to operate, back up, and monitor) for a feature whose current data volume doesn't
  need it.
- **A dedicated vector database** (Pinecone, Weaviate, Milvus, Qdrant). Rejected as premature
  infrastructure: another service to deploy, secure, and keep available, adopted ahead of any
  evidence the in-memory approach is actually a bottleneck.
- **MySQL's native `VECTOR` type.** Not available — that's MySQL 9.x; this project targets
  MySQL 8.0.

## Consequences

- **Positive:** zero new infrastructure. The feature reuses the database, the tenant-scoping
  pattern ([ADR-0002](0002-multi-tenancy-strategy.md)), and the backup/ops story that already
  exist for everything else.
- **Negative — the honest one:** this does not scale past a certain per-tenant document count.
  Brute-force cosine similarity over every embedding in memory is O(n) per query with no index;
  it is the right choice at today's volume and the wrong one well before "millions of
  documents." This is the first thing to revisit if a tenant's document count grows
  significantly — likely pgvector or a dedicated vector store at that point, not a MySQL-native
  fix.
- **Negative:** each document currently gets exactly one embedding for its first
  `MAX_EMBEDDING_INPUT_CHARS` (6,000) characters — no chunking, so a long document's later
  sections are invisible to search and there's no way to point back to *where* in the document
  a match came from. Chunking (split → embed per section → search → return the matching
  section, not just the whole document) is the natural next step for this feature, independent
  of the storage question above.
