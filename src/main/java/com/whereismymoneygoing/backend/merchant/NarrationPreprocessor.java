package com.whereismymoneygoing.backend.merchant;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class NarrationPreprocessor {
    private static final Set<String> TRANSACTION_WORDS =
            Set.of("POS", "UPI", "IMPS", "NEFT", "RTGS", "ACH", "ATM", "DR", "CR", "NETBANK");
    private static final Pattern NUMBER = Pattern.compile("\\b\\d+\\b");
    private static final Pattern CARD = Pattern.compile("\\b\\d+X+\\d+\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern UPI_PREFIX = Pattern.compile("^UPI\\s*-\\s*", Pattern.CASE_INSENSITIVE);
    private static final Pattern POS_PREFIX = Pattern.compile("^POS\\s+", Pattern.CASE_INSENSITIVE);

    private NarrationPreprocessor() {}

    public static List<String> words(String text) {
        return Arrays.stream(text.strip().split("\\s+")).filter(w -> !w.isEmpty()).toList();
    }

    public static String preprocess(String text) {
        String t = words(text).stream()
                .filter(w -> !TRANSACTION_WORDS.contains(w))
                .collect(Collectors.joining(" "));
        t = CARD.matcher(t).replaceAll(" ");
        t = NUMBER.matcher(t).replaceAll(" ");
        return String.join(" ", words(t));
    }

    public static String extractKeyword(String narration) {
        String text = narration.strip();

        Matcher upi = UPI_PREFIX.matcher(text);
        if (upi.find()) {
            String segment = text.substring(upi.end()).split("-", 2)[0].strip();
            if (!segment.isEmpty()) return segment;
        }

        Matcher pos = POS_PREFIX.matcher(text);
        if (pos.find()) {
            List<String> tokens = new ArrayList<>(words(text.substring(pos.end())));
            if (!tokens.isEmpty() && isCardNumber(tokens.get(0))) tokens.remove(0);
            if (!tokens.isEmpty()) return String.join(" ", tokens).strip();
        }
        return text;
    }

    private static boolean isCardNumber(String token) {
        return CARD.matcher(token).lookingAt()
                || (token.length() >= 12 && token.chars().allMatch(Character::isDigit));
    }
}