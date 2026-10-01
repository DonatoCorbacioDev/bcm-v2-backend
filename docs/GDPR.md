# GDPR — registro dei trattamenti e valutazione d'impatto

Questo documento formalizza il ragionamento GDPR applicato a BCM finora sparso
tra varie sessioni di sviluppo. Riflette lo stato del codice come verificato il
2026-07-16, con un controllo di dettaglio aggiuntivo su log, email ed export
il 2026-09-30 (sezioni 2 e 5, gap in fondo alla sezione 9), e con due di quei
gap chiusi il 2026-10-01 (controllo IBAN verificato + retention file SEPA,
sezioni 5 e 9) — non è una dichiarazione aspirazionale, va aggiornato ogni
volta che cambia un controllo. Non sostituisce una consulenza legale: è la
base tecnica su cui un legale può lavorare, non il documento finale.

## 1. Titolare e finalità del trattamento

BCM (Business Contracts Manager) è un software multi-tenant per la gestione
del ciclo di vita dei contratti aziendali (scadenze, valori finanziari,
documenti, fatture, pagamenti SEPA, analisi del rischio contrattuale).

Ogni organizzazione che usa BCM è **titolare autonoma** dei propri dati
(contratti, controparti, dipendenti). Chi opera/distribuisce BCM come
software è **responsabile del trattamento** (data processor) per conto delle
organizzazioni clienti.

## 2. Categorie di dati personali trattati

| Categoria | Dove | Esempi di campi |
|---|---|---|
| Dati identificativi di dipendenti/responsabili (`managers`) | DB `bcm` | nome, cognome, email, telefono, reparto |
| Credenziali utente (`users`) | DB `bcm` | username (email), hash bcrypt della password, secret TOTP cifrato AES-GCM |
| Dati di controparti contrattuali | DB `bcm`, colonna `customer_name` su `contracts` | ragione sociale (raramente persona fisica) |
| Coordinate bancarie | DB `bcm` (`organizations.iban`/`bic`, `electronic_invoices.supplier_iban`/`bic`) | IBAN/BIC dell'organizzazione titolare **e** del fornitore indicato in ogni fattura elettronica caricata — quest'ultimo può coincidere con una persona fisica se il fornitore è una ditta individuale, non è quindi sempre "dato di sola organizzazione" come indicato in precedenza. **Non è mai mascherato**: restituito per intero da `OrganizationDTO`/`ElectronicInvoiceDTO`, modificabile per intero, visualizzato per intero in `organization/page.tsx` e `InvoicesTab.tsx` |
| Contenuto documenti contrattuali | Filesystem (`uploads/`), mai nel DB | testo libero nei PDF caricati dall'utente — può contenere qualunque dato personale il contratto stesso contenga |
| File di pagamento SEPA generati | Filesystem, via `LocalStorageService.storeSepaPayment` (`SepaPaymentService`) | XML `pain.001.001.03` con IBAN/BIC del titolare e del fornitore incorporati in chiaro — vedi retention in sezione 5 |
| Log applicativi | Tabella `audit_logs` (pulita) + log applicativo testuale (non filtrato) | `audit_logs`: azione, tipo entità, id entità, username, org id — **mai il corpo del documento o i valori modificati**. Il log applicativo testuale invece riporta in chiaro email/nome del responsabile in alcuni punti (`ContractSchedulerService`, `DummyEmailService`, `MonthlyReporter`) — non è un dato strutturato consultabile dall'interessato, ma resta un'esposizione da considerare per la retention dei log di sistema (oggi non coperta da `AuditLogRetentionService`, che riguarda solo `audit_logs`) |
| Notifiche email (scadenze, digest settimanale) | Corpo email inviata via SMTP | Nome e cognome del responsabile destinatario, nomi di contratti/controparti — uso previsto e proporzionato alla finalità (notificare la persona giusta), non richiede azione |

Non vengono trattate categorie particolari di dati (art. 9 GDPR — salute,
origine etnica, orientamento, ecc.) come parte del modello dati previsto;
resta possibile che compaiano incidentalmente nel testo libero di un
contratto caricato dall'utente, sulla stessa base giuridica del contratto
stesso.

## 3. Base giuridica

- Dati di dipendenti/responsabili e credenziali: esecuzione del rapporto di
  lavoro/contratto con l'organizzazione cliente (art. 6.1.b).
- Documenti contrattuali e relativo contenuto: esecuzione del contratto tra
  l'organizzazione cliente e la propria controparte (art. 6.1.b), BCM è mero
  strumento di gestione.
- Log di audit: legittimo interesse alla sicurezza e tracciabilità (art. 6.1.f).

## 4. Sotto-responsabili (sub-processor) e trasferimenti extra-UE

| Servizio | Ruolo | Dati che riceve | Extra-UE? |
|---|---|---|---|
| Ollama (LLM per estrazione clausole a rischio e agente di analisi) | Self-hosted, container `bcm-ml`/locale | Testo del documento, inviato solo per la singola richiesta | **No** — gira nell'infrastruttura del titolare/responsabile, nessun dato lascia il perimetro. Nessun sub-processor terzo coinvolto. |
| Tesseract OCR | Self-hosted, stesso container backend | Immagine della pagina scansionata | **No** — locale, nessuna chiamata esterna |
| SMTP (invio email: inviti, notifiche, digest settimanale) | Provider configurato da `MAIL_HOST`/`MAIL_PORT` (Gmail SMTP di default in `.env.example`, sostituibile) | Indirizzo email destinatario, contenuto della notifica | Dipende dal provider scelto in produzione — **da specificare per ogni deployment**, non è un valore fisso di BCM |

Non ci sono chiamate a servizi SaaS terzi (nessun Textract, nessun Bedrock,
nessuna AI di terze parti) — è stata una decisione esplicita del progetto
(vedi cronologia commit "AWS removed, PDFBox + Ollama integrated").

## 5. Conservazione e cancellazione

- **Documenti**: nessuna scadenza automatica; cancellati esplicitamente
  tramite `DELETE /contracts/{id}/documents/{docId}` (cascata su riga DB +
  file fisico via `LocalStorageService`).
- **File di pagamento SEPA** (`SepaPaymentService`, contengono IBAN/BIC in
  chiaro del titolare e del fornitore): purge automatico giornaliero (job
  schedulato alle 3:30, `SepaPaymentRetentionService`, dal 2026-10-01) delle
  righe/file più vecchi di `SEPA_PAYMENT_RETENTION_DAYS` (default 730 giorni
  / 2 anni, in linea con la conservazione fiscale italiana dei documenti di
  pagamento — più lungo dei 180 giorni degli audit log perché è un dato di
  natura diversa). Prima di questa data non c'era alcuna scadenza.
- **Utenti/responsabili**: cancellabili tramite `DELETE /users/{id}` e
  `DELETE /managers/{id}`. Lo storico contratti (`contract_history`) resta
  per obblighi fiscali/contrattuali: dalla V34, cancellare l'utente
  anonimizza il riferimento (`modified_by` → `NULL`) invece di bloccare la
  cancellazione, la riga di storico (cosa è cambiato, quando) resta intatta.
  Gli `audit_logs` storici mantengono lo `username` come testo libero (non
  è un riferimento FK, quindi non blocca né si anonimizza automaticamente
  alla cancellazione) — coperti comunque dal purge a tempo sotto.
- **Log di audit**: purge automatico giornaliero (job schedulato alle 3:00,
  `AuditLogRetentionService`) delle righe più vecchie di
  `AUDIT_LOG_RETENTION_DAYS` (default 180 giorni / 6 mesi, configurabile).
- **Refresh token**: rotazione con rilevamento riuso, TTL configurato via
  JWT — vedi `docs/SECURITY.md`.

## 6. Diritti dell'interessato — cosa è già supportato

| Diritto | Supporto attuale |
|---|---|
| Accesso | I dati sono consultabili dall'organizzazione titolare via UI/API — non c'è un self-service "esporta i miei dati" per il singolo utente |
| Rettifica | Sì, via UI (profilo utente, dati responsabile) |
| Cancellazione | Sì per utenti/responsabili (vedi sopra), parziale per audit log (gap noto) |
| Portabilità | Export Excel/PDF esiste per i contratti (funzione commerciale), non è pensato come export ex art. 20 |
| Opposizione/limitazione | Non applicabile in modo specifico — da gestire manualmente lato titolare |

## 7. Decisioni automatizzate (art. 22)

Il punteggio di rischio contrattuale e l'analisi delle clausole a rischio
**non sono decisioni automatizzate con effetti giuridici**: sono segnalazioni
a un revisore umano (manager/admin), mai un'azione automatica sul contratto.
Nessun contratto viene approvato, rifiutato o modificato senza intervento
umano — vedi il workflow di approvazione (`ContractWorkflowService`), che
richiede sempre un'azione esplicita di un utente con `can_approve_contracts`.

## 8. Misure tecniche di sicurezza (rimando)

Il dettaglio tecnico completo (cifratura, scoping multi-tenant, gestione
credenziali, rate limiting) è in `docs/SECURITY.md` — questo documento non lo
duplica, lo referenzia. In sintesi rilevante ai fini GDPR (art. 32):
tenant scoping testato (`CrossTenantAccessTest`), password in bcrypt, secret
TOTP cifrati AES-GCM, refresh token rotanti con rilevamento riuso, upload
limitati e validati (10MB, magic-byte PDF check, path traversal escluso per
costruzione). Dal 2026-10-01: controllo di integrità sull'IBAN del
fornitore prima di generare un pagamento SEPA (`Counterparty.verifiedIban`,
`SepaPaymentService`) — se l'IBAN in fattura differisce da quello già
verificato per quella controparte, la generazione è **bloccata**
(`IbanMismatchException`, HTTP 409) invece di procedere silenziosamente;
un cambio IBAN legittimo richiede un'azione esplicita separata
(`PATCH /counterparties/{id}/verified-iban`, solo ADMIN), loggata con
IBAN vecchio/nuovo mascherati (`CounterpartyService#confirmVerifiedIban`).
Mitiga le frodi per sostituzione IBAN (fatture compromesse o fornitori
impersonati), non sostituisce il mascheramento dell'IBAN (gap ancora
aperto, vedi sezione 9).

## 9. Gap noti (da chiudere prima di un trattamento su dati reali)

- [ ] Nessun registro dei trattamenti legale formale al di fuori di questo
      documento tecnico — un legale deve rivedere e formalizzare.
  - Nessuna DPIA (valutazione d'impatto) formale — probabilmente non
      obbligatoria per il volume/tipo di dati attuale, ma da valutare quando
      arriva il primo cliente reale.
  - Nessun meccanismo self-service di "esporta i miei dati" per il singolo
      interessato (oggi richiede intervento manuale del titolare).
  - Il provider SMTP di produzione non è ancora scelto/documentato — va
      indicato nel registro trattamenti reale una volta deciso.
  - Nessun Data Processing Agreement (DPA) template pronto da far firmare
      alle organizzazioni clienti quando BCM opera come responsabile del
      trattamento per loro conto.
  - **Nessun mascheramento dell'IBAN** in nessun punto del sistema (API,
      frontend, export SEPA) — sempre restituito e visualizzato per intero,
      sia per l'IBAN dell'organizzazione titolare sia per quello del
      fornitore in fattura. Zero occorrenze di logica di masking/redazione
      in tutto il codebase (verificato 2026-09-30) — **ancora aperto**,
      distinto dal controllo di integrità sotto (uno impedisce di pagare un
      IBAN sbagliato, l'altro limiterebbe chi può *vedere* l'IBAN giusto).
  - ~~Nessuna retention per i file di pagamento SEPA~~ — **chiuso
      2026-10-01**: `SepaPaymentRetentionService`, purge dopo
      `SEPA_PAYMENT_RETENTION_DAYS` (default 730 giorni). Vedi sezione 5.
  - ~~Nessun controllo su cambio IBAN di un fornitore~~ — **chiuso
      2026-10-01**: `Counterparty.verifiedIban` + blocco su mismatch in
      `SepaPaymentService` + audit dedicato su ogni conferma/cambio. Vedi
      sezione 8.
