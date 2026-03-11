package com.proyecto.moveon.data.session;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;

@RunWith(AndroidJUnit4.class)
public class SecureSessionManagerInstrumentedTest {

    private Context context;
    private SecureSessionManager manager;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        manager = new SecureSessionManager(context);
        manager.logout();
    }

    @After
    public void tearDown() {
        manager.logout();
    }

    @Test
    public void isLoggedIn_requiresBothTokens() throws Exception {
        assertFalse(manager.isLoggedIn());

        manager.saveLogin("alice", fakeJwtWithSub("123"), "refresh_123");
        assertTrue(manager.isLoggedIn());

        manager.saveLogin("alice", fakeJwtWithSub("123"), "refresh_123");
        removeEncryptedValue("KEY_REFRESH_TOKEN_CT", "KEY_REFRESH_TOKEN_IV");
        assertFalse(manager.isLoggedIn());

        manager.saveLogin("alice", fakeJwtWithSub("123"), "refresh_123");
        removeEncryptedValue("KEY_ACCESS_TOKEN_CT", "KEY_ACCESS_TOKEN_IV");
        assertFalse(manager.isLoggedIn());
    }

    @Test
    public void saveLogin_derivesStableUserIdAndAccountKeyFromJwtSub() {
        manager.saveLogin("alice", fakeJwtWithSub("987"), "refresh_987");

        assertEquals("987", manager.getUserId());
        assertEquals("uid_987", manager.getAccountKey());
    }

    @Test
    public void getUserId_recoversFromStoredAccessTokenWhenUserIdWasNotCached() throws Exception {
        manager.saveLogin("alice", fakeJwtWithSub("456"), "refresh_456");
        removeEncryptedValue("KEY_USER_ID_CT", "KEY_USER_ID_IV");

        assertEquals("456", manager.getUserId());
        assertEquals("uid_456", manager.getAccountKey());
    }

    private void removeEncryptedValue(String cipherFieldName, String ivFieldName) throws Exception {
        String prefName = getPrivateStaticString("PREF_NAME");
        String cipherKey = getPrivateStaticString(cipherFieldName);
        String ivKey = getPrivateStaticString(ivFieldName);

        SharedPreferences prefs = context.getSharedPreferences(prefName, Context.MODE_PRIVATE);
        prefs.edit().remove(cipherKey).remove(ivKey).commit();
    }

    private String getPrivateStaticString(String fieldName) throws Exception {
        Field field = SecureSessionManager.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return (String) field.get(null);
    }

    private static String fakeJwtWithSub(String sub) {
        String header = base64Url("{\"alg\":\"none\",\"typ\":\"JWT\"}");
        String payload = base64Url("{\"sub\":\"" + sub + "\"}");
        return header + "." + payload + ".signature";
    }

    private static String base64Url(String raw) {
        return Base64.encodeToString(
                raw.getBytes(StandardCharsets.UTF_8),
                Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING
        );
    }
}