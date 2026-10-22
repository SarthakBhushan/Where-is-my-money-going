package com.whereismymoneygoing.backend.nlp;

import com.whereismymoneygoing.backend.model.TransactionKind;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/** Answer sentences in English, Hinglish and Hindi. {name} placeholders are filled by {@link #t}. */
final class Messages {

    private Messages() {}

    private static final String[] MONTHS_EN = {"January", "February", "March", "April", "May", "June", "July",
            "August", "September", "October", "November", "December"};
    private static final String[] MONTHS_HI = {"जनवरी", "फरवरी", "मार्च", "अप्रैल", "मई", "जून", "जुलाई",
            "अगस्त", "सितंबर", "अक्टूबर", "नवंबर", "दिसंबर"};

    private static final Map<String, String[]> T = new HashMap<>();
    private static void put(String key, String en, String hinglish, String hindi) {
        T.put(key, new String[]{en, hinglish, hindi});
    }

    static {
        put("merchant.spend",
                "You spent {amt} on {m} {inP} ({n} {pay}, about {avg} each).",
                "Aapne {inP} {m} par {amt} kharch kiye ({n} {pay}, average {avg} per payment).",
                "आपने {inP} {m} पर {amt} खर्च किए ({n} {pay}, औसतन {avg} प्रति भुगतान)।");
        put("merchant.none",
                "I found no payments to {m} {inP}.",
                "Mujhe {inP} {m} ko koi payment nahi mila.",
                "मुझे {inP} {m} के लिए कोई भुगतान नहीं मिला।");
        put("change.up",
                " That is {pct}% higher than {prev} ({prevAmt}).",
                " Ye {prev} ({prevAmt}) se {pct}% zyada hai.",
                " यह {prev} ({prevAmt}) से {pct}% ज़्यादा है।");
        put("change.down",
                " That is {pct}% lower than {prev} ({prevAmt}).",
                " Ye {prev} ({prevAmt}) se {pct}% kam hai.",
                " यह {prev} ({prevAmt}) से {pct}% कम है।");
        put("change.same",
                " That is the same as {prev} ({prevAmt}).",
                " Ye {prev} ({prevAmt}) ke barabar hai.",
                " यह {prev} ({prevAmt}) के बराबर है।");
        put("change.zero",
                " There were no such payments in {prev}.",
                " {prev} mein aisa koi payment nahi hua tha.",
                " {prev} में ऐसा कोई भुगतान नहीं हुआ था।");
        put("category.spend",
                "You spent {amt} on {cat} {inP} ({n} {pay}).",
                "Aapne {inP} {cat} par {amt} kharch kiye ({n} {pay}).",
                "आपने {inP} {cat} पर {amt} खर्च किए ({n} {pay})।");
        put("category.none",
                "I found no {cat} spending {inP}.",
                "Mujhe {inP} {cat} par koi kharcha nahi mila.",
                "मुझे {inP} {cat} पर कोई खर्च नहीं मिला।");
        put("total.spend",
                "You spent {amt} {inP} across {n} {pay}. Most of it went to {top} ({topAmt}).",
                "Aapne {inP} kul {amt} kharch kiye ({n} {pay}). Sabse zyada {top} par gaya ({topAmt}).",
                "आपने {inP} कुल {amt} खर्च किए ({n} {pay})। सबसे ज़्यादा {top} पर गया ({topAmt})।");
        put("nodata",
                "I found no transactions {inP}. Your statement covers {from} to {to}.",
                "Mujhe {inP} koi transaction nahi mila. Aapka statement {from} se {to} tak ka hai.",
                "मुझे {inP} कोई लेन-देन नहीं मिला। आपका स्टेटमेंट {from} से {to} तक का है।");
        put("income",
                "You received {amt} {inP} ({n} credits).",
                "Aapke account mein {inP} {amt} aaye ({n} credits).",
                "आपके खाते में {inP} कुल {amt} आए ({n} बार)।");
        put("income.none",
                "I found no money received {inP}.",
                "Mujhe {inP} koi paisa aaya hua nahi mila.",
                "मुझे {inP} कोई जमा राशि नहीं मिली।");
        put("savings.pos",
                "{inP}, you received {inc} and spent {out}, so you saved {amt}.",
                "{inP} aapko {inc} mile aur {out} kharch hue, yaani aapne {amt} bachaye.",
                "{inP} आपकी कुल आमदनी {inc} और खर्च {out} रहा, यानी आपने {amt} बचाए।");
        put("savings.neg",
                "{inP}, you received {inc} and spent {out}, so you spent {amt} more than you received.",
                "{inP} aapko {inc} mile aur {out} kharch hue, yaani aapne aamdani se {amt} zyada kharch kiya.",
                "{inP} आपकी कुल आमदनी {inc} और खर्च {out} रहा, यानी खर्च आमदनी से {amt} ज़्यादा रहा।");
        put("top.header",
                "Where most of your money went {inP}:",
                "{inP} sabse zyada paisa yahan gaya:",
                "{inP} सबसे ज़्यादा पैसा यहाँ गया:");
        put("top.line",
                "{i}. {m}: {amt} ({n} {pay}, {pct} of spending)",
                "{i}. {m}: {amt} ({n} {pay}, kul kharche ka {pct})",
                "{i}. {m}: {amt} ({n} {pay}, कुल खर्च का {pct})");
        put("biggest.header",
                "Your largest payments {inP}:",
                "{inP} aapke sabse bade payments:",
                "{inP} आपके सबसे बड़े भुगतान:");
        put("biggest.line",
                "{i}. {date}: {amt} to {m}",
                "{i}. {date}: {m} ko {amt}",
                "{i}. {date}: {m} को {amt}");
        put("subs.header",
                "Recurring payments I found:",
                "Mujhe ye recurring payments mile:",
                "मुझे ये आवर्ती भुगतान मिले:");
        put("subs.line",
                "- {m}: {amt} {cad} (about {yr} a year)",
                "- {m}: {amt} {cad} (saal mein lagbhag {yr})",
                "- {m}: {amt} {cad} (साल में लगभग {yr})");
        put("subs.none",
                "I didn't find any recurring payments in this statement.",
                "Is statement mein mujhe koi recurring payment nahi mila.",
                "इस स्टेटमेंट में मुझे कोई आवर्ती भुगतान नहीं मिला।");
        put("compare.main",
                "Spending in {new}: {newAmt}; in {old}: {oldAmt}. That is {pct}% {dir} in {new}.",
                "Kharcha {new} mein {newAmt}, {old} mein {oldAmt}. {new} mein {pct}% {dir} raha.",
                "खर्च {new} में {newAmt}, {old} में {oldAmt}। {new} में {pct}% {dir} रहा।");
        put("compare.nobase",
                "Spending in {new}: {newAmt}; nothing was recorded in {old}.",
                "Kharcha {new} mein {newAmt}; {old} mein kuch record nahi hai.",
                "खर्च {new} में {newAmt}; {old} में कुछ दर्ज नहीं है।");
        put("compare.nopast",
                "I can't compare {new}, because your statement has no earlier data for it.",
                "Main {new} ko compare nahi kar sakta, kyunki statement mein isse pehle ka data nahi hai.",
                "मैं {new} की तुलना नहीं कर सकता, क्योंकि स्टेटमेंट में इससे पहले का डेटा नहीं है।");
        put("compare.header",
                "Biggest changes by merchant:",
                "Merchant ke hisaab se sabse bade badlav:",
                "दुकान के हिसाब से सबसे बड़े बदलाव:");
    }

    static String t(Language l, String key, String... kv) {
        String s = T.get(key)[l.ordinal()];
        for (int i = 0; i + 1 < kv.length; i += 2) s = s.replace("{" + kv[i] + "}", kv[i + 1]);
        return s;
    }

    static String cap(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    static String monthName(Language l, int month) {
        return (l == Language.HINDI ? MONTHS_HI : MONTHS_EN)[month - 1];
    }

    static String date(Language l, LocalDate d) {
        String month = l == Language.HINDI ? monthName(l, d.getMonthValue())
                : monthName(l, d.getMonthValue()).substring(0, 3);
        return d.getDayOfMonth() + " " + month + " " + d.getYear();
    }

    static String monthYear(Language l, java.time.YearMonth ym) {
        return monthName(l, ym.getMonthValue()) + " " + ym.getYear();
    }

    static String payments(Language l, int n) {
        return switch (l) {
            case HINDI -> "भुगतान";
            case HINGLISH -> n == 1 ? "payment" : "payments";
            case ENGLISH -> n == 1 ? "payment" : "payments";
        };
    }

    static String unrecognised(Language l) {
        return switch (l) {
            case HINDI -> "अज्ञात दुकानें";
            default -> "Unrecognised merchants";
        };
    }

    static String direction(Language l, boolean higher) {
        return switch (l) {
            case HINDI -> higher ? "ज़्यादा" : "कम";
            case HINGLISH -> higher ? "zyada" : "kam";
            case ENGLISH -> higher ? "higher" : "lower";
        };
    }

    static String category(Language l, TransactionKind k) {
        if (l != Language.HINDI) return k.label();
        return switch (k) {
            case PURCHASE -> "जीवनशैली खर्च";
            case INVESTMENT -> "निवेश";
            case TRANSFER -> "परिवार/खुद को ट्रांसफर";
            case CASH_WITHDRAWAL -> "नकद निकासी";
            case INSURANCE -> "बीमा";
            case UNKNOWN -> "अज्ञात";
        };
    }

    static String cadence(Language l, String analyticsCadence) {
        if ("Monthly".equalsIgnoreCase(analyticsCadence)) {
            return switch (l) { case HINDI -> "हर महीने"; case HINGLISH -> "har mahine"; case ENGLISH -> "monthly"; };
        }
        String days = analyticsCadence.replaceAll("\\D", "");
        return switch (l) {
            case HINDI -> "हर " + days + " दिन";
            case HINGLISH -> "har " + days + " din";
            case ENGLISH -> "every " + days + " days";
        };
    }

    /** Noun-like name of the period: "October 2026", "12 Mar 2026", "the last 3 months", ... */
    static String periodLabel(Language l, Period p, LocalDate stmtFrom, LocalDate stmtTo) {
        return switch (p.kind()) {
            case MONTH -> monthYear(l, p.yearMonth());
            case DAY -> date(l, p.from());
            case WEEK -> date(l, p.from()) + " - " + date(l, p.to());
            case LAST_N_MONTHS -> switch (l) {
                case HINDI -> "पिछले " + p.months() + " महीनों";
                case HINGLISH -> "pichle " + p.months() + " mahine";
                case ENGLISH -> "the last " + p.months() + " months";
            };
            case ALL -> {
                String range = monthYear(l, java.time.YearMonth.from(stmtFrom)) + " - "
                        + monthYear(l, java.time.YearMonth.from(stmtTo));
                yield switch (l) {
                    case HINDI -> "पूरे स्टेटमेंट (" + range + ")";
                    case HINGLISH -> "poore statement (" + range + ")";
                    case ENGLISH -> "the whole statement (" + range + ")";
                };
            }
        };
    }

    /** "in October 2026", "October 2026 mein", "October 2026 में"; days use "on" / "ko" / "को". */
    static String inPeriod(Language l, Period p, String label) {
        return switch (l) {
            case ENGLISH -> switch (p.kind()) {
                case DAY -> "on " + label;
                case WEEK -> "during " + label;
                case ALL -> "across " + label;
                default -> "in " + label;
            };
            case HINGLISH -> label + (p.kind() == Period.Kind.DAY ? " ko" : " mein");
            case HINDI -> label + (p.kind() == Period.Kind.DAY ? " को" : " में");
        };
    }
}
