package com.whereismymoneygoing.backend.nlp;

import com.whereismymoneygoing.backend.model.ParsedStatement;
import com.whereismymoneygoing.backend.model.Transaction;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/** Understands a chat question and, when it has a data answer, computes it. Remembers the last question per chat. */
@Service
public class QueryUnderstandingService {

    /** {@code answer} is null when the question needs the model (advice, open-ended). */
    public record Understanding(ParsedQuery query, Answer answer) {
        public boolean answered() { return answer != null; }
        public Language language() { return query.language(); }
    }

    private final QueryParser parser;
    private final QueryExecutor executor;
    private final Map<String, ParsedQuery> lastQuery = new ConcurrentHashMap<>();
    private final Map<String, String> lastData = new ConcurrentHashMap<>();

    public QueryUnderstandingService(QueryParser parser, QueryExecutor executor) {
        this.parser = parser;
        this.executor = executor;
    }

    public Understanding understand(String chatId, String message, ParsedStatement statement) {
        List<Transaction> txns = statement.transactions();
        if (txns.isEmpty()) {
            return new Understanding(parser.parse(message, LocalDate.now(), List.of(), null), null);
        }
        LocalDate from = txns.stream().map(Transaction::date).min(Comparator.naturalOrder()).orElseThrow();
        LocalDate to = txns.stream().map(Transaction::date).max(Comparator.naturalOrder()).orElseThrow();
        TreeSet<String> merchants = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (Transaction t : txns) if (!t.isUnknownMerchant()) merchants.add(t.merchant());

        ParsedQuery q = parser.parse(message, to, merchants, lastQuery.get(chatId));
        Answer answer = executor.execute(q, txns, from, to);
        if (q.intent() != Intent.UNKNOWN) lastQuery.put(chatId, q);
        if (answer != null) lastData.put(chatId, answer.data());
        return new Understanding(q, answer);
    }

    /** Data behind the previous answer, so follow-up questions the layer cannot parse still have context. */
    public String lastData(String chatId) {
        return lastData.get(chatId);
    }

    public void forget(String chatId) {
        lastQuery.remove(chatId);
        lastData.remove(chatId);
    }
}
