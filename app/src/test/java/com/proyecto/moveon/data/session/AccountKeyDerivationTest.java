package com.proyecto.moveon.data.session;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class AccountKeyDerivationTest {

    @Test
    public void buildAccountKeyFromUserId_prefixesUidAndTrims() {
        assertEquals("uid_123", SecureSessionManager.buildAccountKeyFromUserId("123"));
        assertEquals("uid_456", SecureSessionManager.buildAccountKeyFromUserId(" 456 "));
    }

    @Test
    public void buildAccountKeyFromUserId_returnsNullWhenBlank() {
        assertNull(SecureSessionManager.buildAccountKeyFromUserId(null));
        assertNull(SecureSessionManager.buildAccountKeyFromUserId(""));
        assertNull(SecureSessionManager.buildAccountKeyFromUserId("   "));
    }
}
