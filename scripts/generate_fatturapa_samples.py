#!/usr/bin/env python3
"""Generate synthetic FatturaPA XML invoices to test FatturaPaXmlParserService
and the electronic invoice upload flow (ElectronicInvoiceController ->
ElectronicInvoiceService) with realistic, varied data.

Standalone offline tool: does not touch any backend Java code. Only writes
XML files to disk; uploading them is an optional extra step (--upload) that
calls the real HTTP API.

What FatturaPaXmlParserService actually reads (namespace-agnostic, by local
tag name only) - everything else in the XML is decorative/realism only:
  FatturaElettronicaHeader > CedentePrestatore > DatiAnagrafici
    > Anagrafica > Denominazione, IdFiscaleIVA (IdPaese+IdCodice)
  FatturaElettronicaBody > DatiGenerali > DatiGeneraliDocumento
    > TipoDocumento, Numero, Data (YYYY-MM-DD), ImportoTotaleDocumento, Divisa
  FatturaElettronicaBody > DatiBeniServizi > DettaglioLinee (0..N)
    > NumeroLinea, Descrizione, Quantita, UnitaMisura, PrezzoUnitario,
      PrezzoTotale, AliquotaIVA
Only the first FatturaElettronicaBody is read - this script never generates
more than one. The parser disables DOCTYPE/external entities (XXE-hardened),
so generated files never contain a DOCTYPE.

Usage:
    python scripts/generate_fatturapa_samples.py
    python scripts/generate_fatturapa_samples.py --count 80 --output-dir scripts/sample_invoices

Optional upload smoke test against a running backend (needs a verified
ADMIN/MANAGER account - the seeded default admin is disabled by
V14__neutralize_default_admin.sql, so use your own credentials):
    python scripts/generate_fatturapa_samples.py --count 5 --upload \
        --contract-id 1 --username <user> --password <password>
"""
import argparse
import os
import random
import shutil
import sys
from datetime import date, timedelta
from pathlib import Path
from xml.sax.saxutils import escape

try:
    from faker import Faker

    _fake = Faker("it_IT")
except ImportError:
    _fake = None

NAMESPACE = "http://ivaservizi.agenziaentrate.gov.it/docs/xsd/fatture/v1.2"

FALLBACK_SURNAMES = [
    "Rossi", "Bianchi", "Verdi", "Ferrari", "Colombo", "Bruno", "Russo",
    "Fontana", "Costa", "Moretti", "Galli", "Conti", "Marini", "Rinaldi", "Greco",
]
FALLBACK_NOUNS = [
    "Forniture", "Logistica", "Consulting", "Impianti", "Servizi", "Edilizia",
    "Tecnologie", "Engineering", "Trasporti", "Software",
]
FALLBACK_SUFFIXES = ["S.r.l.", "S.p.A.", "S.n.c.", "S.a.s."]
DESCRIPTIONS = [
    "Servizio di consulenza informatica", "Fornitura materiale di cancelleria",
    "Manutenzione impianti elettrici", "Licenza software annuale",
    "Servizio di trasporto merci", "Noleggio attrezzature da cantiere",
    "Consulenza fiscale e amministrativa", "Fornitura materiale edile",
    "Servizio di pulizia uffici", "Sviluppo applicativo gestionale",
    "Manutenzione ordinaria impianto idraulico", "Servizio di assistenza tecnica",
    "Fornitura componenti elettronici", "Consulenza legale societaria",
    "Servizio di catering aziendale", "Stampa e rilegatura documenti",
    "Manutenzione hardware e rete", "Servizio di traduzione documenti",
    "Fornitura dispositivi di protezione individuale", "Affitto sala riunioni",
]
PROVINCE_CODES = ["RM", "MI", "TO", "NA", "BO", "FI", "VE", "BA", "PA", "GE"]
UNITS_OF_MEASURE = ["PZ", "HUR", "KG", "GG", "MS", "NR"]
VAT_RATES = [4.00, 10.00, 22.00]
DOC_TYPES = [("TD01", 0.85), ("TD04", 0.15)]
EDGE_CASE_KINDS = ["zero_amount", "single_line", "long_description"]


def parse_args():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--count", type=int, default=50, help="Number of invoice files to generate")
    parser.add_argument("--output-dir", default=None, help="Output directory (default: scripts/sample_invoices next to this script)")
    parser.add_argument("--seed", type=int, default=42)
    parser.add_argument("--credit-note-ratio", type=float, default=0.15)
    parser.add_argument("--edge-case-ratio", type=float, default=0.15)
    parser.add_argument("--keep-existing", action="store_true", help="Don't clear the output directory before generating")
    parser.add_argument("--buyer-name", default="Demo Corp Srl", help="CessionarioCommittente name (the org receiving the invoices)")
    parser.add_argument("--buyer-vat", default="IT00000000001", help="CessionarioCommittente VAT number")

    parser.add_argument("--upload", action="store_true", help="Upload a sample of the generated files via POST /contracts/{id}/invoices")
    parser.add_argument("--base-url", default="http://localhost:8090/api/v1")
    parser.add_argument("--contract-id", type=int, default=None, help="Existing contract id to attach invoices to (required with --upload)")
    parser.add_argument("--username", default=os.environ.get("BCM_USERNAME"))
    parser.add_argument("--password", default=os.environ.get("BCM_PASSWORD"))
    parser.add_argument("--org-slug", default=os.environ.get("BCM_ORG_SLUG"))
    parser.add_argument("--upload-limit", type=int, default=1, help="How many of the generated files to upload (default: 1)")
    return parser.parse_args()


def random_vat(rng):
    return "IT" + "".join(str(rng.randint(0, 9)) for _ in range(11))


def random_company_name(rng):
    if _fake is not None:
        return _fake.company()
    return f"{rng.choice(FALLBACK_SURNAMES)} {rng.choice(FALLBACK_NOUNS)} {rng.choice(FALLBACK_SUFFIXES)}"


def random_description(rng):
    # Faker's bs()/catch_phrase() produce abstract corporate buzzwords (even in
    # it_IT) rather than something that reads like a real invoice line, so a
    # curated pool of plausible Italian service/product descriptions is used
    # regardless of whether Faker is installed.
    return rng.choice(DESCRIPTIONS)


def random_address(rng):
    if _fake is not None:
        return _fake.street_address(), _fake.postcode(), _fake.city()
    return f"Via {rng.choice(FALLBACK_SURNAMES)} {rng.randint(1, 99)}", f"{rng.randint(10000, 99999)}", rng.choice(["Roma", "Milano", "Torino", "Napoli", "Bologna"])


def random_doc_type(rng):
    r = rng.random()
    cumulative = 0.0
    for doc_type, weight in DOC_TYPES:
        cumulative += weight
        if r <= cumulative:
            return doc_type
    return DOC_TYPES[-1][0]


def random_invoice_date(rng, today):
    days_ago = rng.randint(0, 24 * 30)
    return today - timedelta(days=days_ago)


def build_line_items(rng, n_lines, edge_case_kind):
    lines = []
    if edge_case_kind == "zero_amount":
        lines.append({"description": random_description(rng), "quantity": 1.0, "unit": rng.choice(UNITS_OF_MEASURE), "unit_price": 0.0, "vat_rate": rng.choice(VAT_RATES)})
    else:
        for i in range(n_lines):
            quantity = round(rng.uniform(1, 50), 2)
            unit_price = round(rng.uniform(10, 500), 2)
            description = random_description(rng)
            if edge_case_kind == "long_description" and i == 0:
                description = (description + " - ") * 40
                description = description[:2000]
            lines.append({
                "description": description,
                "quantity": quantity,
                "unit": rng.choice(UNITS_OF_MEASURE),
                "unit_price": unit_price,
                "vat_rate": rng.choice(VAT_RATES),
            })

    result = []
    for i, line in enumerate(lines, start=1):
        total_price = round(line["quantity"] * line["unit_price"], 2)
        result.append({
            "line_number": i,
            "description": line["description"],
            "quantity": line["quantity"],
            "unit": line["unit"],
            "unit_price": line["unit_price"],
            "total_price": total_price,
            "vat_rate": line["vat_rate"],
        })
    return result


def compute_summary(lines):
    by_rate = {}
    for line in lines:
        by_rate.setdefault(line["vat_rate"], 0.0)
        by_rate[line["vat_rate"]] += line["total_price"]

    summary = []
    total = 0.0
    for rate, imponibile in by_rate.items():
        imponibile = round(imponibile, 2)
        imposta = round(imponibile * rate / 100, 2)
        summary.append({"vat_rate": rate, "imponibile": imponibile, "imposta": imposta})
        total += imponibile + imposta
    return summary, round(total, 2)


def render_line_xml(line):
    return f"""            <DettaglioLinee>
                <NumeroLinea>{line["line_number"]}</NumeroLinea>
                <Descrizione>{escape(line["description"])}</Descrizione>
                <Quantita>{line["quantity"]:.2f}</Quantita>
                <UnitaMisura>{escape(line["unit"])}</UnitaMisura>
                <PrezzoUnitario>{line["unit_price"]:.2f}</PrezzoUnitario>
                <PrezzoTotale>{line["total_price"]:.2f}</PrezzoTotale>
                <AliquotaIVA>{line["vat_rate"]:.2f}</AliquotaIVA>
            </DettaglioLinee>"""


def render_summary_xml(summary):
    return "\n".join(
        f"""            <DatiRiepilogo>
                <AliquotaIVA>{s["vat_rate"]:.2f}</AliquotaIVA>
                <ImponibileImporto>{s["imponibile"]:.2f}</ImponibileImporto>
                <Imposta>{s["imposta"]:.2f}</Imposta>
                <EsigibilitaIVA>I</EsigibilitaIVA>
            </DatiRiepilogo>"""
        for s in summary
    )


def render_party_xml(rng, name, vat, address, regime_fiscale=None):
    street, postcode, city = address
    province = rng.choice(PROVINCE_CODES)
    regime = f"\n                <RegimeFiscale>{regime_fiscale}</RegimeFiscale>" if regime_fiscale else ""
    return f"""            <DatiAnagrafici>
                <IdFiscaleIVA>
                    <IdPaese>IT</IdPaese>
                    <IdCodice>{vat[2:]}</IdCodice>
                </IdFiscaleIVA>
                <Anagrafica>
                    <Denominazione>{escape(name)}</Denominazione>
                </Anagrafica>{regime}
            </DatiAnagrafici>
            <Sede>
                <Indirizzo>{escape(street)}</Indirizzo>
                <CAP>{postcode}</CAP>
                <Comune>{escape(city)}</Comune>
                <Provincia>{province}</Provincia>
                <Nazione>IT</Nazione>
            </Sede>"""


def render_invoice_xml(rng, seq, supplier_name, supplier_vat, supplier_address, buyer_name, buyer_vat, buyer_address, doc_type, invoice_date, number, lines):
    summary, total = compute_summary(lines)
    lines_xml = "\n".join(render_line_xml(line) for line in lines)
    summary_xml = render_summary_xml(summary)

    return f"""<?xml version="1.0" encoding="UTF-8"?>
<p:FatturaElettronica xmlns:p="{NAMESPACE}" versione="FPR12">
    <FatturaElettronicaHeader>
        <DatiTrasmissione>
            <IdTrasmittente>
                <IdPaese>IT</IdPaese>
                <IdCodice>{supplier_vat[2:]}</IdCodice>
            </IdTrasmittente>
            <ProgressivoInvio>{seq:05d}</ProgressivoInvio>
            <FormatoTrasmissione>FPR12</FormatoTrasmissione>
            <CodiceDestinatario>0000000</CodiceDestinatario>
        </DatiTrasmissione>
        <CedentePrestatore>
{render_party_xml(rng, supplier_name, supplier_vat, supplier_address, regime_fiscale="RF01")}
        </CedentePrestatore>
        <CessionarioCommittente>
{render_party_xml(rng, buyer_name, buyer_vat, buyer_address)}
        </CessionarioCommittente>
    </FatturaElettronicaHeader>
    <FatturaElettronicaBody>
        <DatiGenerali>
            <DatiGeneraliDocumento>
                <TipoDocumento>{doc_type}</TipoDocumento>
                <Divisa>EUR</Divisa>
                <Data>{invoice_date.isoformat()}</Data>
                <Numero>{number}</Numero>
                <ImportoTotaleDocumento>{total:.2f}</ImportoTotaleDocumento>
            </DatiGeneraliDocumento>
        </DatiGenerali>
        <DatiBeniServizi>
{lines_xml}
{summary_xml}
        </DatiBeniServizi>
    </FatturaElettronicaBody>
</p:FatturaElettronica>
"""


def generate_one(rng, seq, today, args, is_edge_case):
    supplier_name = random_company_name(rng)
    supplier_vat = random_vat(rng)
    supplier_address = random_address(rng)
    buyer_address = random_address(rng)
    doc_type = random_doc_type(rng)
    invoice_date = random_invoice_date(rng, today)
    number = str(seq)

    edge_case_kind = rng.choice(EDGE_CASE_KINDS) if is_edge_case else None
    n_lines = 1 if edge_case_kind in ("zero_amount", "single_line") else rng.randint(1, 8)
    lines = build_line_items(rng, n_lines, edge_case_kind)

    xml_content = render_invoice_xml(
        rng, seq, supplier_name, supplier_vat, supplier_address,
        args.buyer_name, args.buyer_vat, buyer_address,
        doc_type, invoice_date, number, lines,
    )
    _, total = compute_summary(lines)
    return xml_content, doc_type, total


def upload_samples(args, file_paths):
    try:
        import requests
    except ImportError:
        print("--upload requires the 'requests' package: pip install -r scripts/requirements.txt")
        sys.exit(1)

    if not args.contract_id:
        print("--upload requires --contract-id (an existing contract id in the target DB).")
        sys.exit(1)
    if not args.username or not args.password:
        print(
            "--upload requires credentials for a verified ADMIN/MANAGER user "
            "(--username/--password or BCM_USERNAME/BCM_PASSWORD env vars). "
            "Note: the seeded default admin account is disabled (V14__neutralize_default_admin.sql)."
        )
        sys.exit(1)

    login_payload = {"username": args.username, "password": args.password}
    if args.org_slug:
        login_payload["organizationSlug"] = args.org_slug

    login_resp = requests.post(f"{args.base_url}/auth/login", json=login_payload, timeout=10)
    if not login_resp.ok:
        print(f"Login failed: {login_resp.status_code} {login_resp.text}")
        sys.exit(1)
    token = login_resp.json()["token"]

    for path in file_paths[: args.upload_limit]:
        with open(path, "rb") as f:
            resp = requests.post(
                f"{args.base_url}/contracts/{args.contract_id}/invoices",
                headers={"Authorization": f"Bearer {token}"},
                files={"file": (path.name, f, "application/xml")},
                timeout=15,
            )
        if resp.ok:
            data = resp.json()
            print(
                f"  [OK] {path.name} -> id={data.get('id')} supplier={data.get('supplierName')} "
                f"total={data.get('totalAmount')} lines={len(data.get('lineItems') or [])}"
            )
        else:
            print(f"  [FAIL] {path.name} -> {resp.status_code} {resp.text}")


def main():
    args = parse_args()
    rng = random.Random(args.seed)
    today = date.today()

    output_dir = Path(args.output_dir) if args.output_dir else Path(__file__).parent / "sample_invoices"
    if output_dir.exists() and not args.keep_existing:
        shutil.rmtree(output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)

    n_edge_cases = max(1, round(args.count * args.edge_case_ratio)) if args.count > 0 else 0
    edge_flags = [True] * n_edge_cases + [False] * (args.count - n_edge_cases)
    rng.shuffle(edge_flags)

    doc_type_counts = {}
    totals = []
    file_paths = []

    for i in range(args.count):
        seq = i + 1
        xml_content, doc_type, total = generate_one(rng, seq, today, args, edge_flags[i])
        file_path = output_dir / f"SYN-INV-{seq:04d}.xml"
        file_path.write_text(xml_content, encoding="utf-8")
        file_paths.append(file_path)
        doc_type_counts[doc_type] = doc_type_counts.get(doc_type, 0) + 1
        totals.append(total)

    avg_total = round(sum(totals) / len(totals), 2) if totals else 0.0
    print(f"Generated {len(file_paths)} FatturaPA XML files in {output_dir}")
    for doc_type, count in sorted(doc_type_counts.items()):
        label = "fatture" if doc_type == "TD01" else "note di credito" if doc_type == "TD04" else doc_type
        print(f"  {doc_type} ({label}): {count}")
    print(f"  Average ImportoTotaleDocumento: {avg_total}")

    if args.upload:
        print("\nUploading sample(s)...")
        upload_samples(args, file_paths)


if __name__ == "__main__":
    main()
