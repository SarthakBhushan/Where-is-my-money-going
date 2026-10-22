package com.whereismymoneygoing.backend.enrichment;


import com.whereismymoneygoing.backend.classifier.TransactionClassifier;
import com.whereismymoneygoing.backend.merchant.MerchantResolver;
import com.whereismymoneygoing.backend.model.Transaction;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class TransactionEnricher {

    private final MerchantResolver resolver;
    private final TransactionClassifier classifier;

    public TransactionEnricher(MerchantResolver resolver, TransactionClassifier classifier){
        this.resolver = resolver;
        this.classifier = classifier;
    }

    public Transaction enrich(Transaction t){
        return t.enriched(resolver.resolve(t.narration(), t.type()), classifier.classify(t.narration()));
    }

    public List<Transaction>enrichAll(List<Transaction> transactions){
        return transactions.stream().map(this::enrich).toList();
    }
}
