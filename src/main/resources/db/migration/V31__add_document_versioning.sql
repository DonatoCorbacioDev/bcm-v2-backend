-- Document versioning for redlining: a "version group" is a set of
-- ContractDocument rows representing successive uploads of the same
-- logical document. version_group_id is a self-referencing tag (not an
-- FK, since deleting one version must not cascade-delete its siblings)
-- pointing at the id of the first version uploaded in the group.
-- Stays nullable: for a brand-new upload the id doesn't exist until after
-- the insert, so the app inserts with version_group_id = NULL and then
-- updates it to its own id in a second statement.
ALTER TABLE contract_documents ADD COLUMN version_group_id BIGINT NULL;
UPDATE contract_documents SET version_group_id = id WHERE version_group_id IS NULL;

ALTER TABLE contract_documents ADD COLUMN version_number INT NOT NULL DEFAULT 1;
ALTER TABLE contract_documents ALTER COLUMN version_number DROP DEFAULT;

-- Cached PDFBox/OCR text, extracted once at upload time and reused for
-- the diff view so comparing two versions never has to re-run OCR.
-- Nullable: extraction is best-effort and older rows backfill lazily.
ALTER TABLE contract_documents ADD COLUMN extracted_text LONGTEXT NULL;

CREATE INDEX idx_contract_document_version_group ON contract_documents (version_group_id);
