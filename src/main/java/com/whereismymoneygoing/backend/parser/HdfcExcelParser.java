package com.whereismymoneygoing.backend.parser;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

@Component
public class HdfcExcelParser extends AbstractGridParser{

    private static final Map<String, String> EXACT = Map.of(
            "Date", "post_date",
            "Narration", "details",
            "Chq./Ref.No.", "ref",
            "Withdrawal Amt.", "debit",
            "Deposit Amt.", "credit",
            "Closing Balance", "balance"
    );

    @Override
    public String bankName(){return "HDFC";}

    @Override
    protected String canonical(String header){return EXACT.get(header);}

    @Override
    protected boolean isHeader(Set<String> columns){
        return columns.containsAll(Set.of("post_date", "details", "ref", "debit", "credit"));
    }
}
