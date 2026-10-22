package com.whereismymoneygoing.backend.parser;

import java.util.HashMap;
import java.util.Map;

final class ColumnAliases {

    private static final Map<String, String> MAP = new HashMap<>();

    static {
        add("value_date", "value", "valuedate", "value date", "dated");
        add("post_date", "postdate", "post date", "txndate", "txn date", "transactiondate",
                "transaction date", "post", "transaction", "businessdate", "business date", "date");
        add("details", "details", "narration", "description", "particulars", "transactionremarks",
                "transaction remarks", "txndetails", "description of transaction", "remarks");
        add("ref", "ref", "refnochequeno", "refno", "ref no", "cheque", "chqrefno", "chqnoref",
                "chqref", "chq", "chqno", "chequeno", "chequenumber", "cheque number", "refnumber");
        add("debit", "debit", "withdrawal", "withdrawalamt", "withdrawal amt", "withdrawalamount",
                "withdrawal amount", "withdrawalamountinr", "withdrawal amountinr", "withdrawalamtinr",
                "debitamt", "debit amt", "debitamount", "debit amount", "dr");
        add("credit", "credit", "deposit", "depositamt", "deposit amt", "depositamount",
                "deposit amount", "depositamountinr", "deposit amountinr", "depositamtinr",
                "creditamt", "credit amt", "creditamount", "credit amount", "cr");
        add("balance", "balance", "balanceinr", "closingbalance", "closing balance",
                "runningbalance", "availablebalance");
    }

    private ColumnAliases(){}

    private static void add(String canonical, String... aliases){
        for(String alias: aliases) MAP.put(alias, canonical);
    }

    static String normalize(String header){
        StringBuilder sb = new StringBuilder();
        for(char ch: header.toCharArray()){
            if(Character.isLetterOrDigit(ch) || Character.isWhitespace(ch)) sb.append(ch);
        }
        return sb.toString().toLowerCase().strip();
    }

    static String canonical(String header) {
        String text = normalize(header);
        String hit = MAP.get(text);
        return hit != null ? hit : MAP.get(text.replace(" ", ""));
    }
}
