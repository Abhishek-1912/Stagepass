package com.stagepass.organizer;

import com.stagepass.organizer.service.CsvWriter;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CsvWriterTest {

    @Test
    void quotesCommasQuotesAndNewlines() {
        assertThat(CsvWriter.cell("Rao, Asha")).isEqualTo("\"Rao, Asha\"");
        assertThat(CsvWriter.cell("say \"hi\"")).isEqualTo("\"say \"\"hi\"\"\"");
        assertThat(CsvWriter.cell("a\nb")).isEqualTo("\"a\nb\"");
        assertThat(CsvWriter.cell("plain")).isEqualTo("plain");
        assertThat(CsvWriter.cell(null)).isEqualTo("");
    }

    @Test
    void guardsFormulaInjection() {
        assertThat(CsvWriter.cell("=HYPERLINK(\"x\")")).isEqualTo("\"'=HYPERLINK(\"\"x\"\")\"");
        assertThat(CsvWriter.cell("+91")).isEqualTo("'+91");
        assertThat(CsvWriter.cell("-5")).isEqualTo("'-5");
        assertThat(CsvWriter.cell("@x")).isEqualTo("'@x");
        assertThat(CsvWriter.cell("\tx")).isEqualTo("'\tx");
    }

    @Test
    void keepsUnicode() {
        assertThat(CsvWriter.cell("Asha राव")).isEqualTo("Asha राव");
    }

    @Test
    void rowsEndWithCrlf() {
        assertThat(CsvWriter.row(List.of("a", "b,c", ""))).isEqualTo("a,\"b,c\",\r\n");
    }
}
