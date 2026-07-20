-- Semantic search over contract documents (Spring AI + Ollama embeddings).
-- Nullable: only documents that have gone through "Analizza con AI" (text
-- extraction) get an embedding, generated as a byproduct of that existing
-- action rather than a separate indexing step.
ALTER TABLE contract_documents ADD COLUMN embedding JSON NULL;
