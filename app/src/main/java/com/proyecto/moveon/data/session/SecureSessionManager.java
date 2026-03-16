package com.proyecto.moveon.data.session;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.proyecto.moveon.utils.StringUtils;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public final class SecureSessionManager {

    private static final String PREF_NAME = "user_prefs_secure";
    private static final String KEYSTORE_ALIAS = "moveon_session_key_v1";
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

    public static final class SessionSnapshot {
        @Nullable private final String username;
        @Nullable private final String accessToken;
        @Nullable private final String refreshToken;
        @Nullable private final String userId;

        private SessionSnapshot(@Nullable String username,
                                @Nullable String accessToken,
                                @Nullable String refreshToken,
                                @Nullable String userId) {
            this.username = username;
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
            this.userId = userId;
        }

        @Nullable public String getUsername() { return username; }
        @Nullable public String getAccessToken() { return accessToken; }
        @Nullable public String getRefreshToken() { return refreshToken; }
        @Nullable public String getUserId() { return userId; }

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

    private final SharedPreferences prefs;
    private final Object sessionLock = new Object();

    private SecureSessionManager(Context context) {
        Context appContext = context.getApplicationContext();
        this.prefs = appContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public void saveLogin(@Nullable String username,
                          @Nullable String accessToken,
                          @Nullable String refreshToken) {
        synchronized (sessionLock) {
            saveLoginLocked(username, accessToken, refreshToken);
        }
    }

    public void updateTokens(@Nullable String accessToken, @Nullable String refreshToken) {
        synchronized (sessionLock) {
            String username = getDecryptedValueLocked(KEY_USERNAME_CT, KEY_USERNAME_IV);
            saveLoginLocked(username, accessToken, refreshToken);
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

    public void logout() {
        synchronized (sessionLock) {
            prefs.edit()
                    .remove(KEY_USERNAME_CT).remove(KEY_USERNAME_IV)
                    .remove(KEY_ACCESS_TOKEN_CT).remove(KEY_ACCESS_TOKEN_IV)
                    .remove(KEY_REFRESH_TOKEN_CT).remove(KEY_REFRESH_TOKEN_IV)
                    .remove(KEY_USER_ID_CT).remove(KEY_USER_ID_IV)
                    .apply();
        }
    }

    public void clearAccessTokenOnly() {
        synchronized (sessionLock) {
            prefs.edit()
                    .remove(KEY_ACCESS_TOKEN_CT).remove(KEY_ACCESS_TOKEN_IV)
                    .apply();
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

    private void saveLoginLocked(@Nullable String username,
                                 @Nullable String accessToken,
                                 @Nullable String refreshToken) {
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

            editor.apply();
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
        if (!StringUtils.hasText(userId)) {
            userId = extractUserIdFromAccessToken(accessToken);
            if (StringUtils.hasText(userId)) {
                persistUserIdQuietlyLocked(userId);
            }
        }

        return new SessionSnapshot(username, accessToken, refreshToken, userId);
    }

    private void persistUserIdQuietlyLocked(@NonNull String userId) {
        try {
            SharedPreferences.Editor editor = prefs.edit();
            putEncrypted(editor, KEY_USER_ID_CT, KEY_USER_ID_IV, userId);
            editor.apply();
        } catch (Exception ignored) {
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
            if (parts.length < 2) return null;

            byte[] payloadBytes = Base64.decode(parts[1], Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
            String payloadJson = new String(payloadBytes, StandardCharsets.UTF_8);
            return new JSONObject(payloadJson);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void putEncrypted(SharedPreferences.Editor editor,
                              String ctKey,
                              String ivKey,
                              String plainText) throws Exception {
        EncryptedValue enc = encrypt(plainText);
        editor.putString(ctKey, enc.cipherTextBase64);
        editor.putString(ivKey, enc.ivBase64);
    }

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
