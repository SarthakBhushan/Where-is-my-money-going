package com.whereismymoneygoing.backend.merchant;

import java.util.List;

public record MerchantRule(
        String merchant,
        List<String> contains
) {
    public MerchantRule{
        contains = contains == null? List.of():List.copyOf(contains);
    }
}
