package com.donatodev.bcm_backend.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** OWASP File Upload Cheat Sheet: the name a file was sent with is never trusted. */
class SafeFileNameTest {

    @ParameterizedTest(name = "[{index}] {0} -> {1}")
    @CsvSource(delimiter = ';', quoteCharacter = '\'', value = {
            "Contratto fornitura 2026.pdf;Contratto fornitura 2026.pdf",
            "CONTRATTO.PDF;CONTRATTO.PDF",
            "C:\\fakepath\\contratto.pdf;contratto.pdf",
            "../../etc/passwd.pdf;passwd.pdf",
            "a/b\\c.pdf;c.pdf",
            "<script>alert(1)</script>.pdf;script.pdf",
            "<b onmouseover=alert(1)>x.pdf;b onmouseover=alert(1)x.pdf",
            "con\"tra:tto|?*.pdf;contratto.pdf",
            "'  ..nascosto.pdf  ';nascosto.pdf",
            "contratto. .pdf;contratto.pdf",
            "contratto;contratto.pdf",
            "\u00e0cc\u00e9nto \u00e8 gi\u00e0.pdf;\u00e0cc\u00e9nto \u00e8 gi\u00e0.pdf"})
    void keepsOnlyAHarmlessLastSegment(String sent, String kept) {
        assertThat(SafeFileName.of(sent, ".pdf", "documento.pdf")).isEqualTo(kept);
    }

    @Test
    void dropsControlAndInvisibleFormattingCharacters() {
        // A right-to-left override makes "contratto<RLO>fdp.exe" read as "contrattoexe.pdf".
        assertThat(SafeFileName.of("riga\r\nnuova\u0000.pdf", ".pdf", "x.pdf")).isEqualTo("riganuova.pdf");
        assertThat(SafeFileName.of("contratto\u202Efdp.exe.pdf", ".pdf", "x.pdf")).isEqualTo("contrattofdp.exe.pdf");
        assertThat(SafeFileName.of("zero\u200Bwidth.pdf", ".pdf", "x.pdf")).isEqualTo("zerowidth.pdf");
    }

    @Test
    void composesAccentsSoTheSameNameLooksTheSame() {
        assertThat(SafeFileName.of("cafe\u0301.pdf", ".pdf", "x.pdf")).isEqualTo("caf\u00e9.pdf");
    }

    @ParameterizedTest
    @CsvSource(delimiter = ';', quoteCharacter = '\'', value = {"''", "'   '", ".pdf", "...pdf", "C:\\dir\\", "<>:|.pdf"})
    void nothingUsableLeftGivesTheFallback(String sent) {
        assertThat(SafeFileName.of(sent, ".pdf", "documento.pdf")).isEqualTo("documento.pdf");
    }

    @Test
    void noNameGivesTheFallback() {
        assertThat(SafeFileName.of(null, ".xml", "fattura.xml")).isEqualTo("fattura.xml");
    }

    @Test
    void aLongNameIsShortenedKeepingTheExtension() {
        String name = SafeFileName.of("a".repeat(300) + ".PDF", ".pdf", "x.pdf");

        assertThat(name).hasSize(SafeFileName.MAX_LENGTH).isEqualTo("a".repeat(196) + ".PDF");
    }

    @Test
    void aNameOfExactlyTheLimitIsKept() {
        String sent = "b".repeat(SafeFileName.MAX_LENGTH - 4) + ".pdf";

        assertThat(SafeFileName.of(sent, ".pdf", "x.pdf")).isEqualTo(sent);
    }

    @Test
    void shorteningNeverSplitsACharacterNorEndsOnADot() {
        // 195 letters, a dot, then emoji (two Java chars each): the cut lands right after the dot.
        String sent = "a".repeat(195) + "." + "\uD83D\uDE00".repeat(20) + ".pdf";

        assertThat(SafeFileName.of(sent, ".pdf", "x.pdf")).isEqualTo("a".repeat(195) + ".pdf");
        assertThat(SafeFileName.of("\uD83D\uDE00".repeat(250) + ".pdf", ".pdf", "x.pdf"))
                .isEqualTo("\uD83D\uDE00".repeat(196) + ".pdf");
    }

    @Test
    void cannotBeInstantiated() throws Exception {
        Constructor<SafeFileName> constructor = SafeFileName.class.getDeclaredConstructor();
        constructor.setAccessible(true);

        assertThatThrownBy(constructor::newInstance)
                .isInstanceOf(InvocationTargetException.class)
                .hasCauseInstanceOf(UnsupportedOperationException.class);
    }
}
