package com.whereismymoneygoing.backend.nlp;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds "this month", "pichle mahine", "पिछले महीने", "March", "last 3 months", "aaj", ... in a question.
 * "Now" is the latest date in the statement, not today's date, so the sample statements work too.
 */
public final class PeriodParser {

    private PeriodParser() {}

    private static String alt(String... words) {
        List<String> out = new ArrayList<>();
        for (String w : words) out.add(Pattern.quote(TextNormalizer.normalize(w)));
        return "(?:" + String.join("|", out) + ")";
    }

    private static final String W_MONTH = alt("month", "months", "mahine", "mahina", "mahino", "mahinon", "maah",
            "महीने", "महीना", "महीनों", "महिने", "महिना", "महीनो", "माह");
    private static final String W_LAST = alt("last", "previous", "prev", "past", "pichle", "pichhle", "pichla",
            "pichhla", "pichley", "पिछले", "पिछला", "बीते");
    private static final String W_THIS = alt("this", "current", "is", "iss", "ye", "yeh", "इस", "यह", "ये");
    private static final String W_WEEK = alt("week", "weeks", "hafte", "hafta", "haftey", "हफ्ते", "हफ्ता",
            "हफ्तों", "सप्ताह");
    private static final String W_TODAY = alt("today", "aaj", "आज");
    private static final String W_YESTERDAY = alt("yesterday", "kal", "कल");
    private static final String B = "(?:^| )";          // boundary before
    private static final String E = "(?= |$)";          // boundary after

    private static final Pattern THIS_MONTH = Pattern.compile(B + W_THIS + " " + W_MONTH + E);
    private static final Pattern LAST_MONTH = Pattern.compile(B + W_LAST + " " + W_MONTH + E);
    private static final Pattern LAST_N = Pattern.compile(B + W_LAST + " (\\S+) " + W_MONTH + E);
    private static final Pattern THIS_WEEK = Pattern.compile(B + W_THIS + " " + W_WEEK + E);
    private static final Pattern LAST_WEEK = Pattern.compile(B + W_LAST + " " + W_WEEK + E);
    private static final Pattern TODAY = Pattern.compile(B + W_TODAY + E);
    private static final Pattern YESTERDAY = Pattern.compile(B + W_YESTERDAY + E);

    private static final Map<String, Integer> NUMBER_WORDS = new LinkedHashMap<>();
    private static final Map<String, Integer> MONTH_NAMES = new LinkedHashMap<>();
    static {
        String[][] numbers = {{"2", "do", "दो", "two"}, {"3", "teen", "तीन", "three"}, {"4", "char", "चार", "four"},
                {"5", "paanch", "panch", "पांच", "five"}, {"6", "chhe", "che", "छह", "six"},
                {"12", "barah", "बारह", "twelve"}};
        for (String[] row : numbers) {
            int n = Integer.parseInt(row[0]);
            for (String w : row) NUMBER_WORDS.put(TextNormalizer.normalize(w), n);
        }
        String[][] months = {
                {"january", "jan", "janwari", "janvari", "जनवरी"},
                {"february", "feb", "farwari", "farvari", "fervari", "फरवरी", "फ़रवरी"},
                {"march", "मार्च"},
                {"april", "apr", "aprail", "अप्रैल"},
                {"may", "mai", "मई"},
                {"june", "jun", "जून"},
                {"july", "jul", "julai", "जुलाई"},
                {"august", "aug", "agast", "अगस्त"},
                {"september", "sep", "sept", "sitambar", "sitamber", "सितंबर", "सितम्बर"},
                {"october", "oct", "aktubar", "अक्टूबर", "अक्तूबर"},
                {"november", "nov", "navambar", "navamber", "नवंबर", "नवम्बर"},
                {"december", "dec", "disambar", "दिसंबर", "दिसम्बर"}};
        for (int i = 0; i < months.length; i++) {
            for (String w : months[i]) MONTH_NAMES.put(TextNormalizer.normalize(w), i + 1);
        }
    }

    private record Hit(int index, Period period) {}

    /** All periods mentioned, in the order they appear. Empty when the question names none. */
    public static List<Period> parseAll(String normalized, LocalDate anchor) {
        List<Hit> hits = new ArrayList<>();
        YearMonth now = YearMonth.from(anchor);

        find(THIS_MONTH, normalized, hits, m -> Period.month(now));
        find(LAST_MONTH, normalized, hits, m -> Period.month(now.minusMonths(1)));
        find(THIS_WEEK, normalized, hits, m -> Period.weekOf(anchor, false));
        find(LAST_WEEK, normalized, hits, m -> Period.weekOf(anchor.minusDays(7), true));
        find(TODAY, normalized, hits, m -> Period.day(anchor));
        find(YESTERDAY, normalized, hits, m -> Period.day(anchor.minusDays(1)));
        find(LAST_N, normalized, hits, m -> {
            Integer n = number(m.group(1));
            return n == null || n < 1 || n > 24 ? null : Period.lastMonths(n, anchor);
        });
        monthNames(normalized, anchor, hits);

        hits.sort((a, b) -> Integer.compare(a.index, b.index));
        List<Period> out = new ArrayList<>();
        for (Hit h : hits) if (!out.contains(h.period)) out.add(h.period);
        return out;
    }

    private interface Maker { Period make(Matcher m); }

    private static void find(Pattern p, String text, List<Hit> hits, Maker maker) {
        Matcher m = p.matcher(text);
        while (m.find()) {
            Period period = maker.make(m);
            if (period != null) hits.add(new Hit(m.start(), period));
        }
    }

    private static Integer number(String token) {
        if (token.matches("\\d{1,2}")) return Integer.parseInt(token);
        return NUMBER_WORDS.get(token);
    }

    private static void monthNames(String normalized, LocalDate anchor, List<Hit> hits) {
        List<String> tokens = TextNormalizer.tokens(normalized);
        int offset = 0;
        for (int i = 0; i < tokens.size(); i++) {
            String t = tokens.get(i);
            Integer month = MONTH_NAMES.get(t);
            if (month != null && !isAmbiguousWordUsedAsEnglish(t, tokens, i)) {
                Integer year = null;
                String next = i + 1 < tokens.size() ? tokens.get(i + 1) : "";
                String prev = i > 0 ? tokens.get(i - 1) : "";
                if (next.matches("20\\d\\d")) year = Integer.parseInt(next);
                else if (prev.matches("20\\d\\d")) year = Integer.parseInt(prev);
                int y = year != null ? year : (month <= anchor.getMonthValue() ? anchor.getYear() : anchor.getYear() - 1);
                hits.add(new Hit(offset, Period.month(YearMonth.of(y, month))));
            }
            offset += t.length() + 1;
        }
    }

    /** "may" and "mai" are also ordinary words ("may I ask", Hindi "main"), so need a month-like context. */
    private static boolean isAmbiguousWordUsedAsEnglish(String token, List<String> tokens, int i) {
        if (!token.equals("may") && !token.equals("mai")) return false;
        String prev = i > 0 ? tokens.get(i - 1) : "";
        String next = i + 1 < tokens.size() ? tokens.get(i + 1) : "";
        boolean monthContext = prev.equals("in") || prev.equals("of") || prev.equals("for") || prev.equals("during")
                || prev.equals("month") || next.equals("month") || next.matches("20\\d\\d") || prev.matches("20\\d\\d")
                || next.equals("mein") || next.equals("me") || next.equals("ka") || next.equals("ke");
        return !monthContext;
    }
}
