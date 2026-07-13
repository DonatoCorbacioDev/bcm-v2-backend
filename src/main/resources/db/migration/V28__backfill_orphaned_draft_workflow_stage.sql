-- Contracts edited to DRAFT before the updateContract workflowStage fix
-- (ContractService.java, "apply manager and business area changes on
-- contract update") were left with status=DRAFT but workflow_stage=NULL
-- forever, since that fix only derives workflow_stage on a transition INTO
-- DRAFT, not for rows already sitting at DRAFT. One-time repair for any such
-- orphaned rows, mirroring the same backfill V26 did at its own deploy time.
UPDATE contracts SET workflow_stage = 'DRAFT' WHERE status = 'DRAFT' AND workflow_stage IS NULL;
