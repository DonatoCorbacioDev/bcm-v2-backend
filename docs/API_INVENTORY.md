# API inventory

Every endpoint the backend serves under `/api/v1`, with who may call it (OWASP API9).
Generated from the running application by `ApiInventoryTest`, which fails when this file
and the code disagree: don't edit it by hand, regenerate it with
`mvn test -Dtest=ApiInventoryTest -Dapi.inventory.update=true`.

"No role" endpoints are public by nature or act on the caller's own account; each is
listed with its reason in `FunctionLevelAuthorizationTest` (OWASP API5). The full
OpenAPI schema is served at `/api/v1/api-docs` outside production.

139 endpoints.

| Path | Method | Access | Handler |
|---|---|---|---|
| `/agent/ask` | POST | `hasAnyRole('ADMIN','MANAGER')` | `MlProxyController.askAgent` |
| `/agent/insights` | GET | `hasAnyRole('ADMIN','MANAGER')` | `MlProxyController.getAgentInsights` |
| `/anomalies` | GET | `hasAnyRole('ADMIN','MANAGER')` | `MlProxyController.getAnomalies` |
| `/audit-logs` | GET | `hasRole('ADMIN')` | `AuditLogController.getAuditLogs` |
| `/auth/2fa/enroll/confirm` | POST | no role | `AuthController.enrollConfirm` |
| `/auth/2fa/enroll/setup` | POST | no role | `AuthController.enrollSetup` |
| `/auth/2fa/verify` | POST | no role | `AuthController.verifyTwoFactor` |
| `/auth/complete-invite` | POST | no role | `AuthController.completeInvite` |
| `/auth/forgot-password` | POST | no role | `AuthController.forgotPassword` |
| `/auth/login` | POST | no role | `AuthController.login` |
| `/auth/logout` | POST | no role | `AuthController.logout` |
| `/auth/me` | GET | no role | `AuthController.getCurrentUser` |
| `/auth/refresh` | POST | no role | `AuthController.refresh` |
| `/auth/reset-password` | POST | no role | `AuthController.resetPassword` |
| `/billing/checkout-session` | POST | `hasRole('ADMIN')` | `BillingController.createCheckoutSession` |
| `/billing/portal-session` | POST | `hasRole('ADMIN')` | `BillingController.createPortalSession` |
| `/billing/webhook` | POST | no role | `BillingController.webhook` |
| `/budgets/{id}` | DELETE | `hasRole('ADMIN')` | `BudgetController.deleteBudget` |
| `/budgets/{id}` | GET | `hasAnyRole('ADMIN', 'MANAGER')` | `BudgetController.getBudgetById` |
| `/budgets/{id}` | PUT | `hasRole('ADMIN')` | `BudgetController.updateBudget` |
| `/budgets` | GET | `hasAnyRole('ADMIN', 'MANAGER')` | `BudgetController.getAllBudgets` |
| `/budgets` | POST | `hasRole('ADMIN')` | `BudgetController.createBudget` |
| `/business-areas/{id}` | DELETE | `hasRole('ADMIN')` | `BusinessAreaController.deleteArea` |
| `/business-areas/{id}` | GET | `hasAnyRole('ADMIN', 'MANAGER')` | `BusinessAreaController.getAreaById` |
| `/business-areas/{id}` | PUT | `hasRole('ADMIN')` | `BusinessAreaController.updateArea` |
| `/business-areas` | GET | `hasAnyRole('ADMIN', 'MANAGER')` | `BusinessAreaController.getAllAreas` |
| `/business-areas` | POST | `hasRole('ADMIN')` | `BusinessAreaController.createArea` |
| `/calendar/{token}.ics` | GET | no role | `CalendarFeedController.getIcsFeed` |
| `/contract-history/contract/{contractId}` | GET | `hasAnyRole('ADMIN', 'MANAGER')` | `ContractHistoryController.getByContractId` |
| `/contract-history/{id}` | GET | `hasAnyRole('ADMIN', 'MANAGER')` | `ContractHistoryController.getById` |
| `/contract-history` | GET | `hasRole('ADMIN')` | `ContractHistoryController.getAll` |
| `/contract-templates/{id}/instantiate` | POST | `hasRole('ADMIN')` | `ContractTemplateController.instantiateTemplate` |
| `/contract-templates/{id}` | DELETE | `hasRole('ADMIN')` | `ContractTemplateController.deleteTemplate` |
| `/contract-templates/{id}` | GET | `hasRole('ADMIN') or hasRole('MANAGER')` | `ContractTemplateController.getTemplate` |
| `/contract-templates/{id}` | PUT | `hasRole('ADMIN')` | `ContractTemplateController.updateTemplate` |
| `/contract-templates` | GET | `hasRole('ADMIN') or hasRole('MANAGER')` | `ContractTemplateController.getTemplates` |
| `/contract-templates` | POST | `hasRole('ADMIN')` | `ContractTemplateController.createTemplate` |
| `/contracts/expiring` | GET | `hasAnyRole('ADMIN', 'MANAGER')` | `ContractController.getExpiringContracts` |
| `/contracts/export/excel` | GET | `hasAnyRole('ADMIN', 'MANAGER')` | `ContractController.exportContractsToExcel` |
| `/contracts/export/pdf` | GET | `hasAnyRole('ADMIN', 'MANAGER')` | `ContractController.exportContractsToPDF` |
| `/contracts/import/excel` | POST | `hasRole('ADMIN')` | `ContractController.importContractsFromExcel` |
| `/contracts/import/template` | GET | `hasRole('ADMIN')` | `ContractController.downloadImportTemplate` |
| `/contracts/search/semantic` | POST | `hasAnyRole('ADMIN','MANAGER')` | `SemanticSearchController.semanticSearch` |
| `/contracts/search` | GET | `hasAnyRole('ADMIN', 'MANAGER')` | `ContractController.search` |
| `/contracts/stats/by-area` | GET | `hasAnyRole('ADMIN', 'MANAGER')` | `ContractController.getContractsByArea` |
| `/contracts/stats/invoicing-summary` | GET | `hasAnyRole('ADMIN', 'MANAGER')` | `ContractController.getInvoicingSummary` |
| `/contracts/stats/timeline` | GET | `hasAnyRole('ADMIN', 'MANAGER')` | `ContractController.getContractsTimeline` |
| `/contracts/stats/top-managers` | GET | `hasAnyRole('ADMIN', 'MANAGER')` | `ContractController.getTopManagers` |
| `/contracts/stats` | GET | `hasAnyRole('ADMIN', 'MANAGER')` | `ContractController.getStats` |
| `/contracts/{contractId}/documents/{documentId}/analyze-clause-risk` | POST | `hasAnyRole('ADMIN','MANAGER')` | `ContractDocumentController.analyzeClauseRisk` |
| `/contracts/{contractId}/documents/{documentId}/diff/{otherDocumentId}` | GET | `hasAnyRole('ADMIN','MANAGER')` | `ContractDocumentController.diffVersions` |
| `/contracts/{contractId}/documents/{documentId}/download` | GET | `hasAnyRole('ADMIN','MANAGER')` | `ContractDocumentController.downloadDocument` |
| `/contracts/{contractId}/documents/{documentId}/extract` | POST | `hasAnyRole('ADMIN','MANAGER')` | `ContractDocumentController.extractText` |
| `/contracts/{contractId}/documents/{documentId}/terms` | GET | `hasAnyRole('ADMIN','MANAGER')` | `ContractDocumentController.readTerms` |
| `/contracts/{contractId}/documents/{documentId}/versions` | GET | `hasAnyRole('ADMIN','MANAGER')` | `ContractDocumentController.getVersions` |
| `/contracts/{contractId}/documents/{documentId}/versions` | POST | `hasAnyRole('ADMIN','MANAGER')` | `ContractDocumentController.uploadNewVersion` |
| `/contracts/{contractId}/documents/{documentId}` | DELETE | `hasRole('ADMIN')` | `ContractDocumentController.deleteDocument` |
| `/contracts/{contractId}/documents` | GET | `hasAnyRole('ADMIN','MANAGER')` | `ContractDocumentController.getDocuments` |
| `/contracts/{contractId}/documents` | POST | `hasAnyRole('ADMIN','MANAGER')` | `ContractDocumentController.uploadDocument` |
| `/contracts/{contractId}/invoices/match/recompute` | POST | `hasRole('ADMIN')` | `ElectronicInvoiceController.recomputeMatches` |
| `/contracts/{contractId}/invoices/{invoiceId}/download` | GET | `hasAnyRole('ADMIN','MANAGER')` | `ElectronicInvoiceController.downloadInvoice` |
| `/contracts/{contractId}/invoices/{invoiceId}/match/confirm` | POST | `hasRole('ADMIN')` | `ElectronicInvoiceController.confirmMatch` |
| `/contracts/{contractId}/invoices/{invoiceId}/match/reject` | POST | `hasRole('ADMIN')` | `ElectronicInvoiceController.rejectMatch` |
| `/contracts/{contractId}/invoices/{invoiceId}/payment-details` | PATCH | `hasAnyRole('ADMIN','MANAGER')` | `ElectronicInvoiceController.updatePaymentDetails` |
| `/contracts/{contractId}/invoices/{invoiceId}` | DELETE | `hasRole('ADMIN')` | `ElectronicInvoiceController.deleteInvoice` |
| `/contracts/{contractId}/invoices/{invoiceId}` | GET | `hasAnyRole('ADMIN','MANAGER')` | `ElectronicInvoiceController.getInvoice` |
| `/contracts/{contractId}/invoices` | GET | `hasAnyRole('ADMIN','MANAGER')` | `ElectronicInvoiceController.getInvoices` |
| `/contracts/{contractId}/invoices` | POST | `hasAnyRole('ADMIN','MANAGER')` | `ElectronicInvoiceController.uploadInvoice` |
| `/contracts/{contractId}/sepa-payments/{batchId}/download` | GET | `hasAnyRole('ADMIN','MANAGER')` | `SepaPaymentController.downloadPayment` |
| `/contracts/{contractId}/sepa-payments` | GET | `hasAnyRole('ADMIN','MANAGER')` | `SepaPaymentController.getPayments` |
| `/contracts/{contractId}/sepa-payments` | POST | `hasAnyRole('ADMIN','MANAGER')` | `SepaPaymentController.createSepaPayment` |
| `/contracts/{contractId}/workflow/approve` | POST | `hasAnyRole('ADMIN', 'MANAGER')` | `ContractWorkflowController.approve` |
| `/contracts/{contractId}/workflow/events` | GET | `hasAnyRole('ADMIN', 'MANAGER')` | `ContractWorkflowController.getEvents` |
| `/contracts/{contractId}/workflow/reject` | POST | `hasAnyRole('ADMIN', 'MANAGER')` | `ContractWorkflowController.reject` |
| `/contracts/{contractId}/workflow/submit` | POST | `hasAnyRole('ADMIN', 'MANAGER')` | `ContractWorkflowController.submitForReview` |
| `/contracts/{id}/generate-financial-values` | POST | `hasRole('ADMIN')` | `ContractController.generateFinancialValues` |
| `/contracts/{id}` | DELETE | `hasRole('ADMIN')` | `ContractController.deleteContract` |
| `/contracts/{id}` | GET | `hasAnyRole('ADMIN', 'MANAGER')` | `ContractController.getContractById` |
| `/contracts/{id}` | PUT | `hasRole('ADMIN')` | `ContractController.updateContract` |
| `/contracts` | GET | `hasRole('ADMIN')` | `ContractController.getAllContracts` |
| `/contracts` | POST | `hasRole('ADMIN')` | `ContractController.createContract` |
| `/counterparties/{id}/invoicing-summary` | GET | `hasAnyRole('ADMIN', 'MANAGER')` | `CounterpartyController.getInvoicingSummary` |
| `/counterparties/{id}/verified-iban` | PATCH | `hasRole('ADMIN')` | `CounterpartyController.confirmVerifiedIban` |
| `/counterparties/{id}` | DELETE | `hasRole('ADMIN')` | `CounterpartyController.deleteCounterparty` |
| `/counterparties/{id}` | GET | `hasAnyRole('ADMIN', 'MANAGER')` | `CounterpartyController.getCounterpartyById` |
| `/counterparties/{id}` | PUT | `hasRole('ADMIN')` | `CounterpartyController.updateCounterparty` |
| `/counterparties` | GET | `hasAnyRole('ADMIN', 'MANAGER')` | `CounterpartyController.getAllCounterparties` |
| `/counterparties` | POST | `hasRole('ADMIN')` | `CounterpartyController.createCounterparty` |
| `/financial-types/{id}` | DELETE | `hasRole('ADMIN')` | `FinancialTypeController.deleteType` |
| `/financial-types/{id}` | GET | `hasAnyRole('ADMIN', 'MANAGER')` | `FinancialTypeController.getTypeById` |
| `/financial-types/{id}` | PUT | `hasRole('ADMIN')` | `FinancialTypeController.updateType` |
| `/financial-types` | GET | `hasAnyRole('ADMIN', 'MANAGER')` | `FinancialTypeController.getAllTypes` |
| `/financial-types` | POST | `hasRole('ADMIN')` | `FinancialTypeController.createType` |
| `/financial-values/by-contract/{contractId}` | GET | `hasAnyRole('ADMIN', 'MANAGER')` | `FinancialValueController.getValuesByContract` |
| `/financial-values/{id}` | DELETE | `hasRole('ADMIN')` | `FinancialValueController.deleteValue` |
| `/financial-values/{id}` | GET | `hasAnyRole('ADMIN', 'MANAGER')` | `FinancialValueController.getValueById` |
| `/financial-values/{id}` | PUT | `hasAnyRole('ADMIN', 'MANAGER')` | `FinancialValueController.updateValue` |
| `/financial-values` | GET | `hasAnyRole('ADMIN', 'MANAGER')` | `FinancialValueController.getAllValues` |
| `/financial-values` | POST | `hasAnyRole('ADMIN', 'MANAGER')` | `FinancialValueController.createValue` |
| `/forecast` | GET | `hasAnyRole('ADMIN','MANAGER')` | `MlProxyController.getForecast` |
| `/home/attention` | GET | `hasAnyRole('ADMIN', 'MANAGER')` | `HomeController.getAttention` |
| `/internal/organizations/{id}/tier` | PUT | no role | `InternalOrganizationController.changeTier` |
| `/managers/{id}` | DELETE | `hasRole('ADMIN')` | `ManagerController.deleteManager` |
| `/managers/{id}` | GET | `hasRole('ADMIN')` | `ManagerController.getManagerById` |
| `/managers/{id}` | PATCH | `hasRole('ADMIN')` | `ManagerController.patchManager` |
| `/managers/{id}` | PUT | `hasRole('ADMIN')` | `ManagerController.updateManager` |
| `/managers` | GET | `hasAnyRole('ADMIN', 'MANAGER')` | `ManagerController.getAllManagers` |
| `/managers` | POST | `hasRole('ADMIN')` | `ManagerController.createManager` |
| `/notifications/{id}/read` | PATCH | `hasAnyRole('ADMIN', 'MANAGER')` | `NotificationController.markAsRead` |
| `/notifications` | GET | `hasAnyRole('ADMIN', 'MANAGER')` | `NotificationController.getNotifications` |
| `/notifications` | POST | `hasAnyRole('ADMIN', 'MANAGER')` | `NotificationController.createReminder` |
| `/organizations/me/deletion/confirm` | POST | `hasRole('ADMIN')` | `OrganizationController.confirmDeletion` |
| `/organizations/me/deletion` | DELETE | `hasRole('ADMIN')` | `OrganizationController.cancelDeletion` |
| `/organizations/me/deletion` | POST | `hasRole('ADMIN')` | `OrganizationController.requestDeletion` |
| `/organizations/me/export` | GET | `hasRole('ADMIN')` | `OrganizationController.exportMyOrganization` |
| `/organizations/me/usage` | GET | `hasRole('ADMIN')` | `OrganizationController.getMyOrganizationUsage` |
| `/organizations/me` | GET | `hasRole('ADMIN')` | `OrganizationController.getMyOrganization` |
| `/organizations/me` | PUT | `hasRole('ADMIN')` | `OrganizationController.updateMyOrganization` |
| `/organizations/register/confirm` | POST | no role | `OrganizationController.confirmRegistration` |
| `/organizations/register/resend` | POST | no role | `OrganizationController.resendRegistrationCode` |
| `/organizations/register` | POST | no role | `OrganizationController.register` |
| `/risk-feedback/contracts/{contractId}` | POST | `hasAnyRole('ADMIN','MANAGER')` | `RiskFeedbackController.submitFeedback` |
| `/risk-feedback` | GET | `hasAnyRole('ADMIN','MANAGER')` | `RiskFeedbackController.getFeedback` |
| `/risk-scores` | GET | `hasAnyRole('ADMIN','MANAGER')` | `MlProxyController.getRiskScores` |
| `/roles` | GET | `hasRole('ADMIN')` | `RoleController.getAllRoles` |
| `/users/invite` | POST | `hasRole('ADMIN')` | `UserController.invite` |
| `/users/me/2fa/confirm` | POST | no role | `TwoFactorAuthController.confirm` |
| `/users/me/2fa/disable` | POST | no role | `TwoFactorAuthController.disable` |
| `/users/me/2fa/setup` | POST | no role | `TwoFactorAuthController.setup` |
| `/users/me/2fa/status` | GET | no role | `TwoFactorAuthController.status` |
| `/users/me/calendar-feed/regenerate` | POST | no role | `CalendarFeedController.regenerateFeedUrl` |
| `/users/me/calendar-feed` | GET | no role | `CalendarFeedController.getFeedUrl` |
| `/users/me/password` | POST | `isAuthenticated()` | `UserController.changeOwnPassword` |
| `/users/{id}` | DELETE | `hasRole('ADMIN')` | `UserController.deleteUser` |
| `/users/{id}` | GET | `hasRole('ADMIN')` | `UserController.getUserById` |
| `/users/{id}` | PATCH | `hasRole('ADMIN')` | `UserController.patchUser` |
| `/users/{id}` | PUT | `hasRole('ADMIN')` | `UserController.updateUser` |
| `/users` | GET | `hasRole('ADMIN')` | `UserController.getAllUsers` |
| `/users` | POST | `hasRole('ADMIN')` | `UserController.createUser` |
