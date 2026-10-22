package com.whereismymoneygoing.backend.merchant;

import java.util.List;

public record RuleFile(List<MerchantRule> rules) {
    public RuleFile{
        rules = rules == null? List.of(): List.copyOf(rules);
    }
}
