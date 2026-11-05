/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.service;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.donatodev.bcm_backend.dto.ContractTermsDTO.Field;
import com.donatodev.bcm_backend.dto.ContractTermsDTO.Term;

/**
 * Reads tacit renewal, notice period, renewal length and explicit start/end
 * dates from Italian contract text, by fixed rules.
 * <p>
 * Built for precision over recall: a wrong value that a person copies into
 * the contract is worse than no value. So a rule only fires on wording that
 * says one thing (a notice "to withdraw" is not a notice "to cancel the
 * renewal"), and when the text gives two different values for the same field
 * nothing is proposed for it. Measured against the hand-written corpus in
 * src/test/resources/contract-terms (ContractTermsExtractorEvaluationTest).
 */
@Component
public class ContractTermsExtractor {

    private static final int DAYS_PER_MONTH = 30;
    private static final int MAX_SENTENCE_CHARS = 400;

    private static final Map<String, Integer> NUMBER_WORDS = Map.ofEntries(
            Map.entry("un", 1), Map.entry("uno", 1), Map.entry("una", 1), Map.entry("due", 2),
            Map.entry("tre", 3), Map.entry("quattro", 4), Map.entry("cinque", 5), Map.entry("sei", 6),
            Map.entry("sette", 7), Map.entry("otto", 8), Map.entry("nove", 9), Map.entry("dieci", 10),
            Map.entry("undici", 11), Map.entry("dodici", 12), Map.entry("quindici", 15),
            Map.entry("diciotto", 18), Map.entry("venti", 20), Map.entry("ventiquattro", 24),
            Map.entry("trenta", 30), Map.entry("trentasei", 36), Map.entry("quarantacinque", 45),
            Map.entry("sessanta", 60), Map.entry("novanta", 90), Map.entry("centoventi", 120),
            Map.entry("centottanta", 180));

    /** A number in digits or words, optionally repeated in digits: "sessanta (60)". */
    private static final String NUMBER = "(\\d{1,3}|" + String.join("|", NUMBER_WORDS.keySet().stream()
            .sorted((a, b) -> b.length() - a.length()).toList()) + ")(?:\\s?\\((\\d{1,3})\\))?";

    private static final Pattern NOT_RENEWING = Pattern.compile(
            // "non" up to four plain words before the renewal: "non si rinnova",
            // "non sarà soggetto a proroga", "non è previsto alcun rinnovo".
            // Only letters in between: a comma or a number ends the reach, so
            // "qualora non venga data disdetta, si rinnova" stays a renewal.
            "\\bnon\\s(?:\\p{L}+\\s){0,4}(?:rinnov|prorog)"
            // Any form of "escludere" up to five words before it: "escluso ogni
            // meccanismo di rinnovo", "escludono espressamente il rinnovo".
            + "|esclu(?:s|d)\\p{L}*\\s(?:\\p{L}+\\s){0,5}(?:rinnov|prorog)"
            + "|rinnovo\\s(?:tacito|automatico)\\s(?:è\\s)?esclus"
            + "|senza\\s(?:alcun\\s)?rinnovo\\s(?:tacito|automatico)"
            + "|non\\s(?:è\\s)?(?:previsto|ammesso)\\s(?:il\\s|alcun\\s)?rinnovo"
            + "|rinnovo\\s(?:\\p{L}+\\s){0,4}non\\sè\\sautomatico");

    private static final Pattern RENEWING = Pattern.compile(
            "tacitamente\\s(?:rinnov|prorog)|(?:rinnov|prorog)\\p{L}*\\s(?:tacitamente|automaticamente)"
            + "|tacito\\srinnovo|rinnovo\\s(?:tacito|automatico)|automaticamente\\s(?:rinnov|prorog)"
            + "|(?:rinnov|prorog)\\p{L}*\\sdi\\sdiritto"
            + "|salvo\\sdisdetta|in\\s(?:mancanza|assenza|difetto)\\sdi\\sdisdetta");

    private static final String UNIT = "(giorni|gg\\.?|mesi|mese)";
    private static final Pattern NOTICE_AFTER_KEYWORD = Pattern.compile(
            "(?:preavviso|anticipo)\\s(?:scritto\\s|minimo\\s)?(?:di\\s)?(?:almeno\\s|non\\sinferiore\\sa\\s)?"
            + NUMBER + "\\s" + UNIT);
    private static final Pattern NOTICE_BEFORE_DEADLINE = Pattern.compile(
            "\\b" + NUMBER + "\\s" + UNIT + "\\s(?:di\\scalendario\\s)?(?:prima|antecedenti|precedenti|di\\spreavviso|di\\santicipo)");

    private static final Pattern RENEWAL_YEAR_BY_YEAR = Pattern.compile(
            "di\\sanno\\sin\\sanno|(?:per|di)\\s(?:un\\s)?(?:ulteriore|altro|nuovo)\\s(?:periodo\\sdi\\s)?anno"
            + "|rinnov\\p{L}*\\s(?:tacitamente\\s|automaticamente\\s)?per\\sun\\sanno");
    private static final Pattern RENEWAL_MULTI_YEAR = Pattern.compile(
            "(?:per|di)\\s(?:un\\s)?(?:ulteriore|altro|nuovo)\\s(biennio|triennio)"
            + "|di\\s(biennio|triennio)\\sin\\s(?:biennio|triennio)");
    /** "di tre mesi in tre mesi", "di dodici mesi in dodici mesi". */
    private static final Pattern RENEWAL_X_IN_X = Pattern.compile(
            "di\\s" + NUMBER + "\\s(mesi|mese|anni|anno)\\sin\\s\\p{L}+\\s(?:mesi|mese|anni|anno)");
    private static final Map<String, Integer> PERIOD_ADJECTIVES = Map.of(
            "mensili", 1, "bimestrali", 2, "trimestrali", 3, "semestrali", 6, "annuali", 12,
            "biennali", 24, "triennali", 36);
    /** "per ulteriori periodi semestrali", "per successivi periodi annuali", "per ulteriori annualità". */
    private static final Pattern RENEWAL_ADJECTIVE = Pattern.compile(
            "periodi\\s(?:successivi\\s|ulteriori\\s)?(" + String.join("|", PERIOD_ADJECTIVES.keySet()) + ")"
            + "|(?:ulteriori|successive)\\sannualità|(?:una\\s)?(?:ulteriore|nuova)\\sannualità");
    private static final Pattern RENEWAL_PERIOD = Pattern.compile(
            "(?:(?:per|di)\\s(?:ulteriori|successivi|altri)\\s"
            + "|(?:per\\s)?(?:un\\s)?(?:ulteriore|nuovo|successivo)\\speriodo\\sdi\\s"
            + "|(?:per\\s)?periodi\\s(?:successivi\\s|ulteriori\\s|uguali\\s)?di\\s)"
            + NUMBER + "\\s(mesi|mese|anni|anno)");

    private static final String MONTHS = "gennaio|febbraio|marzo|aprile|maggio|giugno|luglio|agosto"
            + "|settembre|ottobre|novembre|dicembre";
    private static final String DATE = "(?:(\\d{1,2})[°º]?\\s(" + MONTHS + ")\\s(\\d{4})"
            + "|(\\d{1,2})[/.\\-](\\d{1,2})[/.\\-](\\d{4}))";
    private static final Pattern START_DATE = Pattern.compile(
            "(?:a\\sdecorrere\\sdal(?:la\\sdata\\sdel)?|con\\sdecorrenza\\sda(?:l)?|decorre\\sdal|a\\spartire\\sdal"
            + "|con\\seffetto\\sdal|(?:ha|avrà)\\sinizio\\s(?:il|dal)|entra\\sin\\svigore\\s(?:il|dal)"
            + "|data\\sdi\\sinizio:?\\s(?:il\\s)?)\\s?(?:giorno\\s)?" + DATE);
    private static final Pattern END_DATE = Pattern.compile(
            "(?:con\\sscadenza\\s(?:il|al)|(?:avrà\\s)?scadenza\\sil|scadrà\\sil|scade\\sil|fino\\sal|sino\\sal"
            + "|termin(?:a|erà)\\s(?:\\p{L}+mente\\s)?il|(?:avrà\\s)?termine\\sil|data\\sdi\\sscadenza:?\\s(?:il\\s)?|data\\sdi\\sfine:?\\s(?:il\\s)?)\\s?" + DATE);
    private static final Pattern FROM_TO = Pattern.compile("\\bdal\\s" + DATE + "\\sal\\s" + DATE);
    /** "dal ... al ..." names the contract's own term only in a sentence about its duration. */
    private static final Pattern ABOUT_TERM = Pattern.compile("durata|validità|efficacia|vigore|contratto\\sha|incarico\\sha");

    private static final Pattern NOTICE_CONTEXT = Pattern.compile("disdett|disdir|rinnov|prorog");
    private static final Pattern RENEWAL_CONTEXT = Pattern.compile("rinnov|prorog");

    /** Splits where a sentence ends and the next starts; blank lines (clause breaks) always split. */
    private static final Pattern SENTENCE_END = Pattern.compile("(?<=[.;!?])\\s+(?=[A-ZÀÈÉÌÒÙ0-9\"«(])");
    private static final Pattern BLANK_LINE = Pattern.compile("\\n\\s*\\n");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    public List<Term> extract(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        Map<Field, Candidates> found = new LinkedHashMap<>();
        for (Field field : Field.values()) {
            found.put(field, new Candidates());
        }
        for (String sentence : sentences(text)) {
            String lower = sentence.toLowerCase(Locale.ITALIAN);
            readRenewal(sentence, lower, found.get(Field.AUTO_RENEW));
            if (NOTICE_CONTEXT.matcher(lower).find()) {
                readNotice(sentence, lower, found.get(Field.NOTICE_DAYS));
            }
            if (RENEWAL_CONTEXT.matcher(lower).find()) {
                readRenewalLength(sentence, lower, found.get(Field.RENEWAL_MONTHS));
            }
            readDates(sentence, lower, found.get(Field.START_DATE), found.get(Field.END_DATE));
        }
        List<Term> terms = new ArrayList<>();
        found.forEach((field, candidates) -> candidates.single(field).ifPresent(terms::add));
        return dropImpossibleDates(terms);
    }

    private static List<String> sentences(String text) {
        List<String> result = new ArrayList<>();
        for (String block : BLANK_LINE.split(text.replace(' ', ' '))) {
            String flat = WHITESPACE.matcher(block).replaceAll(" ").trim();
            for (String sentence : SENTENCE_END.split(flat)) {
                if (!sentence.isBlank()) {
                    result.add(sentence.length() > MAX_SENTENCE_CHARS
                            ? sentence.substring(0, MAX_SENTENCE_CHARS) + "…" : sentence);
                }
            }
        }
        return result;
    }

    private static void readRenewal(String sentence, String lower, Candidates out) {
        Matcher not = NOT_RENEWING.matcher(lower);
        if (not.find()) {
            out.add("false", sentence.substring(not.start(), not.end()), sentence);
            return;
        }
        Matcher yes = RENEWING.matcher(lower);
        if (yes.find()) {
            out.add("true", sentence.substring(yes.start(), yes.end()), sentence);
        }
    }

    private static void readNotice(String sentence, String lower, Candidates out) {
        for (Pattern pattern : List.of(NOTICE_AFTER_KEYWORD, NOTICE_BEFORE_DEADLINE)) {
            Matcher m = pattern.matcher(lower);
            while (m.find()) {
                Integer amount = number(m.group(1), m.group(2));
                if (amount == null) {
                    continue;
                }
                boolean months = m.group(3).startsWith("mes");
                int days = months ? amount * DAYS_PER_MONTH : amount;
                String asWritten = amount + (months ? (amount == 1 ? " mese" : " mesi") : " giorni");
                out.add(String.valueOf(days), asWritten, sentence);
            }
        }
    }

    private static void readRenewalLength(String sentence, String lower, Candidates out) {
        Matcher yearly = RENEWAL_YEAR_BY_YEAR.matcher(lower);
        if (yearly.find()) {
            out.add("12", sentence.substring(yearly.start(), yearly.end()), sentence);
        }
        Matcher multiYear = RENEWAL_MULTI_YEAR.matcher(lower);
        if (multiYear.find()) {
            String span = multiYear.group(1) != null ? multiYear.group(1) : multiYear.group(2);
            out.add("biennio".equals(span) ? "24" : "36", multiYear.group(), sentence);
        }
        Matcher xInX = RENEWAL_X_IN_X.matcher(lower);
        while (xInX.find()) {
            Integer amount = number(xInX.group(1), xInX.group(2));
            if (amount != null) {
                out.add(String.valueOf(xInX.group(3).startsWith("ann") ? amount * 12 : amount), xInX.group(), sentence);
            }
        }
        Matcher adjective = RENEWAL_ADJECTIVE.matcher(lower);
        while (adjective.find()) {
            int months = adjective.group(1) != null ? PERIOD_ADJECTIVES.get(adjective.group(1)) : 12;
            out.add(String.valueOf(months), adjective.group(), sentence);
        }
        Matcher period = RENEWAL_PERIOD.matcher(lower);
        while (period.find()) {
            Integer amount = number(period.group(1), period.group(2));
            if (amount != null) {
                int months = period.group(3).startsWith("ann") ? amount * 12 : amount;
                out.add(String.valueOf(months), sentence.substring(period.start(), period.end()), sentence);
            }
        }
    }

    private static void readDates(String sentence, String lower, Candidates start, Candidates end) {
        Matcher fromTo = FROM_TO.matcher(lower);
        if (fromTo.find() && ABOUT_TERM.matcher(lower).find()) {
            addDate(start, fromTo, 1, sentence);
            addDate(end, fromTo, 7, sentence);
        }
        Matcher s = START_DATE.matcher(lower);
        while (s.find()) {
            addDate(start, s, 1, sentence);
        }
        Matcher e = END_DATE.matcher(lower);
        while (e.find()) {
            addDate(end, e, 1, sentence);
        }
    }

    /** Reads the DATE group starting at {@code first}: either "1 gennaio 2026" (3 groups) or "01/01/2026" (next 3). */
    private static void addDate(Candidates out, Matcher m, int first, String sentence) {
        try {
            LocalDate date;
            if (m.group(first) != null) {
                date = LocalDate.of(Integer.parseInt(m.group(first + 2)),
                        monthNumber(m.group(first + 1)), Integer.parseInt(m.group(first)));
            } else {
                date = LocalDate.of(Integer.parseInt(m.group(first + 5)),
                        Integer.parseInt(m.group(first + 4)), Integer.parseInt(m.group(first + 3)));
            }
            out.add(date.toString(), sentence.substring(m.start(), m.end()), sentence);
        } catch (DateTimeException ignored) {
            // "31/02/2026" and the like: not a date, so not a value.
        }
    }

    private static int monthNumber(String name) {
        return List.of(MONTHS.split("\\|")).indexOf(name) + 1;
    }

    private static Integer number(String token, String digitsInBrackets) {
        if (digitsInBrackets != null) {
            return Integer.valueOf(digitsInBrackets);
        }
        if (token.chars().allMatch(Character::isDigit)) {
            return Integer.valueOf(token);
        }
        return NUMBER_WORDS.get(token);
    }

    /** A start after the end means one of the two was misread: propose neither. */
    private static List<Term> dropImpossibleDates(List<Term> terms) {
        String start = valueOf(terms, Field.START_DATE);
        String end = valueOf(terms, Field.END_DATE);
        if (start != null && end != null && start.compareTo(end) > 0) {
            return terms.stream()
                    .filter(t -> t.field() != Field.START_DATE && t.field() != Field.END_DATE)
                    .toList();
        }
        return terms;
    }

    private static String valueOf(List<Term> terms, Field field) {
        return terms.stream().filter(t -> t.field() == field).map(Term::value).findFirst().orElse(null);
    }

    /** Every value read for one field; it is proposed only if they all agree. */
    private static final class Candidates {
        private final List<Term> seen = new ArrayList<>();

        void add(String value, String asWritten, String sentence) {
            seen.add(new Term(null, value, asWritten, sentence));
        }

        java.util.Optional<Term> single(Field field) {
            if (seen.isEmpty() || seen.stream().map(Term::value).distinct().count() > 1) {
                return java.util.Optional.empty();
            }
            Term first = seen.get(0);
            return java.util.Optional.of(new Term(field, first.value(), first.asWritten(), first.sentence()));
        }
    }
}
