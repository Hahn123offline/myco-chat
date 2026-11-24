package com.dsh.egress;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AuditEntryMaskTest {

    @Test public void maskerSpans计入审计() {
        AuditEntry e = new AuditEntry(1L, "t", "p", true,
                EgressTier.WHITE, EgressTier.WHITE, true, null,
                0, 0, "REDACT_THEN_CLOUD", 100, "abc", "allowed", 5, "email,");
        assertEquals(5, e.maskedSpans);
        assertEquals(5, e.maskerSpans);
        assertTrue(e.toJsonLine().contains("\"maskedSpans\":5"));
        assertTrue(e.toJsonLine().contains("\"maskerKinds\":\"email,\""));
    }
}
