package com.proyecto.moveon.data.session;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import androidx.annotation.Nullable;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public class SecureSessionManager {

    private static final String PREF_NAME = "user_prefs_secure";

    // Alias de clave en Android Keystore
    private static final String KEYSTORE_ALIAS = "moveon_session_key_v1";
    private static final String ANDROID_KEYSTORE = "AndroidKeyStore";

    // Claves en SharedPreferences (guardan ciphertext + iv)
    private static final String KEY_ACCESS_TOKEN_CT = "access_token_ct";
    private static final String KEY_ACCESS_TOKEN_IV = "access_token_iv";

    private static final String KEY_REFRESH_TOKEN_CT = "refresh_token_ct";
    private static final String KEY_REFRESH_TOKEN_IV = "refresh_token_iv";

    private static final String KEY_USERNAME_CT = "username_ct";
    private static final String KEY_USERNAME_IV = "username_iv";
    private static final String KEY_REMEMBERED_ID_CT = "remembered_id_ct";
    private static final String KEY_REMEMBERED_ID_IV = "remembered_id_iv";

    // AES/GCM
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH_BITS = 128;

    private final SharedPreferences prefs;
    private final Context appContext;

    public SecureSessionManager(Context context) {
        this.appContext = context.getApplicationContext();
        this.prefs = appContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            throw new IllegalStateException("SecureSessionManager requiere minSdk 23+ (Android 6.0)");
        }
    }

    public void saveLogin(String username, String accessToken, String refreshToken) {
        if (username == null) username = "";
        if (accessToken == null) accessToken = "";
        if (refreshToken == null) refreshToken = "";

        try {
            SharedPreferences.Editor editor = prefs.edit();

            putEncrypted(editor, KEY_USERNAME_CT, KEY_USERNAME_IV, username);
            putEncrypted(editor, KEY_ACCESS_TOKEN_CT, KEY_ACCESS_TOKEN_IV, accessToken);
            putEncrypted(editor, KEY_REFRESH_TOKEN_CT, KEY_REFRESH_TOKEN_IV, refreshToken);

            editor.apply();
        } catch (Exception e) {
            throw new RuntimeException("Error guardando sesión segura", e);
        }
    }

    public void updateTokens(String accessToken, String refreshToken) {
        String username = getUsername();
        if (username == null) username = "";
        saveLogin(username, accessToken, refreshToken);
    }

    public boolean isLoggedIn() {
        // Preferimos refresh (sesión real). Mantenemos compatibilidad con token viejo.
        return hasText(getRefreshToken()) || hasText(getAccessToken());
    }

    public boolean hasRefreshSession() {
        return hasText(getRefreshToken());
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

    public void logout() {
        prefs.edit().clear().apply();
    }

    public void saveRememberedIdentifier(@Nullable String identifier) {
        try {
            SharedPreferences.Editor editor = prefs.edit();
            if (identifier == null || identifier.trim().isEmpty()) {
                // Si desmarca la casilla, borramos el rastro
                editor.remove(KEY_REMEMBERED_ID_CT);
                editor.remove(KEY_REMEMBERED_ID_IV);
            } else {
                // Si la marca, lo guardamos cifrado
                putEncrypted(editor, KEY_REMEMBERED_ID_CT, KEY_REMEMBERED_ID_IV, identifier);
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
    private boolean hasText(@Nullable String value) {
        return value != null && !value.trim().isEmpty();
    }

    // =========================
    // Helpers internos
    // =========================

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
            // Si falla (clave invalidada, datos corruptos, etc.), limpiamos sesión para evitar estados raros
            logout();
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
                KeyProperties.KEY_ALGORITHM_AES,
                ANDROID_KEYSTORE
        );

        KeyGenParameterSpec keySpec = new KeyGenParameterSpec.Builder(
                KEYSTORE_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT
        )
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