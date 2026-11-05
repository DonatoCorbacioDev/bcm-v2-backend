-- The counterparty's address in parts, as an electronic invoice gives it
-- (FatturaPA CedentePrestatore > Sede: Indirizzo, CAP, Comune, Provincia,
-- Nazione), so a supplier's town shows in the list and can be filled in from
-- its first invoice. The existing free-text address column stays and now
-- holds the street and number; existing values are not split, since a free
-- text address can't be split reliably. All optional.

ALTER TABLE counterparties
    ADD COLUMN postal_code VARCHAR(10) NULL AFTER address,
    ADD COLUMN city        VARCHAR(100) NULL AFTER postal_code,
    ADD COLUMN province    VARCHAR(2)  NULL AFTER city,
    ADD COLUMN country     VARCHAR(2)  NULL AFTER province;
