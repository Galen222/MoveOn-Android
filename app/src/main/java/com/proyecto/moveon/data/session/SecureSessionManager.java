package com.proyecto.moveon.data.session;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.proyecto.moveon.core.settings.AppSettingsManager;
import com.proyecto.moveon.domain.auth.SocialAuthProvider;
import com.proyecto.moveon.utils.StringUtils;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Gestor centralizado de la sesión autenticada del usuario.
 *
 * <p>Responsabilidades principales:</p>
 * <ul>
 *     <li>Persistir usuario, access token y refresh token cifrados en {@link SharedPreferences}.</li>
 *     <li>Derivar metadatos de sesión a partir del JWT, como el {@code userId}.</li>
 *     <li>Exponer lecturas sincronizadas para que red, UI y repositorios vean un estado coherente.</li>
 * </ul>
 *
 * <p>Importante para la rotación estricta de refresh tokens:
 * cuando un refresh devuelve un nuevo par de tokens, el guardado debe poder hacerse
 * de forma síncrona. Así evitamos que otro hilo lea el refresh antiguo justo después
 * de un refresh exitoso y provoque una reutilización detectada por el backend.</p>
 *
 * <p>La rotación de la clave del Keystore se controla con una versión explícita en el alias.
 * Cuando cambie, hay que migrar o limpiar los datos cifrados persistidos con la versión anterior.</p>
 */
public final class SecureSessionManager {

    private static final String PREF_NAME = "user_prefs_secure";
    private static final String KEYSTORE_ALIAS_BASE = "moveon_session_key";
    private static final int KEYSTORE_ALIAS_VERSION = 1;
    private static final String KEYSTORE_ALIAS =
            KEYSTORE_ALIAS_BASE + "_v" + KEYSTORE_ALIAS_VERSION;
    private static final String ANDROID_KEYSTORE = "AndroidKeyStore";

    private static final String KEY_ACCESS_TOKEN_CT = "access_token_ct";
    private static final String KEY_ACCESS_TOKEN_IV = "access_token_iv";
    private static final String KEY_REFRESH_TOKEN_CT = "refresh_token_ct";
    private static final String KEY_REFRESH_TOKEN_IV = "refresh_token_iv";
    private static final String KEY_USERNAME_CT = "username_ct";
    private static final String KEY_USERNAME_IV = "username_iv";
    private static final String KEY_USER_ID_CT = "user_id_ct";
    private static final String KEY_USER_ID_IV = "user_id_iv";
    private static final String KEY_REMEMBERED_ID_CT = "remembered_id_ct";
    private static final String KEY_REMEMBERED_ID_IV = "remembered_id_iv";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH_BITS = 128;

    // Seguro: el singleton solo retiene appContext (Application), no una Activity.
    // Application vive todo el proceso, así que no hay leak real.
    @SuppressWarnings("StaticFieldLeak")
    private static volatile SecureSessionManager instance;

    @NonNull
    public static SecureSessionManager getInstance(@NonNull Context context) {
        if (instance == null) {
            synchronized (SecureSessionManager.class) {
                if (instance == null) {
                    instance = new SecureSessionManager(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    /**
     * Foto inmutable del estado actual de sesión.
     *
     * <p>Se usa para leer acceso/refresh/identidad como un bloque coherente
     * bajo el mismo lock, evitando combinaciones inconsistentes entre llamadas.</p>
     */
    public static final class SessionSnapshot {
        @Nullable private final String username;
        @Nullable private final String accessToken;
        @Nullable private final String refreshToken;
        @Nullable private final String userId;
        @Nullable private final String authProvider;

        private SessionSnapshot(@Nullable String username,
                                @Nullable String accessToken,
                                @Nullable String refreshToken,
                                @Nullable String userId,
                                @Nullable String authProvider) {
            this.username = username;
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
            this.userId = userId;
            this.authProvider = authProvider;
        }

        @Nullable public String getUsername() { return username; }
        @Nullable public String getAccessToken() { return accessToken; }
        @Nullable public String getRefreshToken() { return refreshToken; }
        @Nullable public String getUserId() { return userId; }
        @Nullable public String getAuthProvider() { return authProvider; }

        public boolean hasCompleteSession() {
            return StringUtils.hasText(accessToken) && StringUtils.hasText(refreshToken);
        }

        public boolean hasRecoverableSession() {
            return StringUtils.hasText(accessToken) || StringUtils.hasText(refreshToken);
        }

        public boolean hasRefreshToken() {
            return StringUtils.hasText(refreshToken);
        }

        @Nullable
        public String getAccountKey() {
            return buildAccountKeyFromUserId(userId);
        }
    }

    private final Context appContext;
    private final SharedPreferences prefs;
    private final Object sessionLock = new Object();

    private SecureSessionManager(Context context) {
        this.appContext = context.getApplicationContext();
        this.prefs = appContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    /**
     * Guarda una sesión completa usando persistencia asíncrona.
     *
     * <p>Es apropiado para login interactivo y escrituras no críticas, donde la UI
     * no depende de que el fichero se haya fsync-eado inmediatamente.</p>
     */
    public void saveLogin(@Nullable String username,
                          @Nullable String accessToken,
                          @Nullable String refreshToken) {
        synchronized (sessionLock) {
            saveLoginLocked(username, accessToken, refreshToken, false, null, false);
        }
    }

    /**
     * Guarda una sesión completa forzando persistencia síncrona.
     *
     * <p>Debe usarse tras un refresh exitoso para publicar el nuevo refresh token
     * antes de devolver el control al resto de hilos. Así reducimos al mínimo la
     * ventana en la que otro flujo podría intentar reutilizar el refresh anterior.</p>
     */
    public void saveLoginSync(@Nullable String username,
                              @Nullable String accessToken,
                              @Nullable String refreshToken) {
        synchronized (sessionLock) {
            saveLoginLocked(username, accessToken, refreshToken, true, null, false);
        }
    }

    /**
     * Guarda una sesión completa y actualiza explícitamente el provider de autenticación.
     *
     * <p>Usar {@code authProvider = null} para un login tradicional por email/usuario y
     * {@link SocialAuthProvider#GOOGLE} para un login social con Google.</p>
     */
    public void saveLoginWithProvider(@Nullable String username,
                                      @Nullable String accessToken,
                                      @Nullable String refreshToken,
                                      @Nullable String authProvider) {
        synchronized (sessionLock) {
            saveLoginLocked(username, accessToken, refreshToken, false, authProvider, true);
        }
    }

    /**
     * Variante síncrona de {@link #saveLoginWithProvider(String, String, String, String)}.
     */
    public void saveLoginSyncWithProvider(@Nullable String username,
                                          @Nullable String accessToken,
                                          @Nullable String refreshToken,
                                          @Nullable String authProvider) {
        synchronized (sessionLock) {
            saveLoginLocked(username, accessToken, refreshToken, true, authProvider, true);
        }
    }

    /**
     * Actualiza access/refresh preservando el usuario actual.
     *
     * <p>Persistencia asíncrona. Para el camino de refresh de red, preferir
     * {@link #updateTokensSync(String, String)}.</p>
     */
    public void updateTokens(@Nullable String accessToken, @Nullable String refreshToken) {
        synchronized (sessionLock) {
            String username = getDecryptedValueLocked(KEY_USERNAME_CT, KEY_USERNAME_IV);
            saveLoginLocked(username, accessToken, refreshToken, false, null, false);
        }
    }

    /**
     * Variante síncrona de {@link #updateTokens(String, String)}.
     *
     * <p>Útil cuando un flujo de autenticación necesita que los nuevos tokens queden
     * visibles de inmediato para peticiones concurrentes.</p>
     */
    public void updateTokensSync(@Nullable String accessToken, @Nullable String refreshToken) {
        synchronized (sessionLock) {
            String username = getDecryptedValueLocked(KEY_USERNAME_CT, KEY_USERNAME_IV);
            saveLoginLocked(username, accessToken, refreshToken, true, null, false);
        }
    }

    public boolean isLoggedIn() {
        synchronized (sessionLock) {
            return readSessionSnapshotLocked().hasCompleteSession();
        }
    }

    public boolean hasRecoverableSession() {
        synchronized (sessionLock) {
            return readSessionSnapshotLocked().hasRecoverableSession();
        }
    }

    public boolean hasRefreshToken() {
        synchronized (sessionLock) {
            return readSessionSnapshotLocked().hasRefreshToken();
        }
    }

    public boolean isAccessTokenExpiringWithinSeconds(long leewaySeconds) {
        synchronized (sessionLock) {
            String accessToken = readSessionSnapshotLocked().getAccessToken();
            if (!StringUtils.hasText(accessToken)) return true;

            Long expEpochSeconds = extractExpFromAccessToken(accessToken);
            if (expEpochSeconds == null) return true;

            long nowSeconds = System.currentTimeMillis() / 1000L;
            return expEpochSeconds <= (nowSeconds + Math.max(0L, leewaySeconds));
        }
    }

    @Nullable
    public String getAccessToken() {
        synchronized (sessionLock) {
            return readSessionSnapshotLocked().getAccessToken();
        }
    }

    @Nullable
    public String getRefreshToken() {
        synchronized (sessionLock) {
            return readSessionSnapshotLocked().getRefreshToken();
        }
    }

    @Nullable
    public String getUsername() {
        synchronized (sessionLock) {
            return readSessionSnapshotLocked().getUsername();
        }
    }

    @Nullable
    public String getUserId() {
        synchronized (sessionLock) {
            return readSessionSnapshotLocked().getUserId();
        }
    }

    @Nullable
    public String getAccountKey() {
        synchronized (sessionLock) {
            return readSessionSnapshotLocked().getAccountKey();
        }
    }

    /**
     * Devuelve el provider con el que se autenticó la sesión actual o la última sesión recuperable.
     */
    @Nullable
    public String getAuthProvider() {
        return AppSettingsManager.getAuthProvider(appContext);
    }

    /**
     * Indica si la sesión actual o recuperable pertenece a un acceso con Google.
     */
    public boolean isLoggedWithGoogle() {
        return SocialAuthProvider.GOOGLE.equals(AppSettingsManager.getAuthProvider(appContext));
    }

    @NonNull
    public SessionSnapshot getSessionSnapshot() {
        synchronized (sessionLock) {
            return readSessionSnapshotLocked();
        }
    }

    @Nullable
    public static String buildAccountKeyFromUserId(@Nullable String userId) {
        if (!StringUtils.hasText(userId)) return null;
        return "uid_" + userId.trim();
    }

    /**
     * Cierra la sesión local y limpia también las pistas usadas para el acceso social automático.
     *
     * <p>Además de borrar usuario/tokens/provider, desactiva el silent sign-in de Google
     * para que el siguiente arranque no vuelva a intentar un reingreso automático por inercia.</p>
     */
    public void logout() {
        synchronized (sessionLock) {
            // En logout sí queremos garantía dura: una vez retornamos no deben quedar
            // restos de sesión pendientes de escribirse en disco.
            boolean committed = prefs.edit()
                    .remove(KEY_USERNAME_CT).remove(KEY_USERNAME_IV)
                    .remove(KEY_ACCESS_TOKEN_CT).remove(KEY_ACCESS_TOKEN_IV)
                    .remove(KEY_REFRESH_TOKEN_CT).remove(KEY_REFRESH_TOKEN_IV)
                    .remove(KEY_USER_ID_CT).remove(KEY_USER_ID_IV)
                    .commit();
            if (!committed) {
                throw new IllegalStateException("Error limpiando sesión segura");
            }

            clearSocialAuthStateLocked();
        }
    }

    public void clearAccessTokenOnly() {
        synchronized (sessionLock) {
            // Igual que en logout: el caller espera que el access desaparezca en el acto.
            prefs.edit()
                    .remove(KEY_ACCESS_TOKEN_CT).remove(KEY_ACCESS_TOKEN_IV)
                    .commit();
        }
    }

    public void saveRememberedIdentifier(@Nullable String identifier) {
        synchronized (sessionLock) {
            try {
                SharedPreferences.Editor editor = prefs.edit();

                String safeIdentifier = StringUtils.textOf(identifier);
                if (safeIdentifier.isEmpty()) {
                    editor.remove(KEY_REMEMBERED_ID_CT).remove(KEY_REMEMBERED_ID_IV);
                } else {
                    putEncrypted(editor, KEY_REMEMBERED_ID_CT, KEY_REMEMBERED_ID_IV, safeIdentifier);
                }

                editor.apply();
            } catch (Exception e) {
                throw new RuntimeException("Error guardando identificador recordado", e);
            }
        }
    }

    @Nullable
    public String getRememberedIdentifier() {
        synchronized (sessionLock) {
            return getDecryptedValueLocked(KEY_REMEMBERED_ID_CT, KEY_REMEMBERED_ID_IV);
        }
    }

    public boolean shouldShowAutoPauseAlertsByDefault() {
        return AppSettingsManager.shouldShowAutoPauseAlertsByDefault(appContext);
    }

    public void setShowAutoPauseAlertsByDefault(boolean show) {
        AppSettingsManager.setShowAutoPauseAlertsByDefault(appContext, show);
    }

    /**
     * Guarda usuario, access token, refresh token y metadatos derivados.
     *
     * @param persistSynchronously {@code true} para usar {@link SharedPreferences.Editor#commit()}
     *                             y bloquear hasta que la escritura termine.
     */
    private void saveLoginLocked(@Nullable String username,
                                 @Nullable String accessToken,
                                 @Nullable String refreshToken,
                                 boolean persistSynchronously,
                                 @Nullable String authProvider,
                                 boolean replaceAuthProvider) {
        try {
            SharedPreferences.Editor editor = prefs.edit();
            putEncrypted(editor, KEY_USERNAME_CT, KEY_USERNAME_IV, StringUtils.textOf(username));
            putEncrypted(editor, KEY_ACCESS_TOKEN_CT, KEY_ACCESS_TOKEN_IV, StringUtils.textOf(accessToken));
            putEncrypted(editor, KEY_REFRESH_TOKEN_CT, KEY_REFRESH_TOKEN_IV, StringUtils.textOf(refreshToken));

            String userId = extractUserIdFromAccessToken(accessToken);
            if (StringUtils.hasText(userId)) {
                putEncrypted(editor, KEY_USER_ID_CT, KEY_USER_ID_IV, userId);
            } else {
                editor.remove(KEY_USER_ID_CT).remove(KEY_USER_ID_IV);
            }

            persistEditor(editor, persistSynchronously, "Error persistiendo sesión segura");

            if (replaceAuthProvider) {
                // En login tradicional limpiamos el provider previo; en login social guardamos Google.
                persistAuthProvider(authProvider, persistSynchronously);
            }
        } catch (Exception e) {
            throw new RuntimeException("Error guardando sesión segura", e);
        }
    }

    @NonNull
    private SessionSnapshot readSessionSnapshotLocked() {
        String username = getDecryptedValueLocked(KEY_USERNAME_CT, KEY_USERNAME_IV);
        String accessToken = getDecryptedValueLocked(KEY_ACCESS_TOKEN_CT, KEY_ACCESS_TOKEN_IV);
        String refreshToken = getDecryptedValueLocked(KEY_REFRESH_TOKEN_CT, KEY_REFRESH_TOKEN_IV);

        String userId = getDecryptedValueLocked(KEY_USER_ID_CT, KEY_USER_ID_IV);
        String authProvider = AppSettingsManager.getAuthProvider(appContext);
        if (!StringUtils.hasText(userId)) {
            userId = extractUserIdFromAccessToken(accessToken);
            if (StringUtils.hasText(userId)) {
                persistUserIdQuietlyLocked(userId);
            }
        }

        return new SessionSnapshot(username, accessToken, refreshToken, userId, authProvider);
    }

    private void persistUserIdQuietlyLocked(@NonNull String userId) {
        try {
            SharedPreferences.Editor editor = prefs.edit();
            putEncrypted(editor, KEY_USER_ID_CT, KEY_USER_ID_IV, userId);
            editor.apply();
        } catch (Exception ignored) {
            // No interrumpimos la lectura de sesión por un fallo no crítico de caché.
        }
    }

    @Nullable
    private String extractUserIdFromAccessToken(@Nullable String accessToken) {
        JSONObject payload = decodeJwtPayload(accessToken);
        if (payload == null) return null;

        String sub = payload.optString("sub", null);
        return StringUtils.hasText(sub) ? sub.trim() : null;
    }

    @Nullable
    private Long extractExpFromAccessToken(@Nullable String accessToken) {
        JSONObject payload = decodeJwtPayload(accessToken);
        if (payload == null || !payload.has("exp")) return null;

        long exp = payload.optLong("exp", -1L);
        return exp > 0L ? exp : null;
    }

    @Nullable
    private JSONObject decodeJwtPayload(@Nullable String token) {
        if (!StringUtils.hasText(token)) return null;

        try {
            String[] parts = token.split("\\.");
            // Validación estructural mínima del JWT.
            // Solo aceptamos header.payload.signature para evitar payloads corruptos.
            if (parts.length != 3) return null;

            byte[] payloadBytes = Base64.decode(parts[1], Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
            String payloadJson = new String(payloadBytes, StandardCharsets.UTF_8);
            JSONObject payload = new JSONObject(payloadJson);

            // Para que la sesión sea utilizable necesitamos, como mínimo,
            // la identidad (sub) y la expiración (exp).
            if (!payload.has("sub") || !payload.has("exp")) return null;

            return payload;
        } catch (Exception ignored) {
            return null;
        }
    }


    private void putEncryptedOrRemove(@NonNull SharedPreferences.Editor editor,
                                      @NonNull String ctKey,
                                      @NonNull String ivKey,
                                      @Nullable String plainText) throws Exception {
        if (StringUtils.hasText(plainText)) {
            putEncrypted(editor, ctKey, ivKey, plainText.trim());
            return;
        }
        editor.remove(ctKey).remove(ivKey);
    }

    private void putEncrypted(SharedPreferences.Editor editor,
                              String ctKey,
                              String ivKey,
                              String plainText) throws Exception {
        EncryptedValue enc = encrypt(plainText);
        editor.putString(ctKey, enc.cipherTextBase64);
        editor.putString(ivKey, enc.ivBase64);
    }

    /**
     * Lee y descifra un valor almacenado.
     *
     * <p>Si el material cifrado está corrupto, se limpia silenciosamente para que
     * la siguiente lectura no vuelva a chocar contra el mismo dato inválido.</p>
     */
    @Nullable
    private String getDecryptedValueLocked(String ctKey, String ivKey) {
        String ctBase64 = prefs.getString(ctKey, null);
        String ivBase64 = prefs.getString(ivKey, null);
        if (ctBase64 == null || ivBase64 == null) return null;
        try {
            return decrypt(ctBase64, ivBase64);
        } catch (Exception e) {
            prefs.edit().remove(ctKey).remove(ivKey).apply();
            return null;
        }
    }


    private void persistAuthProvider(@Nullable String authProvider, boolean persistSynchronously) {
        SharedPreferences.Editor editor = appContext
                .getSharedPreferences(AppSettingsManager.PREFS, Context.MODE_PRIVATE)
                .edit();
        if (StringUtils.hasText(authProvider)) {
            editor.putString("auth_provider", authProvider.trim());
        } else {
            editor.remove("auth_provider");
        }
        persistEditor(editor, persistSynchronously, "Error persistiendo el provider de autenticación");
    }

    /**
     * Limpia el provider persistido y desactiva el silent sign-in de Google con persistencia
     * síncrona para que el cambio quede aplicado antes de abandonar la pantalla o el proceso actual.
     */
    private void clearSocialAuthStateLocked() {
        SharedPreferences.Editor editor = appContext
                .getSharedPreferences(AppSettingsManager.PREFS, Context.MODE_PRIVATE)
                .edit()
                .remove("auth_provider")
                .putBoolean("google_silent_enabled", false);
        boolean committed = editor.commit();
        if (!committed) {
            throw new IllegalStateException("Error limpiando el estado social local");
        }
    }

    private void persistEditor(@NonNull SharedPreferences.Editor editor,
                               boolean persistSynchronously,
                               @NonNull String failureMessage) {
        if (persistSynchronously) {
            boolean committed = editor.commit();
            if (!committed) {
                throw new IllegalStateException(failureMessage);
            }
            return;
        }
        editor.apply();
    }

    private EncryptedValue encrypt(String plainText) throws Exception {
        SecretKey secretKey = getOrCreateSecretKey();
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, secretKey);
        byte[] iv = cipher.getIV();
        byte[] cipherBytes = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
        return new EncryptedValue(
                Base64.encodeToString(cipherBytes, Base64.NO_WRAP),
                Base64.encodeToString(iv, Base64.NO_WRAP)
        );
    }

    private String decrypt(String cipherTextBase64, String ivBase64) throws Exception {
        SecretKey secretKey = getOrCreateSecretKey();
        byte[] cipherBytes = Base64.decode(cipherTextBase64, Base64.NO_WRAP);
        byte[] iv = Base64.decode(ivBase64, Base64.NO_WRAP);
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec);
        byte[] plainBytes = cipher.doFinal(cipherBytes);
        return new String(plainBytes, StandardCharsets.UTF_8);
    }

    private SecretKey getOrCreateSecretKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
        keyStore.load(null);
        KeyStore.Entry existingEntry = keyStore.getEntry(KEYSTORE_ALIAS, null);
        if (existingEntry instanceof KeyStore.SecretKeyEntry) {
            return ((KeyStore.SecretKeyEntry) existingEntry).getSecretKey();
        }
        KeyGenerator keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE);
        KeyGenParameterSpec keySpec = new KeyGenParameterSpec.Builder(
                KEYSTORE_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build();
        keyGenerator.init(keySpec);
        return keyGenerator.generateKey();
    }

    private static class EncryptedValue {
        final String cipherTextBase64;
        final String ivBase64;

        EncryptedValue(String cipherTextBase64, String ivBase64) {
            this.cipherTextBase64 = cipherTextBase64;
            this.ivBase64 = ivBase64;
        }
    }
}
