package com.whereismymoneygoing.backend.parser;

import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class SbiExcelParser extends AbstractGridParser {

    @Override
    public String bankName() { return "SBI"; }

    @Override
    protected boolean isHeader(Set<String> columns) {
        return (columns.contains("post_date") || columns.contains("value_date"))
                && columns.contains("details");
    }
}