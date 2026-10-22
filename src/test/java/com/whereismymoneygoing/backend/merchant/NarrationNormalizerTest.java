package com.whereismymoneygoing.backend.merchant;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class NarrationNormalizerTest {

    @Test
    void upperCasesAndTurnsSeparatorsIntoSingleSpaces() {
        assertEquals("UPI ZOMATO UPIPAYMENT ICICI BANK",
                NarrationNormalizer.normalize("UPI/zomato/UPIPayment/ICICI Bank"));
    }

    @Test
    void collapsesRunsOfWhitespace() { // fails until the SPACES regex fix
        assertEquals("UPI ZOMATO ORDER", NarrationNormalizer.normalize("UPI/ZOMATO   ORDER"));
    }
}