package com.whereismymoneygoing.backend.parser;

import com.whereismymoneygoing.backend.model.Transaction;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

abstract class AbstractGridParser implements ExcelStatementParser {

    /** Does this set of canonical column names look like this bank's transaction header? */
    protected abstract boolean isHeader(Set<String> canonicalColumns);

    protected String canonical(String header) {
        return ColumnAliases.canonical(header);
    }

    @Override
    public List<Transaction> parse(List<List<String>> grid) {
        int headerRow = -1;
        Map<Integer, String> columns = Map.of();

        for (int r = 0; r < grid.size(); r++) {
            Map<Integer, String> candidate = mapHeader(grid.get(r));
            if (isHeader(new HashSet<>(candidate.values()))) {
                headerRow = r;
                columns = candidate;
                break;
            }
        }
        if (headerRow < 0) {
            throw new StatementNotRecognisedException(
                    "We could not find the transaction table in this " + bankName() + " statement.");
        }

        List<Transaction> transactions = new ArrayList<>();
        for (int r = headerRow + 1; r < grid.size(); r++) {
            List<String> cells = grid.get(r);
            Map<String, String> row = new HashMap<>();
            for (Map.Entry<Integer, String> column : columns.entrySet()) {
                String value = column.getKey() < cells.size()
                        ? StatementRowMapper.clean(cells.get(column.getKey())) : "";
                row.merge(column.getValue(), value, (a, b) -> a.isEmpty() ? b : a);
            }
            Transaction t = StatementRowMapper.toTransaction(row);
            if (t != null) transactions.add(t);
        }

        if (transactions.isEmpty()) {
            throw new StatementParseException(
                    "The statement was recognised, but no valid transactions were found.");
        }
        return transactions;
    }

    private Map<Integer, String> mapHeader(List<String> cells) {
        Map<Integer, String> map = new LinkedHashMap<>();
        for (int i = 0; i < cells.size(); i++) {
            String canonical = canonical(StatementRowMapper.clean(cells.get(i)));
            if (canonical != null) map.put(i, canonical);
        }
        return map;
    }
}