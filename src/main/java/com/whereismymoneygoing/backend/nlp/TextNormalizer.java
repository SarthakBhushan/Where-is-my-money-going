package com.whereismymoneygoing.backend.nlp;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;

/**
 * Makes Hindi, Hinglish and English text comparable: lower-case, no punctuation, Devanagari digits turned into
 * ASCII digits, and the Devanagari nukta / chandrabindu / anusvara dropped so spelling variants match
 * (ज़ोमैटो = जोमैटो, सितंबर = सितबर). Dictionary entries go through the same function.
 */
public final class TextNormalizer {

    private TextNormalizer() {}

    public static String normalize(String s) {
        if (s == null) return "";
        String t = Normalizer.normalize(s, Normalizer.Form.NFC);   // splits ज़ into ज + nukta
        StringBuilder sb = new StringBuilder(t.length());
        t.codePoints().forEach(cp -> {
            if (cp == 0x093C || cp == 0x0901 || cp == 0x0902 || cp == 0x200C || cp == 0x200D) return;
            int type = Character.getType(cp);
            if (type == Character.DECIMAL_DIGIT_NUMBER) {
                sb.append((char) ('0' + Character.digit(cp, 10)));
            } else if (Character.isLetter(cp) || type == Character.NON_SPACING_MARK
                    || type == Character.COMBINING_SPACING_MARK) {
                sb.appendCodePoint(Character.toLowerCase(cp));
            } else {
                sb.append(' ');
            }
        });
        return sb.toString().trim().replaceAll(" +", " ");
    }

    public static List<String> tokens(String normalized) {
        return normalized.isEmpty() ? List.of() : Arrays.asList(normalized.split(" "));
    }

    /** Normalised with spaces removed: "Barbeque Nation" and "barbeque nation" both become "barbequenation". */
    public static String compact(String s) {
        return normalize(s).replace(" ", "");
    }
}
