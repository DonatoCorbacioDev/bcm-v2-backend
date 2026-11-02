#!/usr/bin/env python3
# Copyright (c) 2025 Donato Corbacio. All rights reserved.
# Licensed under the terms of the LICENSE file at the repository root.

"""Attach the public demo's purchase invoices, run nightly by demo-reset.sh
right after sql/DML/seed-demo-org.sql.

Shows the whole invoice -> match -> payment flow on the two purchase
contracts that seed creates, with dates relative to today so it never ages:

  NRT-2025-007 (Nuvola Cloud Services, 4,800/month)
    - last month's invoice: matched by an admin and already paid by SEPA
    - this month's invoice: matched automatically, waiting for confirmation
  NRT-2025-008 (Ferri & Associati, 6,500/quarter)
    - this quarter's invoice asks to be paid on a NEW bank account: the SEPA
      anti-fraud check blocks any transfer until an admin confirms the change

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

from generate_fatturapa_samples import render_invoice_xml

BUYER = ("Nortiva Solutions S.r.l.", "IT03214560012", ("Via Melchiorre Gioia 41", "20124", "Milano"))

NUVOLA = {
    "name": "Nuvola Cloud Services S.r.l.",
    "vat": "IT07824150960",
    "iban": "IT70K0306909400000071528400",
    "bic": "NUVLITM1XXX",
    "address": ("Via dei Mille 18", "10123", "Torino"),
}
FERRI = {
    "name": "Ferri & Associati S.r.l.",
    "vat": "IT05391280772",
    "address": ("Corso Vittorio Emanuele II 104", "70122", "Bari"),
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


def net_of_vat(total):
    """Taxable amount whose 22% VAT adds up to exactly `total`, so the invoice
    total equals the contract's monthly cost and the match scores 100%."""
    net = round(total / (1 + VAT_RATE / 100), 2)
    while round(net + round(net * VAT_RATE / 100, 2), 2) < total:
        net = round(net + 0.01, 2)
    return net


def invoice_xml(supplier, number, invoice_date, description, total, iban, bic=None):
    net = net_of_vat(total)
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
    parser.add_argument("--cloud-contract-id", type=int, required=True, help="id of NRT-2025-007")
    parser.add_argument("--advisory-contract-id", type=int, required=True, help="id of NRT-2025-008")
    parser.add_argument("--demo-username", default="demo")
    parser.add_argument("--admin-username", default="demo_admin")
    args = parser.parse_args()

    demo_password = os.environ.get("BCM_DEMO_PASSWORD")
    admin_password = os.environ.get("BCM_ADMIN_PASSWORD")
    if not demo_password or not admin_password:
        print("Set BCM_DEMO_PASSWORD and BCM_ADMIN_PASSWORD in the environment.")
        return 1

    today = date.today()
    last_month = first_of_month(today, 1)
    this_month = first_of_month(today, 0)
    problems = []

    # Uploaded as the demo manager, like a visitor would; matching runs on upload.
    demo = Api(args.base_url, args.demo_username, demo_password)
    admin = Api(args.base_url, args.admin_username, admin_password)

    paid = demo.upload(args.cloud_contract_id, f"NCS-{last_month:%Y-%m}.xml", invoice_xml(
        NUVOLA, f"NCS-{last_month:%Y-%m}", last_month,
        f"Hosting e servizi cloud, {MONTHS_IT[last_month.month - 1]} {last_month.year}",
        4800.00, NUVOLA["iban"], NUVOLA["bic"]))
    to_review = demo.upload(args.cloud_contract_id, f"NCS-{this_month:%Y-%m}.xml", invoice_xml(
        NUVOLA, f"NCS-{this_month:%Y-%m}", this_month,
        f"Hosting e servizi cloud, {MONTHS_IT[this_month.month - 1]} {this_month.year}",
        4800.00, NUVOLA["iban"], NUVOLA["bic"]))
    new_iban = demo.upload(args.advisory_contract_id, f"FA-{this_month:%Y-%m}.xml", invoice_xml(
        FERRI, f"FA-{this_month:%Y}-{(this_month.month - 1) // 3 + 1}T", this_month,
        f"Consulenza fiscale e societaria, {(this_month.month - 1) // 3 + 1}° trimestre {this_month.year}",
        6500.00, FERRI_NEW_IBAN))

    # Confirming a match and paying are admin steps the public demo can't do.
    confirmed = admin.confirm_match(args.cloud_contract_id, paid["id"])
    admin.pay_by_sepa(args.cloud_contract_id, [paid["id"]], today)

    print("Demo purchase invoices:")
    expect(f"{paid['invoiceNumber']} match", confirmed["matchStatus"], "CONFIRMED", problems)
    expect(f"{to_review['invoiceNumber']} match", to_review["matchStatus"], "SUGGESTED", problems)
    expect(f"{to_review['invoiceNumber']} IBAN", to_review["supplierIban"], masked(NUVOLA["iban"]), problems)
    expect(f"{new_iban['invoiceNumber']} match", new_iban["matchStatus"], "SUGGESTED", problems)
    expect(f"{new_iban['invoiceNumber']} IBAN", new_iban["supplierIban"], masked(FERRI_NEW_IBAN), problems)

    if problems:
        print(f"FAILED: {', '.join(problems)}")
        return 1
    print("Done.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
