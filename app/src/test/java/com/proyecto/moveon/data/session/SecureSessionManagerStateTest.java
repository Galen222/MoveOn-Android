package com.proyecto.moveon.data.session;

import static org.junit.Assert.*;

import android.content.SharedPreferences;

import com.proyecto.moveon.core.settings.AppSettingsManager;
import com.proyecto.moveon.domain.auth.SocialAuthProvider;
import com.proyecto.moveon.testutil.MemoryContext;
import com.proyecto.moveon.testutil.MemorySharedPreferences;

import org.junit.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Set;

/**
 * Tests de estado interno puro de {@link SecureSessionManager} sin usar Android Keystore.
 */
public class SecureSessionManagerStateTest {

    /**
     * Verifica que SessionSnapshot expone identidad, tokens, provider y clave de cuenta derivada.
     */
    @Test
    public void sessionSnapshot_exposesValuesAndDerivedAccountKey() throws Exception {
        SecureSessionManager.SessionSnapshot snapshot = snapshot(
                "ana",
                "access",
                "refresh",
                "42",
                SocialAuthProvider.GOOGLE
        );

        assertEquals("ana", snapshot.getUsername());
        assertEquals("access", snapshot.getAccessToken());
        assertEquals("refresh", snapshot.getRefreshToken());
        assertEquals("42", snapshot.getUserId());
        assertEquals(SocialAuthProvider.GOOGLE, snapshot.getAuthProvider());
        assertTrue(snapshot.hasCompleteSession());
        assertTrue(snapshot.hasRecoverableSession());
        assertTrue(snapshot.hasRefreshToken());
        assertEquals("uid_42", snapshot.getAccountKey());
    }

    /**
     * Verifica que SessionSnapshot distingue sesiones parciales, recuperables y vacías.
     */
    @Test
    public void sessionSnapshot_distinguishesRecoverableAndIncompleteSessions() throws Exception {
        SecureSessionManager.SessionSnapshot accessOnly = snapshot("ana", "access", null, null, null);
        SecureSessionManager.SessionSnapshot refreshOnly = snapshot("ana", null, "refresh", null, null);
        SecureSessionManager.SessionSnapshot empty = snapshot(null, " ", "", " ", null);

        assertFalse(accessOnly.hasCompleteSession());
        assertTrue(accessOnly.hasRecoverableSession());
        assertFalse(accessOnly.hasRefreshToken());
        assertNull(accessOnly.getAccountKey());

        assertFalse(refreshOnly.hasCompleteSession());
        assertTrue(refreshOnly.hasRecoverableSession());
        assertTrue(refreshOnly.hasRefreshToken());

        assertFalse(empty.hasCompleteSession());
        assertFalse(empty.hasRecoverableSession());
        assertFalse(empty.hasRefreshToken());
        assertNull(empty.getAccountKey());
    }

    /**
     * Verifica que el contenedor EncryptedValue conserva ciphertext e IV sin transformarlos.
     */
    @Test
    public void encryptedValue_storesCipherTextAndIv() throws Exception {
        Class<?> encryptedClass = Class.forName("com.proyecto.moveon.data.session.SecureSessionManager$EncryptedValue");
        Constructor<?> constructor = encryptedClass.getDeclaredConstructor(String.class, String.class);
        constructor.setAccessible(true);
        Object encrypted = constructor.newInstance("cipher", "iv");

        assertEquals("cipher", field(encryptedClass, "cipherTextBase64").get(encrypted));
        assertEquals("iv", field(encryptedClass, "ivBase64").get(encrypted));
    }

    /**
     * Verifica que persistEditor usa apply en modo asíncrono y commit en modo síncrono.
     */
    @Test
    public void persistEditor_persistsBothAsyncAndSyncModes() throws Exception {
        SecureSessionManager manager = allocate(SecureSessionManager.class);
        MemorySharedPreferences prefs = new MemorySharedPreferences();
        Method persistEditor = method("persistEditor", SharedPreferences.Editor.class, boolean.class, String.class);

        persistEditor.invoke(manager, prefs.edit().putString("async", "ok"), false, "fallo async");
        persistEditor.invoke(manager, prefs.edit().putString("sync", "ok"), true, "fallo sync");

        assertEquals("ok", prefs.getString("async", null));
        assertEquals("ok", prefs.getString("sync", null));
    }

    /**
     * Verifica que persistEditor propaga un fallo cuando el commit síncrono devuelve falso.
     */
    @Test
    public void persistEditor_throwsWhenSynchronousCommitFails() throws Exception {
        SecureSessionManager manager = allocate(SecureSessionManager.class);
        Method persistEditor = method("persistEditor", SharedPreferences.Editor.class, boolean.class, String.class);

        try {
            persistEditor.invoke(manager, new FailingCommitEditor(), true, "commit fallido");
            fail("El commit fallido debe elevar IllegalStateException");
        } catch (InvocationTargetException expected) {
            assertTrue(expected.getCause() instanceof IllegalStateException);
            assertEquals("commit fallido", expected.getCause().getMessage());
        }
    }

    /**
     * Verifica que putEncryptedOrRemove elimina ambas claves cuando el texto plano no tiene contenido útil.
     */
    @Test
    public void putEncryptedOrRemove_removesCipherAndIvKeysForBlankPlainText() throws Exception {
        SecureSessionManager manager = allocate(SecureSessionManager.class);
        MemorySharedPreferences prefs = new MemorySharedPreferences();
        SharedPreferences.Editor editor = prefs.edit()
                .putString("ct", "cipher")
                .putString("iv", "vector");
        editor.commit();

        editor = prefs.edit();
        method("putEncryptedOrRemove", SharedPreferences.Editor.class, String.class, String.class, String.class)
                .invoke(manager, editor, "ct", "iv", "   ");
        editor.commit();

        assertFalse(prefs.contains("ct"));
        assertFalse(prefs.contains("iv"));
    }

    /**
     * Verifica que getDecryptedValueLocked devuelve null si falta ciphertext o IV sin intentar descifrar.
     */
    @Test
    public void getDecryptedValueLocked_returnsNullWhenCipherMaterialIsIncomplete() throws Exception {
        SecureSessionManager manager = allocate(SecureSessionManager.class);
        MemorySharedPreferences prefs = new MemorySharedPreferences();
        setField(manager, "prefs", prefs);

        assertNull(method("getDecryptedValueLocked", String.class, String.class).invoke(manager, "ct", "iv"));

        prefs.edit().putString("ct", "cipher").commit();

        assertNull(method("getDecryptedValueLocked", String.class, String.class).invoke(manager, "ct", "iv"));
    }

    /**
     * Verifica que persistAuthProvider recorta el provider y lo elimina cuando llega sin texto.
     */
    @Test
    public void persistAuthProvider_trimsAndRemovesProviderInAppSettingsPrefs() throws Exception {
        SecureSessionManager manager = allocate(SecureSessionManager.class);
        MemoryContext context = new MemoryContext();
        setField(manager, "appContext", context);

        method("persistAuthProvider", String.class, boolean.class).invoke(manager, " google ", false);

        assertEquals("google", context.preferences(AppSettingsManager.PREFS).getString("auth_provider", null));

        method("persistAuthProvider", String.class, boolean.class).invoke(manager, " ", true);

        assertNull(context.preferences(AppSettingsManager.PREFS).getString("auth_provider", null));
    }

    /**
     * Verifica que clearSocialAuthStateLocked borra el provider y fuerza silent sign-in a falso.
     */
    @Test
    public void clearSocialAuthStateLocked_removesProviderAndDisablesGoogleSilentSignIn() throws Exception {
        SecureSessionManager manager = allocate(SecureSessionManager.class);
        MemoryContext context = new MemoryContext();
        context.preferences(AppSettingsManager.PREFS).edit()
                .putString("auth_provider", "google")
                .putBoolean("google_silent_enabled", true)
                .commit();
        setField(manager, "appContext", context);

        method("clearSocialAuthStateLocked").invoke(manager);

        assertNull(context.preferences(AppSettingsManager.PREFS).getString("auth_provider", null));
        assertFalse(context.preferences(AppSettingsManager.PREFS).getBoolean("google_silent_enabled", true));
    }

    private static SecureSessionManager.SessionSnapshot snapshot(String username,
                                                                 String accessToken,
                                                                 String refreshToken,
                                                                 String userId,
                                                                 String authProvider) throws Exception {
        Constructor<SecureSessionManager.SessionSnapshot> constructor =
                SecureSessionManager.SessionSnapshot.class.getDeclaredConstructor(
                        String.class,
                        String.class,
                        String.class,
                        String.class,
                        String.class
                );
        constructor.setAccessible(true);
        return constructor.newInstance(username, accessToken, refreshToken, userId, authProvider);
    }

    private static Method method(String name, Class<?>... parameterTypes) throws Exception {
        Method method = SecureSessionManager.class.getDeclaredMethod(name, parameterTypes);
        method.setAccessible(true);
        return method;
    }

    private static Field field(Class<?> type, String name) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = SecureSessionManager.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    @SuppressWarnings("unchecked")
    private static <T> T allocate(Class<T> type) throws Exception {
        Field field = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");
        field.setAccessible(true);
        Object unsafe = field.get(null);
        Method method = unsafe.getClass().getMethod("allocateInstance", Class.class);
        return (T) method.invoke(unsafe, type);
    }

    private static final class FailingCommitEditor implements SharedPreferences.Editor {
        @Override public SharedPreferences.Editor putString(String key, String value) { return this; }
        @Override public SharedPreferences.Editor putStringSet(String key, Set<String> values) { return this; }
        @Override public SharedPreferences.Editor putInt(String key, int value) { return this; }
        @Override public SharedPreferences.Editor putLong(String key, long value) { return this; }
        @Override public SharedPreferences.Editor putFloat(String key, float value) { return this; }
        @Override public SharedPreferences.Editor putBoolean(String key, boolean value) { return this; }
        @Override public SharedPreferences.Editor remove(String key) { return this; }
        @Override public SharedPreferences.Editor clear() { return this; }
        @Override public boolean commit() { return false; }
        @Override public void apply() {}
    }
}
