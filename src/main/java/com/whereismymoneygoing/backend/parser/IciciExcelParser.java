package com.whereismymoneygoing.backend.parser;

import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class IciciExcelParser extends AbstractGridParser {

    @Override
    public String bankName() { return "ICICI"; }

    @Override
    protected boolean isHeader(Set<String> columns) {
        return columns.contains("value_date") && columns.contains("post_date")
                && columns.contains("details")
                && (columns.contains("debit") || columns.contains("credit"));
    }
}
