package com.whereismymoneygoing.backend.nlp;

import com.whereismymoneygoing.backend.model.TransactionKind;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Keyword lists in English, Hinglish (Roman Hindi) and Devanagari. Entries are normalised on load. */
final class Lexicon {

    private Lexicon() {}

    /** A word list: exact words, plus prefixes so inflections (kharcha, kharche, खर्चा) match too. */
    record Lex(Set<String> exact, List<String> prefixes) {
        static Lex of(String exactWords, String prefixWords) {
            Set<String> e = new HashSet<>();
            for (String w : exactWords.split("\\s+")) if (!w.isBlank()) e.add(TextNormalizer.normalize(w));
            List<String> p = new ArrayList<>();
            for (String w : prefixWords.split("\\s+")) if (!w.isBlank()) p.add(TextNormalizer.normalize(w));
            return new Lex(e, p);
        }

        boolean matches(String token) {
            if (exact.contains(token)) return true;
            for (String p : prefixes) if (token.startsWith(p)) return true;
            return false;
        }

        boolean in(List<String> tokens) {
            for (String t : tokens) if (matches(t)) return true;
            return false;
        }
    }

    static final Lex SPEND = Lex.of(
            "spend spent spending expense expenses cost costs paid pay paying amount total kitna kitne kitni "
          + "paisa paise paiso rupaye rupees rs inr gaya gaye diya diye lage laga कितना कितने कितनी",
            "kharch खर्च पैस");
    static final Lex INCOME = Lex.of(
            "income salary earn earned earning earnings credited received receive kamai aamdani mila mile "
          + "तनख्वाह वेतन सैलरी",
            "कमाई कमा आमदनी आय");
    static final Lex SAVINGS = Lex.of(
            "saving savings saved save bachat bacha bachi bachaye bachana",
            "बचत बचा bachat");
    static final Lex ADVICE = Lex.of(
            "advice tips tip suggest suggestion suggestions recommend salah sujhav reduce cut improve ideas idea "
          + "plan bachau bachaun bachane सलाह सुझाव",
            "सलाह सुझाव");
    static final Lex TOP = Lex.of(
            "top most highest biggest largest max maximum sabse sbse zyada jyada jyaada bada badi bade सबसे "
          + "ज्यादा ज़्यादा ज़्यादा ज्यादा बड़ा बड़ी बड़े",
            "ज्यादा ज़्यादा");
    static final Lex WHERE = Lex.of(
            "where kahan kahaan kaha kidhar kis kisme kisko merchant merchants shop shops store stores place "
          + "places jagah dukan vendor कहाँ कहां कहा किस दुकान जगह",
            "कहा किस दुकान");
    static final Lex SINGLE = Lex.of(
            "single transaction transactions payment payments ek एक", "");
    static final Lex SUBSCRIPTIONS = Lex.of(
            "subscription subscriptions recurring autopay सब्सक्रिप्शन", "subscri recurr सब्सक्रिप");
    static final Lex COMPARE = Lex.of(
            "compare comparison compared versus vs difference differ tulna badha ghata increase decrease "
          + "changed change तुलना अंतर",
            "compar तुलना");

    static final Map<TransactionKind, Lex> CATEGORIES = Map.of(
            TransactionKind.INVESTMENT, Lex.of(
                    "investment investments invest invested sip mutual mf stocks stock shares nivesh", "निवेश nivesh"),
            TransactionKind.INSURANCE, Lex.of(
                    "insurance premium premiums lic bima", "बीमा"),
            TransactionKind.CASH_WITHDRAWAL, Lex.of(
                    "cash atm withdrawal withdrawals nakad nakdi", "नकद"),
            TransactionKind.TRANSFER, Lex.of(
                    "transfer transfers family ghar bheja bheje", "घर भेज परिवार"),
            TransactionKind.PURCHASE, Lex.of(
                    "lifestyle shopping purchase purchases kharidari", "खरीद"));

    /** Words that are never merchant names, so fuzzy matching must not turn them into one. */
    private static final Set<String> COMMON = new HashSet<>();
    static {
        for (Lex l : new Lex[]{SPEND, INCOME, SAVINGS, ADVICE, TOP, WHERE, SINGLE, SUBSCRIPTIONS, COMPARE}) {
            COMMON.addAll(l.exact());
        }
        for (Lex l : CATEGORIES.values()) COMMON.addAll(l.exact());
        for (String w : ("what this that last next month months week weeks year show tell give much many from with "
                + "have has did does was were your mine about please paid payment statement spending money rupees "
                + "pichle pichhle mahine mahina mahino hafte hafta batao bata bataiye dikhao dikha mera meri mere "
                + "mujhe aapne maine kitna hua hui hue kiya kiye mein aaj kal yeh ye iss isme isse hain "
                + "january february march april june july august september october november december "
                + "janwari farwari agast sitambar aktubar navambar disambar more less same other another next first second "
                + "max big star cafe tax fees card bill good best high low only also just like some very over user "
                + "need want know main hum tum aap kuch sab bahut thoda zara phir abhi").split(" ")) {
            COMMON.add(TextNormalizer.normalize(w));
        }
    }

    static boolean isCommonWord(String token) {
        return COMMON.contains(token);
    }
}
