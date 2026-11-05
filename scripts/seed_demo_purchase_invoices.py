#!/usr/bin/env python3
# Copyright (c) 2025 Donato Corbacio. All rights reserved.
# Licensed under the terms of the LICENSE file at the repository root.

"""Attach the public demo's purchase invoices, run nightly by demo-reset.sh
right after sql/DML/seed-demo-org.sql.

Shows the whole invoice -> match -> payment flow on the two purchase
contracts that seed creates, with dates relative to today so it never ages:

  NRT-117 (Nuvola Cloud Services, 4,800/month)
    - every earlier month's invoice: matched by an admin and paid by SEPA
    - this month's invoice: matched automatically, waiting for confirmation
  NRT-118 (Ferri & Associati, 6,500/quarter)
    - the earlier quarters' invoices: matched and paid on the trusted IBAN
    - this quarter's invoice asks to be paid on a NEW bank account: the SEPA
      anti-fraud check blocks any transfer until an admin confirms the change
  NRT-119 (Rossi Impianti, maintenance with tacit renewal)
    - the contract itself as a PDF (scripts/demo_contract_pdf.py), read back
      through "Leggi rinnovo e scadenze" to check it finds the same terms

The history matters: the expected costs in seed-demo-org.sql cover the last
12 months, so with only the newest invoices a supplier showed most of its
costs as never invoiced. Now invoiced matches expected except what is still
waiting, which is exactly the story the demo tells.

Supplier VAT numbers and IBANs must stay equal to the ones in
seed-demo-org.sql: matching compares the invoice VAT with the counterparty's,
and SEPA compares the invoice IBAN with the counterparty's verified one.

Usage (credentials from the environment, never on the command line):
    BCM_DEMO_PASSWORD=... BCM_ADMIN_PASSWORD=... python3 scripts/seed_demo_purchase_invoices.py \\
        --cloud-contract-id 123 --advisory-contract-id 124

Exits 1 if any step fails or an invoice doesn't end up in the expected
state, so a broken night shows up in demo-reset.log.
"""

import argparse
import os
import random
import sys
from datetime import date, timedelta

import requests

from demo_contract_pdf import maintenance_contract_text, text_pdf
from generate_fatturapa_samples import render_invoice_xml

BUYER = ("Nortiva Solutions S.r.l.", "IT03214560012", ("Via Melchiorre Gioia 41", "20124", "Milano", "MI"))

NUVOLA = {
    "name": "Nuvola Cloud Services S.r.l.",
    "vat": "IT07824150960",
    "iban": "IT70K0306909400000071528400",
    "bic": "NUVLITM1XXX",
    "address": ("Via dei Mille 18", "10123", "Torino", "TO"),
}
FERRI = {
    "name": "Ferri & Associati S.r.l.",
    "vat": "IT05391280772",
    # The trusted IBAN (verified_iban in seed-demo-org.sql), used by past invoices.
    "iban": "IT17M0503411701000062194812",
    "address": ("Corso Vittorio Emanuele II 104", "70122", "Bari", "BA"),
}
# Not the verified IBAN in seed-demo-org.sql: the "we changed bank" scam.
FERRI_NEW_IBAN = "IT07B3600003200000087345519"

VAT_RATE = 22.0
MONTHS_IT = ["gennaio", "febbraio", "marzo", "aprile", "maggio", "giugno", "luglio",
             "agosto", "settembre", "ottobre", "novembre", "dicembre"]


def first_of_month(day, months_back):
    year, month = day.year, day.month - months_back
    while month < 1:
        month += 12
        year -= 1
    return date(year, month, 1)


def invoice_xml(supplier, number, invoice_date, description, net, iban, bic=None):
    """`net` is the taxable amount (IVA esclusa), equal to the contract's
    instalment: BCM compares contract amounts with the taxable amount, so the
    match scores 100% while the document total carries 22% VAT on top."""
    lines = [{
        "line_number": 1, "description": description, "quantity": 1.0, "unit": "servizio",
        "unit_price": net, "total_price": net, "vat_rate": VAT_RATE,
    }]
    payment = {"iban": iban, "bic": bic, "due_date": invoice_date + timedelta(days=30)}
    rng = random.Random(number)  # same number, same cosmetic details (province codes)
    return render_invoice_xml(
        rng, 1, supplier["name"], supplier["vat"], supplier["address"],
        BUYER[0], BUYER[1], BUYER[2], "TD01", invoice_date, number, lines, payment=payment)


class Api:
    def __init__(self, base_url, username, password):
        self.base_url = base_url
        resp = requests.post(f"{base_url}/auth/login",
                             json={"username": username, "password": password}, timeout=15)
        resp.raise_for_status()
        self.headers = {"Authorization": f"Bearer {resp.json()['token']}"}

    def upload(self, contract_id, file_name, xml):
        resp = requests.post(f"{self.base_url}/contracts/{contract_id}/invoices", headers=self.headers,
                             files={"file": (file_name, xml.encode("utf-8"), "application/xml")}, timeout=30)
        resp.raise_for_status()
        return resp.json()

    def counterparty_city(self, name):
        resp = requests.get(f"{self.base_url}/counterparties", headers=self.headers, timeout=15)
        resp.raise_for_status()
        return next((c.get("city"), c.get("province")) for c in resp.json() if c["name"] == name)

    def contract_by_number(self, number):
        resp = requests.get(f"{self.base_url}/contracts/search", headers=self.headers,
                            params={"q": number, "size": 5}, timeout=15)
        resp.raise_for_status()
        return next(c for c in resp.json()["content"] if c["contractNumber"] == number)

    def upload_document(self, contract_id, file_name, pdf):
        resp = requests.post(f"{self.base_url}/contracts/{contract_id}/documents", headers=self.headers,
                             files={"file": (file_name, pdf, "application/pdf")}, timeout=30)
        resp.raise_for_status()
        return resp.json()

    def read_terms(self, contract_id, document_id):
        resp = requests.get(f"{self.base_url}/contracts/{contract_id}/documents/{document_id}/terms",
                            headers=self.headers, timeout=15)
        resp.raise_for_status()
        return {term["field"]: term["value"] for term in resp.json()["terms"]}

    def confirm_match(self, contract_id, invoice_id):
        resp = requests.post(f"{self.base_url}/contracts/{contract_id}/invoices/{invoice_id}/match/confirm",
                             headers=self.headers, timeout=15)
        resp.raise_for_status()
        return resp.json()

    def pay_by_sepa(self, contract_id, invoice_ids, execution_date):
        resp = requests.post(f"{self.base_url}/contracts/{contract_id}/sepa-payments", headers=self.headers,
                             json={"invoiceIds": invoice_ids, "executionDate": execution_date.isoformat()},
                             timeout=30)
        resp.raise_for_status()


def masked(iban):
    """How the API shows an IBAN: it never returns the full number."""
    return f"{iban[:2]}...{iban[-4:]}"


def expect(label, actual, expected, problems):
    status = "OK" if actual == expected else "WRONG"
    print(f"  [{status}] {label}: {actual} (expected {expected})")
    if actual != expected:
        problems.append(label)


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--base-url", default="http://127.0.0.1:8090/api/v1")
    parser.add_argument("--cloud-contract-id", type=int, required=True, help="id of NRT-117")
    parser.add_argument("--advisory-contract-id", type=int, required=True, help="id of NRT-118")
    parser.add_argument("--demo-username", default="demo")
    parser.add_argument("--admin-username", default="demo_admin")
    args = parser.parse_args()

    demo_password = os.environ.get("BCM_DEMO_PASSWORD")
    admin_password = os.environ.get("BCM_ADMIN_PASSWORD")
    if not demo_password or not admin_password:
        print("Set BCM_DEMO_PASSWORD and BCM_ADMIN_PASSWORD in the environment.")
        return 1

    today = date.today()
    this_month = first_of_month(today, 0)
    problems = []

    # Uploaded as the demo manager, like a visitor would; matching runs on upload.
    demo = Api(args.base_url, args.demo_username, demo_password)
    admin = Api(args.base_url, args.admin_username, admin_password)

    # Past invoices, one per cost line in seed-demo-org.sql: Nuvola for each
    # of the 11 earlier months, Ferri for the 3 earlier quarters.
    cloud_paid = []
    for months_back in range(11, 0, -1):
        month = first_of_month(today, months_back)
        cloud_paid.append(demo.upload(args.cloud_contract_id, f"NCS-{month:%Y-%m}.xml", invoice_xml(
            NUVOLA, f"NCS-{month:%Y-%m}", month,
            f"Hosting e servizi cloud, {MONTHS_IT[month.month - 1]} {month.year}",
            4800.00, NUVOLA["iban"], NUVOLA["bic"])))
    advisory_paid = []
    for months_back in (9, 6, 3):
        month = first_of_month(today, months_back)
        quarter = (month.month - 1) // 3 + 1
        advisory_paid.append(demo.upload(args.advisory_contract_id, f"FA-{month:%Y-%m}.xml", invoice_xml(
            FERRI, f"FA-{month:%Y}-{quarter}T", month,
            f"Consulenza fiscale e societaria, {quarter}° trimestre {month.year}",
            6500.00, FERRI["iban"])))
    to_review = demo.upload(args.cloud_contract_id, f"NCS-{this_month:%Y-%m}.xml", invoice_xml(
        NUVOLA, f"NCS-{this_month:%Y-%m}", this_month,
        f"Hosting e servizi cloud, {MONTHS_IT[this_month.month - 1]} {this_month.year}",
        4800.00, NUVOLA["iban"], NUVOLA["bic"]))
    new_iban = demo.upload(args.advisory_contract_id, f"FA-{this_month:%Y-%m}.xml", invoice_xml(
        FERRI, f"FA-{this_month:%Y}-{(this_month.month - 1) // 3 + 1}T", this_month,
        f"Consulenza fiscale e societaria, {(this_month.month - 1) // 3 + 1}° trimestre {this_month.year}",
        6500.00, FERRI_NEW_IBAN))

    # Confirming a match and paying are admin steps the public demo can't do.
    print("Demo purchase invoices:")
    for contract_id, invoices in ((args.cloud_contract_id, cloud_paid), (args.advisory_contract_id, advisory_paid)):
        confirmed = [admin.confirm_match(contract_id, invoice["id"]) for invoice in invoices]
        admin.pay_by_sepa(contract_id, [invoice["id"] for invoice in invoices], today)
        for invoice in confirmed:
            expect(f"{invoice['invoiceNumber']} match", invoice["matchStatus"], "CONFIRMED", problems)
    expect(f"{to_review['invoiceNumber']} match", to_review["matchStatus"], "SUGGESTED", problems)
    expect(f"{to_review['invoiceNumber']} IBAN", to_review["supplierIban"], masked(NUVOLA["iban"]), problems)
    expect(f"{new_iban['invoiceNumber']} match", new_iban["matchStatus"], "SUGGESTED", problems)
    expect(f"{new_iban['invoiceNumber']} IBAN", new_iban["supplierIban"], masked(FERRI_NEW_IBAN), problems)

    # The suppliers' address, filled in by BCM from their first invoice.
    print("Supplier addresses from the invoices:")
    expect("Nuvola city", demo.counterparty_city(NUVOLA["name"]), ("Torino", "TO"), problems)
    expect("Ferri city", demo.counterparty_city(FERRI["name"]), ("Bari", "BA"), problems)

    # The maintenance contract's PDF, with tonight's dates, for "Leggi rinnovo
    # e scadenze"; reading it back checks the feature works on the live demo.
    maintenance = demo.contract_by_number("NRT-119")
    start, end = date.fromisoformat(maintenance["startDate"]), date.fromisoformat(maintenance["endDate"])
    document = demo.upload_document(maintenance["id"], "Contratto manutenzione Rossi Impianti.pdf",
                                    text_pdf(maintenance_contract_text(start, end)))
    terms = demo.read_terms(maintenance["id"], document["id"])
    print("Demo contract document:")
    expect("NRT-119 terms", terms, {"AUTO_RENEW": "true", "NOTICE_DAYS": "90", "RENEWAL_MONTHS": "12",
                                    "START_DATE": start.isoformat(), "END_DATE": end.isoformat()}, problems)

    if problems:
        print(f"FAILED: {', '.join(problems)}")
        return 1
    print("Done.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
