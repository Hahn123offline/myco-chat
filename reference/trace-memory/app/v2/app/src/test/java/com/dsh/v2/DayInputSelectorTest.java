package com.dsh.v2;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class DayInputSelectorTest {

    @Test public void excluded_local链不得进入日叙事输入() {
        assertTrue(DayInputSelector.eligible(0, "done"));
        assertFalse(DayInputSelector.eligible(0, "excluded_local"));
        assertFalse(DayInputSelector.eligible(1, "done"));
    }
}
