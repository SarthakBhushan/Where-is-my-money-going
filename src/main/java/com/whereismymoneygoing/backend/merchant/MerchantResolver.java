package com.whereismymoneygoing.backend.merchant;


import com.whereismymoneygoing.backend.model.Transaction;
import com.whereismymoneygoing.backend.model.TransactionType;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class MerchantResolver {

    private static final Set<String> CREDIT_ONLY = Set.of("Dividend Credit", "Interest Credit");

    private record CompiledRule(String merchant, List<List<String>> keywords) {}

    private final MerchantRuleStore store;
    private volatile List<CompiledRule> rules = List.of();

    public MerchantResolver(MerchantRuleStore store) {
        this.store = store;
        reload();
    }

    /** Re-reads rule files; call after a rule is added. Keywords are normalized once, not per transaction. */
    public final void reload() {
        List<CompiledRule> compiled = new ArrayList<>();
        for (MerchantRule rule : store.allRules()) {
            List<List<String>> keywords = new ArrayList<>();
            for (String keyword : rule.contains()) {
                List<String> words = NarrationPreprocessor.words(
                        NarrationPreprocessor.preprocess(NarrationNormalizer.normalize(keyword)));
                if (!words.isEmpty()) keywords.add(words);
            }
            compiled.add(new CompiledRule(rule.merchant(), keywords));
        }
        this.rules = compiled;
    }

    public String resolve(String narration, TransactionType type) {
        List<String> words = NarrationPreprocessor.words(
                NarrationPreprocessor.preprocess(NarrationNormalizer.normalize(narration)));

        for (CompiledRule rule : rules) {
            for (List<String> keyword : rule.keywords()) {
                if (phraseMatches(words, keyword)) {
                    if (type == TransactionType.DEBIT && CREDIT_ONLY.contains(rule.merchant())) continue;
                    return rule.merchant();
                }
            }
        }
        return Transaction.UNKNOWN_MERCHANT;
    }

    /** Contiguous word run, or the glued keyword embedded inside one narration word (ACTFIBERNET). */
    static boolean phraseMatches(List<String> words, List<String> keywordWords) {
        int run = keywordWords.size();
        for (int i = 0; i + run <= words.size(); i++) {
            if (words.subList(i, i + run).equals(keywordWords)) return true;
        }
        String glued = String.join("", keywordWords);
        return !glued.isEmpty() && words.stream().anyMatch(w -> w.contains(glued));
    }
}