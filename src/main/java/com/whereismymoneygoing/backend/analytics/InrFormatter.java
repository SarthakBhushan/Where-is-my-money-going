package com.whereismymoneygoing.backend.analytics;

import java.math.BigDecimal;
import java.util.Locale;
import org.springframework.stereotype.Component;

/** Available in templates as ${@fmt.inr(amount)}. */
@Component("fmt")
public class InrFormatter {

    public String inr(BigDecimal amount) {
        if (amount == null) return "-";
        double v = amount.doubleValue();
        if (Math.abs(v) >= 1_00_00_000) return String.format(Locale.US, "₹%.2f Cr", v / 1_00_00_000);
        if (Math.abs(v) >= 1_00_000) return String.format(Locale.US, "₹%.2f L", v / 1_00_000);
        return String.format(Locale.US, "₹%,.0f", v);
    }
}