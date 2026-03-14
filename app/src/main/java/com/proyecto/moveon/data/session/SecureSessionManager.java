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
    private static final String KEY_NOTIFICATIONS_CT = "notifications_enabled_ct";
    private static final String KEY_NOTIFICATIONS_IV = "notifications_enabled_iv";

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

    private final SharedPreferences prefs;

    private SecureSessionManager(Context context) {
        Context appContext = context.getApplicationContext();
        this.prefs = appContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public void saveLogin(String username, String accessToken, String refreshToken) {
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

    public void updateTokens(String accessToken, String refreshToken) {
        String username = getUsername();
        saveLogin(username, accessToken, refreshToken);
    }

    public boolean isLoggedIn() {
        return StringUtils.hasText(getAccessToken()) && StringUtils.hasText(getRefreshToken());
    }

    @Nullable
    public String getAccessToken() {
        return getDecryptedValue(KEY_ACCESS_TOKEN_CT, KEY_ACCESS_TOKEN_IV);
    }

    @Nullable
    public String getRefreshToken() {
        return getDecryptedValue(KEY_REFRESH_TOKEN_CT, KEY_REFRESH_TOKEN_IV);
    }

    @Nullable
    public String getUsername() {
        return getDecryptedValue(KEY_USERNAME_CT, KEY_USERNAME_IV);
    }

    @Nullable
    public String getUserId() {
        String stored = getDecryptedValue(KEY_USER_ID_CT, KEY_USER_ID_IV);
        if (StringUtils.hasText(stored)) {
            return stored;
        }

        String parsed = extractUserIdFromAccessToken(getAccessToken());
        if (StringUtils.hasText(parsed)) {
            persistUserIdQuietly(parsed);
            return parsed;
        }
        return null;
    }

    @Nullable
    public String getAccountKey() {
        return buildAccountKeyFromUserId(getUserId());
    }

    @Nullable
    public static String buildAccountKeyFromUserId(@Nullable String userId) {
        if (!StringUtils.hasText(userId)) return null;
        return "uid_" + userId.trim();
    }

    public void logout() {
        prefs.edit()
                .remove(KEY_USERNAME_CT).remove(KEY_USERNAME_IV)
                .remove(KEY_ACCESS_TOKEN_CT).remove(KEY_ACCESS_TOKEN_IV)
                .remove(KEY_REFRESH_TOKEN_CT).remove(KEY_REFRESH_TOKEN_IV)
                .remove(KEY_USER_ID_CT).remove(KEY_USER_ID_IV)
                .apply();
    }

    public void clearAccessTokenOnly() {
        prefs.edit()
                .remove(KEY_ACCESS_TOKEN_CT).remove(KEY_ACCESS_TOKEN_IV)
                .apply();
    }

    public void saveRememberedIdentifier(@Nullable String identifier) {
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

    @Nullable
    public String getRememberedIdentifier() {
        return getDecryptedValue(KEY_REMEMBERED_ID_CT, KEY_REMEMBERED_ID_IV);
    }

    public void saveNotificationsEnabled(boolean enabled) {
        try {
            SharedPreferences.Editor editor = prefs.edit();
            putEncrypted(editor, KEY_NOTIFICATIONS_CT, KEY_NOTIFICATIONS_IV, Boolean.toString(enabled));
            editor.apply();
        } catch (Exception e) {
            throw new RuntimeException("Error guardando preferencia de notificaciones", e);
        }
    }

    public boolean areNotificationsEnabled() {
        String value = getDecryptedValue(KEY_NOTIFICATIONS_CT, KEY_NOTIFICATIONS_IV);
        return Boolean.parseBoolean(StringUtils.textOf(value));
    }

    public boolean hasNotificationsPreference() {
        return prefs.contains(KEY_NOTIFICATIONS_CT) && prefs.contains(KEY_NOTIFICATIONS_IV);
    }

    private void persistUserIdQuietly(@NonNull String userId) {
        try {
            SharedPreferences.Editor editor = prefs.edit();
            putEncrypted(editor, KEY_USER_ID_CT, KEY_USER_ID_IV, userId);
            editor.apply();
        } catch (Exception ignored) {
        }
    }

    @Nullable
    private String extractUserIdFromAccessToken(@Nullable String accessToken) {
        if (!StringUtils.hasText(accessToken)) return null;

        try {
            String[] parts = accessToken.split("\\.");
            if (parts.length < 2) return null;

            byte[] payloadBytes = Base64.decode(parts[1], Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
            String payloadJson = new String(payloadBytes, StandardCharsets.UTF_8);
            JSONObject payload = new JSONObject(payloadJson);

            String sub = payload.optString("sub", null);
            return StringUtils.hasText(sub) ? sub.trim() : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private void putEncrypted(SharedPreferences.Editor editor, String ctKey, String ivKey, String plainText) throws Exception {
        EncryptedValue enc = encrypt(plainText);
        editor.putString(ctKey, enc.cipherTextBase64);
        editor.putString(ivKey, enc.ivBase64);
    }

    @Nullable
    private String getDecryptedValue(String ctKey, String ivKey) {
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
