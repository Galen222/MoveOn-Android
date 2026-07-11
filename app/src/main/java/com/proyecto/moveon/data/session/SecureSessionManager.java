package com.proyecto.moveon.data.session;

import android.annotation.SuppressLint;
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

    /**
     * Devuelve el singleton del gestor de sesión ligado al contexto de aplicación.
     *
     * @param context contexto desde el que resolver el {@code applicationContext} seguro.
     * @return instancia única reutilizable en toda la app.
     */
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

        /**
         * Devuelve el nombre visible asociado a la sesión leída.
         *
         * @return nombre persistido o {@code null} si la sesión no lo contiene.
         */
        @Nullable public String getUsername() { return username; }
        /**
         * Devuelve el access token capturado en el snapshot.
         *
         * @return access token descifrado o {@code null} cuando falta.
         */
        @Nullable public String getAccessToken() { return accessToken; }
        /**
         * Devuelve el refresh token capturado en el snapshot.
         *
         * @return refresh token descifrado o {@code null} cuando no existe.
         */
        @Nullable public String getRefreshToken() { return refreshToken; }
        /**
         * Devuelve el identificador de usuario derivado o persistido para la sesión.
         *
         * @return identificador interno del usuario o {@code null} si no pudo resolverse.
         */
        @Nullable public String getUserId() { return userId; }
        /**
         * Indica el proveedor de autenticación asociado al snapshot, si existe.
         *
         * @return proveedor persistido, por ejemplo Google, o {@code null} para login clásico.
         */
        @Nullable public String getAuthProvider() { return authProvider; }

        /**
         * Indica si el snapshot contiene tanto access token como refresh token y por tanto es plenamente reutilizable.
         *
         * @return {@code true} cuando la sesión puede reutilizarse sin pedir credenciales de nuevo.
         */
        public boolean hasCompleteSession() {
            return StringUtils.hasText(accessToken) && StringUtils.hasText(refreshToken);
        }

        /**
         * Indica si hay material mínimo para intentar una recuperación de sesión en segundo plano.
         *
         * @return {@code true} cuando existe al menos uno de los tokens necesarios para intentar recuperar sesión.
         */
        public boolean hasRecoverableSession() {
            return StringUtils.hasText(accessToken) || StringUtils.hasText(refreshToken);
        }

        /**
         * Comprueba si la sesión almacenada conserva refresh token.
         *
         * @return {@code true} cuando el snapshot aún mantiene un refresh token utilizable.
         */
        public boolean hasRefreshToken() {
            return StringUtils.hasText(refreshToken);
        }

        /**
         * Construye la clave estable de cuenta a partir del identificador de usuario del snapshot.
         *
         * @return clave derivada del {@code userId} o {@code null} si el snapshot no identifica una cuenta.
         */
        @Nullable
        public String getAccountKey() {
            return buildAccountKeyFromUserId(userId);
        }
    }

    private final Context appContext;
    private final SharedPreferences prefs;
    private final Object sessionLock = new Object();

    /**
     * Crea el gestor con contexto de aplicación para evitar fugas de Activity.
     */
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

    /**
     * Indica si existe una sesión completa con access y refresh token.
     */
    public boolean isLoggedIn() {
        synchronized (sessionLock) {
            return readSessionSnapshotLocked().hasCompleteSession();
        }
    }

    /**
     * Indica si no queda material suficiente para recuperar la sesión en segundo plano.
     */
    public boolean isSessionRecoveryUnavailable() {
        synchronized (sessionLock) {
            return !readSessionSnapshotLocked().hasRecoverableSession();
        }
    }

    /**
     * Comprueba si la sesión almacenada carece de refresh token.
     */
    public boolean isRefreshTokenMissing() {
        synchronized (sessionLock) {
            return !readSessionSnapshotLocked().hasRefreshToken();
        }
    }

    /**
     * Comprueba si el access token falta, es inválido o vencerá dentro de la ventana indicada.
     */
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

    /**
     * Recupera el access token descifrado de la sesión actual.
     *
     * @return access token actual o {@code null} si la sesión no lo conserva.
     */
    @Nullable
    public String getAccessToken() {
        synchronized (sessionLock) {
            return readSessionSnapshotLocked().getAccessToken();
        }
    }

    /**
     * Recupera el refresh token descifrado de la sesión actual.
     *
     * @return refresh token actual o {@code null} si se perdió o nunca existió.
     */
    @Nullable
    public String getRefreshToken() {
        synchronized (sessionLock) {
            return readSessionSnapshotLocked().getRefreshToken();
        }
    }

    /**
     * Devuelve el identificador o nombre de usuario persistido junto a la sesión.
     *
     * @return nombre de usuario recordado o {@code null} si la sesión no lo trae asociado.
     */
    @Nullable
    public String getUsername() {
        synchronized (sessionLock) {
            return readSessionSnapshotLocked().getUsername();
        }
    }

    /**
     * Devuelve el identificador interno del usuario asociado al access token actual.
     *
     * @return identificador interno derivado del token o recuperado de persistencia; {@code null} si no existe.
     */
    @Nullable
    public String getUserId() {
        synchronized (sessionLock) {
            return readSessionSnapshotLocked().getUserId();
        }
    }

    /**
     * Devuelve la clave estable de cuenta derivada del {@code userId} actual.
     *
     * @return clave lógica de cuenta usada por almacenamiento local o {@code null} si no puede construirse.
     */
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

    /**
     * Lee la sesión completa como un bloque coherente bajo el mismo lock interno.
     */
    @NonNull
    public SessionSnapshot getSessionSnapshot() {
        synchronized (sessionLock) {
            return readSessionSnapshotLocked();
        }
    }

    /**
     * Construye una clave de cuenta estable para caches y almacenamiento local a partir del identificador de usuario.
     */
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
    @SuppressLint("ApplySharedPref")
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

    /**
     * Elimina únicamente el access token persistido manteniendo el resto de la sesión intacta.
     */
    @SuppressLint("ApplySharedPref")
    public void clearAccessTokenOnly() {
        synchronized (sessionLock) {
            // Igual que en logout: el caller espera que el access desaparezca en el acto.
            prefs.edit()
                    .remove(KEY_ACCESS_TOKEN_CT).remove(KEY_ACCESS_TOKEN_IV)
                    .commit();
        }
    }

    /**
     * Guarda o elimina el identificador recordado usado para rellenar accesos posteriores.
     */
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

    /**
     * Recupera el identificador recordado almacenado de forma cifrada.
     */
    @Nullable
    public String getRememberedIdentifier() {
        synchronized (sessionLock) {
            return getDecryptedValueLocked(KEY_REMEMBERED_ID_CT, KEY_REMEMBERED_ID_IV);
        }
    }

    /**
     * Devuelve la preferencia local que controla los avisos de auto-pausa para nuevas sesiones.
     */
    public boolean shouldShowAutoPauseAlertsByDefault() {
        return AppSettingsManager.shouldShowAutoPauseAlertsByDefault(appContext);
    }

    /**
     * Actualiza la preferencia local de avisos de auto-pausa.
     */
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

    /**
     * Lee usuario, tokens y metadatos derivados dentro del lock para producir un snapshot consistente.
     */
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

    /**
     * Cachea silenciosamente el {@code userId} derivado del token sin romper la lectura si la escritura falla.
     */
    private void persistUserIdQuietlyLocked(@NonNull String userId) {
        try {
            SharedPreferences.Editor editor = prefs.edit();
            putEncrypted(editor, KEY_USER_ID_CT, KEY_USER_ID_IV, userId);
            editor.apply();
        } catch (Exception ignored) {
            // No interrumpimos la lectura de sesión por un fallo no crítico de caché.
        }
    }

    /**
     * Extrae el claim {@code sub} del access token para reutilizarlo como identificador local de usuario.
     */
    @Nullable
    private String extractUserIdFromAccessToken(@Nullable String accessToken) {
        JSONObject payload = decodeJwtPayload(accessToken);
        if (payload == null) return null;

        String sub = payload.optString("sub", null);
        return StringUtils.hasText(sub) ? sub.trim() : null;
    }

    /**
     * Extrae el instante de expiración del access token en segundos epoch.
     */
    @Nullable
    private Long extractExpFromAccessToken(@Nullable String accessToken) {
        JSONObject payload = decodeJwtPayload(accessToken);
        if (payload == null || !payload.has("exp")) return null;

        long exp = payload.optLong("exp", -1L);
        return exp > 0L ? exp : null;
    }

    /**
     * Decodifica el payload del JWT y exige los claims mínimos necesarios para considerar la sesión utilizable.
     */
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


    /**
     * Cifra y guarda un valor o elimina sus claves persistidas cuando el texto plano llega vacío.
     */
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

    /**
     * Cifra el texto indicado y escribe en el editor tanto el ciphertext como su IV asociado.
     */
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


    /**
     * Persiste el proveedor de autenticación en las preferencias globales con modo síncrono u asíncrono según el flujo.
     */
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
    @SuppressLint("ApplySharedPref")
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

    /**
     * Ejecuta la persistencia del editor eligiendo entre {@code commit()} y {@code apply()} según la criticidad del flujo.
     */
    @SuppressLint("ApplySharedPref")
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

    /**
     * Cifra un texto usando AES/GCM y devuelve tanto el ciphertext como el IV generado para esa operación.
     */
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

    /**
     * Descifra un valor almacenado previamente a partir de su ciphertext e IV en Base64.
     */
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

    /**
     * Recupera la clave simétrica del Android Keystore o la crea si todavía no existe para la versión actual del alias.
     */
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

        /**
         * Agrupa el material cifrado generado en una operación de escritura.
         */
        EncryptedValue(String cipherTextBase64, String ivBase64) {
            this.cipherTextBase64 = cipherTextBase64;
            this.ivBase64 = ivBase64;
        }
    }
}
