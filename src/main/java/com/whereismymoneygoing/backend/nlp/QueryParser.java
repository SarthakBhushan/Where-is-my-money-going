package com.whereismymoneygoing.backend.nlp;

import com.whereismymoneygoing.backend.model.TransactionKind;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Rule-based understanding of a question in English, Hinglish or Hindi. No model, works offline. */
@Component
public class QueryParser {

    private static final Set<String> FOLLOW_UP_MARKERS = Set.of("aur", "and", "also", "ab", "phir", "what", "how",
            TextNormalizer.normalize("और"), TextNormalizer.normalize("फिर"), TextNormalizer.normalize("अब"));

    /**
     * @param anchor    latest date in the statement; "this month" means the month of this date
     * @param merchants merchant names that occur in the statement
     * @param previous  the previous question in this chat, used for short follow-ups; may be null
     */
    public ParsedQuery parse(String text, LocalDate anchor, Collection<String> merchants, ParsedQuery previous) {
        Language language = LanguageDetector.detect(text);
        String normalized = TextNormalizer.normalize(text);
        List<String> tokens = TextNormalizer.tokens(normalized);

        List<Period> periods = PeriodParser.parseAll(normalized, anchor);
        String merchant = new MerchantMatcher(merchants).find(tokens).orElse(null);
        TransactionKind kind = merchant != null ? null : category(tokens);

        boolean spend = Lexicon.SPEND.in(tokens);
        boolean savings = Lexicon.SAVINGS.in(tokens);
        boolean howMuch = tokens.stream().anyMatch(t -> Lexicon.SPEND.matches(t) && !t.equals("total") && !t.equals("pay"))
                || (tokens.contains("how") && tokens.contains("much"));
        boolean howTo = tokens.contains("how") || tokens.contains("kaise") || tokens.contains(TextNormalizer.normalize("कैसे"))
                || tokens.contains("more");
        boolean advice = Lexicon.ADVICE.in(tokens) || (savings && howTo && !howMuch);

        Intent intent;
        if (Lexicon.SUBSCRIPTIONS.in(tokens)) intent = Intent.SUBSCRIPTIONS;
        else if (Lexicon.COMPARE.in(tokens)) intent = Intent.COMPARE;
        else if (advice) intent = Intent.ADVICE;
        else if (savings) intent = Intent.SAVINGS;
        else if (Lexicon.INCOME.in(tokens) && merchant == null) intent = Intent.INCOME;
        else if (merchant != null) intent = Intent.MERCHANT_SPEND;
        else if (kind != null) intent = Intent.CATEGORY_SPEND;
        else if (Lexicon.TOP.in(tokens)) {
            intent = Lexicon.SINGLE.in(tokens) && !Lexicon.WHERE.in(tokens) ? Intent.BIGGEST_EXPENSE : Intent.TOP_MERCHANTS;
        } else if (spend) intent = Intent.TOTAL_SPEND;
        else intent = Intent.UNKNOWN;

        boolean inherited = false;
        if (previous != null && previous.intent().hasDataAnswer()) {
            boolean newInfo = merchant != null || kind != null || !periods.isEmpty();
            boolean followUpWord = tokens.stream().anyMatch(FOLLOW_UP_MARKERS::contains);
            if (intent == Intent.UNKNOWN && newInfo) {
                intent = previous.intent();
                if (merchant == null && kind == null) { merchant = previous.merchant(); kind = previous.kind(); }
                if (periods.isEmpty()) periods = previous.periods();
                inherited = true;
            } else if ((intent == Intent.MERCHANT_SPEND || intent == Intent.CATEGORY_SPEND) && periods.isEmpty()
                    && followUpWord && tokens.size() <= 6 && previous.hasPeriod()) {
                periods = previous.periods();   // "what about swiggy" keeps the month we were just discussing
                inherited = true;
            } else if (intent == Intent.TOTAL_SPEND && merchant == null && kind == null
                    && previous.intent() == Intent.MERCHANT_SPEND && tokens.size() <= 6 && followUpWord) {
                intent = Intent.MERCHANT_SPEND;
                merchant = previous.merchant();
                inherited = true;
            }
        }
        return new ParsedQuery(language, intent, merchant, kind, periods, inherited);
    }

    private static TransactionKind category(List<String> tokens) {
        for (Map.Entry<TransactionKind, Lexicon.Lex> e : Lexicon.CATEGORIES.entrySet()) {
            if (e.getValue().in(tokens)) return e.getKey();
        }
        return null;
    }
}
