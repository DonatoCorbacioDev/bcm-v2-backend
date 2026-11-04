#!/usr/bin/env python3
# Copyright (c) 2025 Donato Corbacio. All rights reserved.
# Licensed under the terms of the LICENSE file at the repository root.
"""The public demo's sample contract document: Rossi Impianti's maintenance
contract as a one-page PDF, with the start and end dates of that night's
NRT-119, so "Leggi rinnovo e scadenze" reads values that match the contract.

Written with no PDF library (the production VM has plain Python): a single
page of Helvetica text, WinAnsi-encoded so accented letters survive.
"""
import textwrap

MONTHS_IT = ["gennaio", "febbraio", "marzo", "aprile", "maggio", "giugno", "luglio",
             "agosto", "settembre", "ottobre", "novembre", "dicembre"]


def italian_date(day):
    return f"{day.day} {MONTHS_IT[day.month - 1]} {day.year}"


def maintenance_contract_text(start, end):
    """Paragraphs of the contract; the renewal terms match seed-demo-org.sql (90 days, 12 months)."""
    return [
        "CONTRATTO DI MANUTENZIONE IMPIANTI",
        "tra Nortiva Solutions S.r.l. (il Cliente) e Rossi Impianti S.r.l. (il Fornitore)",
        "Art. 1 - Oggetto. Il Fornitore si impegna a eseguire la manutenzione ordinaria e straordinaria "
        "degli impianti di climatizzazione e riscaldamento presso la sede del Cliente in Milano.",
        f"Art. 2 - Durata. Il presente contratto ha durata annuale a decorrere dal {italian_date(start)}, "
        f"con scadenza il {italian_date(end)}.",
        "Art. 3 - Rinnovo. Alla scadenza il contratto si intenderà tacitamente rinnovato per un ulteriore "
        "anno, salvo disdetta da inviarsi a mezzo PEC almeno 90 giorni prima della scadenza.",
        "Art. 4 - Corrispettivo. Il canone annuo è di 6.000 euro oltre IVA, fatturato in rate trimestrali "
        "da pagarsi entro 30 giorni data fattura.",
        "Art. 5 - Recesso. Il Cliente può recedere per giusta causa con preavviso di 15 giorni.",
        "Documento di esempio della demo pubblica di BCM: società e dati sono inventati.",
    ]


def _pdf_string(text):
    raw = text.encode("cp1252")
    return b"(" + raw.replace(b"\\", b"\\\\").replace(b"(", b"\\(").replace(b")", b"\\)") + b")"


def text_pdf(paragraphs, width=88):
    """A one-page A4 PDF with the paragraphs wrapped at `width` characters, a blank line between them."""
    lines = []
    for paragraph in paragraphs:
        lines.extend(textwrap.wrap(paragraph, width) or [""])
        lines.append("")
    stream = b"BT /F1 11 Tf 56 790 Td 15 TL\n"
    for line in lines:
        stream += _pdf_string(line) + b" Tj T*\n"
    stream += b"ET"

    objects = [
        b"<< /Type /Catalog /Pages 2 0 R >>",
        b"<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
        b"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] "
        b"/Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>",
        b"<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>",
        b"<< /Length " + str(len(stream)).encode() + b" >>\nstream\n" + stream + b"\nendstream",
    ]
    out = b"%PDF-1.4\n"
    offsets = []
    for number, body in enumerate(objects, start=1):
        offsets.append(len(out))
        out += f"{number} 0 obj\n".encode() + body + b"\nendobj\n"
    xref = len(out)
    out += f"xref\n0 {len(objects) + 1}\n0000000000 65535 f \n".encode()
    for offset in offsets:
        out += f"{offset:010d} 00000 n \n".encode()
    out += f"trailer\n<< /Size {len(objects) + 1} /Root 1 0 R >>\nstartxref\n{xref}\n%%EOF\n".encode()
    return out
