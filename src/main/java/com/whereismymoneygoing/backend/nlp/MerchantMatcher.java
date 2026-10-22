package com.whereismymoneygoing.backend.nlp;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Finds which merchant from the statement a question is about. Handles brand spellings in Devanagari
 * (ज़ोमैटो), common Hinglish variants and small typos (zomatao).
 */
final class MerchantMatcher {

    /** alias (any script) -> compact key of the merchant it means. */
    private static final Map<String, String> ALIASES = new HashMap<>();
    private static void alias(String merchant, String... aliases) {
        String key = TextNormalizer.compact(merchant);
        for (String a : aliases) ALIASES.put(TextNormalizer.compact(a), key);
    }
    static {
        alias("Zomato", "ज़ोमैटो", "जोमैटो", "जोमाटो", "zomaato", "jomato", "zomatto");
        alias("Swiggy", "स्विगी", "स्वीगी", "swigy", "swiggi", "swigi");
        alias("Instamart", "इंस्टामार्ट");
        alias("Amazon", "अमेज़न", "अमेजन", "अमेज़ॉन", "amazan", "amzon", "amzn");
        alias("Flipkart", "फ्लिपकार्ट", "फ्लिपकार्ट", "flipcart", "flipkarth");
        alias("Myntra", "मिंत्रा", "मयंत्रा", "mintra");
        alias("Blinkit", "ब्लिंकिट", "ब्लिंकइट", "blinkit", "blinket");
        alias("Zepto", "ज़ेप्टो", "ज़ेप्टो", "zeptoo");
        alias("BigBasket", "बिगबास्केट", "बिग बास्केट", "big basket");
        alias("DMart", "डीमार्ट", "डी मार्ट", "d mart");
        alias("Netflix", "नेटफ्लिक्स", "netflicks");
        alias("Disney+ Hotstar", "हॉटस्टार", "हॉटस्टार", "hotstar");
        alias("Spotify", "स्पॉटिफाई", "स्पोटिफाई", "spotifi");
        alias("Uber", "उबर", "ooba");
        alias("Ola", "ओला");
        alias("Rapido", "रैपिडो", "रेपिडो");
        alias("Paytm", "पेटीएम", "paytem", "pay tm");
        alias("PhonePe", "फोनपे", "फोन पे", "phone pe", "phonepay");
        alias("Google Pay", "गूगल पे", "गूगलपे", "जीपे", "gpay", "g pay", "googlepe");
        alias("IRCTC", "आईआरसीटीसी", "irctc");
        alias("MakeMyTrip", "मेकमायट्रिप", "make my trip", "mmt");
        alias("IndiGo", "इंडिगो", "indigo");
        alias("BookMyShow", "बुकमायशो", "book my show", "bms");
        alias("Nykaa", "नायका", "nykaa", "nayka");
        alias("Ajio", "अजियो");
        alias("Meesho", "मीशो", "meshoo");
        alias("McDonald's", "मैकडोनाल्ड्स", "मैकडॉनल्ड्स", "मैक्डोनाल्ड", "mcdonald", "mcd", "mac d");
        alias("KFC", "केएफसी");
        alias("Domino's", "डोमिनोज़", "डोमिनोज", "dominos", "domino");
        alias("Pizza Hut", "पिज़्ज़ा हट", "पिज्जा हट", "pizzahut");
        alias("Starbucks", "स्टारबक्स", "starbuck");
        alias("Jio", "जियो");
        alias("Vodafone Idea", "वोडाफोन", "vodafone", "vi");
        alias("Airtel Xstream", "एयरटेल", "airtel");
        alias("LIC", "एलआईसी");
        alias("Electricity Bill", "बिजली", "bijli", "light bill", "बिजली बिल");
        alias("Gas / LPG", "गैस", "gas", "lpg", "एलपीजी");
        alias("Car Fuel", "पेट्रोल", "petrol", "fuel", "पेट्रोल पंप");
        alias("Mutual Funds", "म्यूचुअल फंड", "म्यूचुअल फंड्स", "mutual fund");
        alias("Cult.fit", "क्यूरफिट", "cult fit", "cultfit");
        alias("Decathlon", "डेकाथलॉन");
        alias("IKEA", "इकिया", "इकेया");
    }

    private final Map<String, String> keyToMerchant = new HashMap<>();   // compact key -> display name

    MerchantMatcher(Collection<String> merchants) {
        Map<String, List<String>> firstWordOwners = new HashMap<>();
        for (String m : merchants) {
            String compact = TextNormalizer.compact(m);
            if (compact.isEmpty()) continue;
            keyToMerchant.put(compact, m);
            List<String> words = TextNormalizer.tokens(TextNormalizer.normalize(m));
            if (words.size() > 1 && words.get(0).length() >= 4) {
                firstWordOwners.computeIfAbsent(words.get(0), k -> new ArrayList<>()).add(m);
            }
        }
        // "amazon" should find "Amazon Prime Video" when it is the only Amazon merchant in the statement.
        firstWordOwners.forEach((word, owners) -> {
            if (owners.size() == 1) keyToMerchant.putIfAbsent(word, owners.get(0));
        });
        ALIASES.forEach((alias, target) -> {
            String merchant = keyToMerchant.get(target);
            if (merchant != null) keyToMerchant.putIfAbsent(alias, merchant);
        });
    }

    Optional<String> find(List<String> tokens) {
        String best = null;
        int bestDistance = Integer.MAX_VALUE;
        int bestLength = -1;
        for (int start = 0; start < tokens.size(); start++) {
            StringBuilder gram = new StringBuilder();
            for (int len = 1; len <= 3 && start + len <= tokens.size(); len++) {
                gram.append(tokens.get(start + len - 1));
                String g = gram.toString();
                if (len == 1 && (Lexicon.isCommonWord(g) || isPlainSmallWord(g))) continue;

                String exact = keyToMerchant.get(g);
                if (exact != null && g.length() >= 2) {
                    if (bestDistance > 0 || g.length() > bestLength) {
                        best = exact; bestDistance = 0; bestLength = g.length();
                    }
                    continue;
                }
                if (g.length() < 4 || Lexicon.isCommonWord(g)) continue;
                for (Map.Entry<String, String> e : keyToMerchant.entrySet()) {
                    String key = e.getKey();
                    if (key.length() < 4) continue;
                    int allowed = allowedTypos(key.length());
                    if (allowed == 0) continue;
                    if (Math.abs(key.length() - g.length()) > allowed) continue;
                    int d = editDistance(g, key, allowed);
                    if (d <= allowed && (d < bestDistance || (d == bestDistance && key.length() > bestLength))) {
                        best = e.getValue(); bestDistance = d; bestLength = key.length();
                    }
                }
            }
        }
        return Optional.ofNullable(best);
    }

    /** Short brand names must match exactly (a one-letter typo of "uber" is more likely the word "over"). */
    private static int allowedTypos(int keyLength) {
        return keyLength <= 5 ? 0 : keyLength <= 9 ? 1 : 2;
    }

    /** Short everyday words ("is", "me", "do") are matched only as exact brand keys of length >= 3. */
    private static boolean isPlainSmallWord(String g) {
        return g.length() <= 2;
    }

    /** Optimal-string-alignment distance (insert, delete, replace, swap neighbours); gives up above {@code limit}. */
    static int editDistance(String a, String b, int limit) {
        int n = a.length(), m = b.length();
        int[][] d = new int[n + 1][m + 1];
        for (int i = 0; i <= n; i++) d[i][0] = i;
        for (int j = 0; j <= m; j++) d[0][j] = j;
        for (int i = 1; i <= n; i++) {
            for (int j = 1; j <= m; j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                d[i][j] = Math.min(Math.min(d[i - 1][j] + 1, d[i][j - 1] + 1), d[i - 1][j - 1] + cost);
                if (i > 1 && j > 1 && a.charAt(i - 1) == b.charAt(j - 2) && a.charAt(i - 2) == b.charAt(j - 1)) {
                    d[i][j] = Math.min(d[i][j], d[i - 2][j - 2] + 1);
                }
            }
        }
        return d[n][m] > limit ? limit + 1 : d[n][m];
    }
}
