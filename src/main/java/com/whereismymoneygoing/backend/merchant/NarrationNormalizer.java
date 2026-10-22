package com.whereismymoneygoing.backend.merchant;

import java.util.Locale;
import java.util.regex.Pattern;

public class NarrationNormalizer {

    private static final Pattern SEPARATORS = Pattern.compile("[-_/\\\\\\\\.,:;()]+");
    private static final Pattern SPACES = Pattern.compile("\\s+");

    private NarrationNormalizer(){}

    public static String normalize(String narration){
        String text = narration.toUpperCase(Locale.ROOT);
        text = SEPARATORS.matcher(text).replaceAll(" ");
        text = SPACES.matcher(text).replaceAll(" ");
        return text.strip();
    }
}
