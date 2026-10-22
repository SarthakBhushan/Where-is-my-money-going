package com.whereismymoneygoing.backend.nlp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.Test;

class LanguageAndPeriodTest {

    private static final LocalDate ANCHOR = SampleStatement.ANCHOR;   // 22 Oct 2026

    private static List<Period> periods(String question) {
        return PeriodParser.parseAll(TextNormalizer.normalize(question), ANCHOR);
    }

    @Test
    void normalizerUnifiesDevanagariSpellings() {
        assertEquals(TextNormalizer.normalize("ज़ोमैटो"), TextNormalizer.normalize("जोमैटो"));
        assertEquals("zomato kitna", TextNormalizer.normalize("  Zomato,  KITNA?! "));
        assertEquals("barbequenation", TextNormalizer.compact("Barbeque Nation"));
        assertEquals("12 3", TextNormalizer.normalize("१२ ३"));
    }

    @Test
    void detectsTheThreeLanguages() {
        assertEquals(Language.ENGLISH, LanguageDetector.detect("how much did I spend on Zomato this month"));
        assertEquals(Language.HINGLISH, LanguageDetector.detect("is mahine zomato pe kitna kharcha hua"));
        assertEquals(Language.HINDI, LanguageDetector.detect("इस महीने ज़ोमैटो पर कितना खर्च हुआ"));
        assertEquals(Language.HINDI, LanguageDetector.detect("zomato पर कितना खर्च हुआ"));
    }

    @Test
    void thisMonthAndLastMonthInAllLanguages() {
        Period oct = Period.month(YearMonth.of(2026, 10));
        Period sep = Period.month(YearMonth.of(2026, 9));
        assertEquals(List.of(oct), periods("spend this month"));
        assertEquals(List.of(oct), periods("is mahine ka kharcha"));
        assertEquals(List.of(oct), periods("इस महीने का खर्च"));
        assertEquals(List.of(sep), periods("spend last month"));
        assertEquals(List.of(sep), periods("pichle mahine ka kharcha"));
        assertEquals(List.of(sep), periods("पिछले महीने का खर्च"));
    }

    @Test
    void namedMonthsPickTheMostRecentOneNotInTheFuture() {
        assertEquals(List.of(Period.month(YearMonth.of(2026, 3))), periods("spend in march"));
        assertEquals(List.of(Period.month(YearMonth.of(2025, 12))), periods("december ka kharcha"));
        assertEquals(List.of(Period.month(YearMonth.of(2026, 9))), periods("सितंबर में कितना खर्च हुआ"));
        assertEquals(List.of(Period.month(YearMonth.of(2024, 3))), periods("spend in march 2024"));
    }

    @Test
    void mayAsAnOrdinaryWordIsNotAMonth() {
        assertTrue(periods("may I see my spending").isEmpty());
        assertEquals(List.of(Period.month(YearMonth.of(2026, 5))), periods("spend in may"));
    }

    @Test
    void lastNMonthsDaysAndWeeks() {
        Period last3 = periods("zomato last 3 months").get(0);
        assertEquals(LocalDate.of(2026, 8, 1), last3.from());
        assertEquals(ANCHOR, last3.to());
        assertEquals(Period.Kind.LAST_N_MONTHS, periods("pichle teen mahine").get(0).kind());
        assertEquals(Period.day(ANCHOR), periods("aaj kitna").get(0));
        assertEquals(Period.day(ANCHOR.minusDays(1)), periods("yesterday spend").get(0));
        assertEquals(LocalDate.of(2026, 10, 19), periods("this week").get(0).from());   // Monday
        assertEquals(LocalDate.of(2026, 10, 12), periods("last week").get(0).from());
    }

    @Test
    void twoPeriodsKeepTheirOrder() {
        List<Period> ps = periods("compare this month with last month");
        assertEquals(List.of(Period.month(YearMonth.of(2026, 10)), Period.month(YearMonth.of(2026, 9))), ps);
    }
}
