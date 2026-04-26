package com.proyecto.moveon.data.session;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertNull;

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

/**
 * Tests instrumentados de {@link SecureSessionManager}.
 *
 * <p>Se validan especialmente los caminos críticos de autenticación:
 * login, rotación de tokens y recuperación de metadatos derivados.</p>
 */
@RunWith(AndroidJUnit4.class)
public class SecureSessionManagerInstrumentedTest {

    private Context context;
    private SecureSessionManager manager;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        manager = SecureSessionManager.getInstance(context);
        manager.logout();
    }

    @After
    public void tearDown() {
        manager.logout();
    }

    /**
     * Verifica el escenario cubierto por {@link #isLoggedIn_requiresBothTokens()}.
     */
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

    /**
     * Verifica el escenario cubierto por {@link #saveLogin_derivesStableUserIdAndAccountKeyFromJwtSub()}.
     */
    @Test
    public void saveLogin_derivesStableUserIdAndAccountKeyFromJwtSub() {
        manager.saveLogin("alice", fakeJwtWithSub("987"), "refresh_987");

        assertEquals("987", manager.getUserId());
        assertEquals("uid_987", manager.getAccountKey());
    }

    /**
     * Verifica el escenario cubierto por {@link #updateTokens_preservesUsernameAndRefreshesDerivedIdentity()}.
     */
    @Test
    public void updateTokens_preservesUsernameAndRefreshesDerivedIdentity() {
        manager.saveLogin("alice", fakeJwtWithSub("111"), "refresh_old");

        manager.updateTokens(fakeJwtWithSub("222"), "refresh_new");

        assertTrue(manager.isLoggedIn());
        assertEquals("alice", manager.getUsername());
        assertEquals("222", manager.getUserId());
        assertEquals("uid_222", manager.getAccountKey());
        assertEquals("refresh_new", manager.getRefreshToken());
    }

    /**
     * Verifica el escenario cubierto por {@link #saveLoginSync_publishesTokensImmediatelyForConcurrentReaders()}.
     */
    @Test
    public void saveLoginSync_publishesTokensImmediatelyForConcurrentReaders() {
        manager.saveLoginSync("alice", fakeJwtWithSub("321"), "refresh_rotated");

        assertTrue(manager.isLoggedIn());
        assertEquals("alice", manager.getUsername());
        assertEquals("321", manager.getUserId());
        assertEquals("refresh_rotated", manager.getRefreshToken());
    }

    /**
     * Verifica el escenario cubierto por {@link #updateTokensSync_preservesUsernameAndRotatesRefreshTokenImmediately()}.
     */
    @Test
    public void updateTokensSync_preservesUsernameAndRotatesRefreshTokenImmediately() {
        manager.saveLogin("alice", fakeJwtWithSub("111"), "refresh_old");

        manager.updateTokensSync(fakeJwtWithSub("222"), "refresh_new");

        assertTrue(manager.isLoggedIn());
        assertEquals("alice", manager.getUsername());
        assertEquals("222", manager.getUserId());
        assertEquals("uid_222", manager.getAccountKey());
        assertEquals("refresh_new", manager.getRefreshToken());
    }

    /**
     * Verifica el escenario cubierto por {@link #getUserId_recoversFromStoredAccessTokenWhenUserIdWasNotCached()}.
     */
    @Test
    public void getUserId_recoversFromStoredAccessTokenWhenUserIdWasNotCached() throws Exception {
        manager.saveLogin("alice", fakeJwtWithSub("456"), "refresh_456");
        removeEncryptedValue("KEY_USER_ID_CT", "KEY_USER_ID_IV");

        assertEquals("456", manager.getUserId());
        assertEquals("uid_456", manager.getAccountKey());
    }

    /**
     * Verifica el escenario cubierto por {@link #saveLoginWithProvider_persistsGoogleProvider()}.
     */
    @Test
    public void saveLoginWithProvider_persistsGoogleProvider() {
        manager.saveLoginWithProvider(
                "alice",
                fakeJwtWithSub("456"),
                "refresh_456",
                "google"
        );

        assertEquals("google", manager.getAuthProvider());
        assertTrue(manager.isLoggedWithGoogle());
    }

    /**
     * Verifica el escenario cubierto por {@link #logout_clearsProviderAndDisablesSilentGoogleSignIn()}.
     */
    @Test
    public void logout_clearsProviderAndDisablesSilentGoogleSignIn() {
        manager.saveLoginWithProvider(
                "alice",
                fakeJwtWithSub("456"),
                "refresh_456",
                "google"
        );
        context.getSharedPreferences("social_auth_prefs", Context.MODE_PRIVATE)
                .edit()
                .putBoolean("google_silent_enabled", true)
                .commit();

        manager.logout();

        assertNull(manager.getAuthProvider());
        assertFalse(manager.isLoggedWithGoogle());
        assertFalse(context.getSharedPreferences("social_auth_prefs", Context.MODE_PRIVATE)
                .getBoolean("google_silent_enabled", true));
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

    /**
     * Genera un JWT sintético con {@code sub} y {@code exp}, que son los campos
     * mínimos exigidos por {@link SecureSessionManager} para considerar válido
     * el payload de sesión.
     */
    private static String fakeJwtWithSub(String sub) {
        long expEpochSeconds = (System.currentTimeMillis() / 1000L) + 3600L;
        String header = base64Url("{\"alg\":\"none\",\"typ\":\"JWT\"}");
        String payload = base64Url("{\"sub\":\"" + sub + "\",\"exp\":" + expEpochSeconds + "}");
        return header + "." + payload + ".signature";
    }

    private static String base64Url(String raw) {
        return Base64.encodeToString(
                raw.getBytes(StandardCharsets.UTF_8),
                Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING
        );
    }
}
