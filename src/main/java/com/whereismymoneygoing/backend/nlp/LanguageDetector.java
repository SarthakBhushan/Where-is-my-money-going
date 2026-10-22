package com.whereismymoneygoing.backend.nlp;

import java.util.List;
import java.util.Set;

/** Devanagari letters mean Hindi; common Roman-Hindi words mean Hinglish; everything else is English. */
public final class LanguageDetector {

    private LanguageDetector() {}

    private static final Set<String> HINGLISH_MARKERS = Set.of(
            "kitna", "kitne", "kitni", "kharcha", "kharch", "kharche", "paisa", "paise", "paiso", "rupaye",
            "bachat", "batao", "bata", "btao", "bataiye", "dikhao", "dikha", "kaise", "kahan", "kahaan", "kidhar",
            "sabse", "zyada", "jyada", "mahine", "mahina", "mahino", "pichle", "pichhle", "pichla", "aaj",
            "hai", "hain", "hua", "hui", "hue", "kiya", "kiye", "mera", "meri", "mere", "mujhe", "aapne", "maine",
            "mene", "ka", "ki", "ke", "ko", "par", "pe", "mein", "mei", "nahi", "kya", "kyu", "kyun", "hafte",
            "hafta", "tha", "thi", "hoon", "kamai", "aamdani", "aur", "isme", "isse", "wala", "wali", "gaya", "gaye");

    public static Language detect(String text) {
        if (text == null || text.isBlank()) return Language.ENGLISH;
        int devanagari = 0;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            if (Character.UnicodeScript.of(cp) == Character.UnicodeScript.DEVANAGARI) devanagari++;
            i += Character.charCount(cp);
        }
        if (devanagari >= 2) return Language.HINDI;
        List<String> tokens = TextNormalizer.tokens(TextNormalizer.normalize(text));
        for (String t : tokens) if (HINGLISH_MARKERS.contains(t)) return Language.HINGLISH;
        return Language.ENGLISH;
    }
}
