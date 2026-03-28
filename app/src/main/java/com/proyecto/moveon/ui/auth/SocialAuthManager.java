package com.proyecto.moveon.ui.auth;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.CancellationSignal;
import android.util.Base64;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.credentials.Credential;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.CustomCredential;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.GetCredentialCancellationException;
import androidx.credentials.exceptions.GetCredentialException;
import androidx.credentials.exceptions.NoCredentialException;

import com.google.android.libraries.identity.googleid.GetGoogleIdOption;
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;
import com.proyecto.moveon.BuildConfig;
import com.proyecto.moveon.R;
import com.proyecto.moveon.utils.StringUtils;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Locale;

public final class SocialAuthManager {

    private static final String PREFS_NAME = "social_auth_prefs";
    private static final String KEY_GOOGLE_SILENT_ENABLED = "google_silent_enabled";

    public interface Listener {
        void onGoogleAccountReady(@NonNull SocialGoogleAccount account, boolean silent);
        void onSocialFlowError(@NonNull String message, boolean silent);
        void onSocialFlowCanceled();
    }

    @NonNull private final AppCompatActivity activity;
    @NonNull private final Listener listener;
    @NonNull private final CredentialManager credentialManager;

    public SocialAuthManager(@NonNull AppCompatActivity activity, @NonNull Listener listener) {
        this.activity = activity;
        this.listener = listener;
        this.credentialManager = CredentialManager.create(activity);
    }

    public void signInWithGoogle() {
        if (isGoogleMissingConfiguration()) {
            listener.onSocialFlowError(activity.getString(R.string.social_google_not_configured), false);
            return;
        }

        GetSignInWithGoogleOption googleOption =
                new GetSignInWithGoogleOption.Builder(BuildConfig.GOOGLE_WEB_CLIENT_ID)
                        .setNonce(generateNonce())
                        .build();

        GetCredentialRequest request = new GetCredentialRequest.Builder()
                .addCredentialOption(googleOption)
                .build();

        requestCredential(request, false);
    }

    public void trySilentSignInWithGoogle() {
        if (isGoogleMissingConfiguration() || !isSilentGoogleSignInEnabled(activity)) {
            return;
        }

        GetGoogleIdOption googleOption = new GetGoogleIdOption.Builder()
                .setServerClientId(BuildConfig.GOOGLE_WEB_CLIENT_ID)
                .setFilterByAuthorizedAccounts(true)
                .setAutoSelectEnabled(true)
                .setNonce(generateNonce())
                .build();

        GetCredentialRequest request = new GetCredentialRequest.Builder()
                .addCredentialOption(googleOption)
                .build();

        requestCredential(request, true);
    }

    private boolean isGoogleMissingConfiguration() {
        return !StringUtils.hasText(BuildConfig.GOOGLE_WEB_CLIENT_ID);
    }

    public static void enableSilentGoogleSignIn(@NonNull Context context) {
        preferences(context).edit().putBoolean(KEY_GOOGLE_SILENT_ENABLED, true).apply();
    }

    public static void disableSilentGoogleSignIn(@NonNull Context context) {
        preferences(context).edit().putBoolean(KEY_GOOGLE_SILENT_ENABLED, false).apply();
    }

    public static boolean isSilentGoogleSignInEnabled(@NonNull Context context) {
        return preferences(context).getBoolean(KEY_GOOGLE_SILENT_ENABLED, true);
    }

    @NonNull
    private static SharedPreferences preferences(@NonNull Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    private void requestCredential(@NonNull GetCredentialRequest request, boolean silent) {
        credentialManager.getCredentialAsync(
                activity,
                request,
                new CancellationSignal(),
                ContextCompat.getMainExecutor(activity),
                new CredentialManagerCallback<>() {
                    @Override
                    public void onResult(GetCredentialResponse result) {
                        handleGoogleCredential(result, silent);
                    }

                    @Override
                    public void onError(@NonNull GetCredentialException e) {
                        handleCredentialError(e, silent);
                    }
                }
        );
    }

    private void handleCredentialError(@NonNull GetCredentialException e, boolean silent) {
        if (e instanceof GetCredentialCancellationException) {
            if (!silent) {
                listener.onSocialFlowCanceled();
            }
            return;
        }

        if (silent && e instanceof NoCredentialException) {
            return;
        }

        listener.onSocialFlowError(resolveGoogleErrorMessage(e, silent), silent);
    }

    private void handleGoogleCredential(@NonNull GetCredentialResponse result, boolean silent) {
        Credential credential = result.getCredential();
        if (!(credential instanceof CustomCredential)) {
            listener.onSocialFlowError(activity.getString(R.string.social_google_generic_error), silent);
            return;
        }

        CustomCredential customCredential = (CustomCredential) credential;
        if (!GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL.equals(customCredential.getType())) {
            listener.onSocialFlowError(activity.getString(R.string.social_google_generic_error), silent);
            return;
        }

        final GoogleIdTokenCredential googleCredential;
        try {
            googleCredential = GoogleIdTokenCredential.createFrom(customCredential.getData());
        } catch (RuntimeException e) {
            listener.onSocialFlowError(activity.getString(R.string.social_google_invalid_token), silent);
            return;
        }

        String idToken = googleCredential.getIdToken();
        if (!StringUtils.hasText(idToken)) {
            listener.onSocialFlowError(activity.getString(R.string.social_google_invalid_token), silent);
            return;
        }

        String avatarUrl = googleCredential.getProfilePictureUri() != null
                ? googleCredential.getProfilePictureUri().toString()
                : null;

        enableSilentGoogleSignIn(activity);

        SocialGoogleAccount account = new SocialGoogleAccount(
                idToken,
                extractEmailFromIdToken(idToken),
                googleCredential.getDisplayName(),
                avatarUrl
        );
        listener.onGoogleAccountReady(account, silent);
    }

    @NonNull
    private String resolveGoogleErrorMessage(@NonNull GetCredentialException e, boolean silent) {
        if (silent) {
            return activity.getString(R.string.social_google_silent_sign_in_failed);
        }

        String raw = e.getMessage();
        if (StringUtils.hasText(raw)) {
            String normalized = raw.toLowerCase(Locale.ROOT);
            if (normalized.contains("cancel")) {
                return activity.getString(R.string.social_auth_canceled);
            }
        }
        return activity.getString(R.string.social_google_generic_error);
    }

    @Nullable
    private String extractEmailFromIdToken(@Nullable String idToken) {
        if (!StringUtils.hasText(idToken)) return null;

        try {
            String[] parts = idToken.split("\\.");
            if (parts.length < 2 || !StringUtils.hasText(parts[1])) {
                return null;
            }

            byte[] decoded = Base64.decode(parts[1], Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
            String payload = new String(decoded, StandardCharsets.UTF_8);
            JSONObject json = new JSONObject(payload);

            String email = json.optString("email", null);
            return StringUtils.hasText(email) ? email : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    @NonNull
    private String generateNonce() {
        byte[] bytes = new byte[16];
        new SecureRandom().nextBytes(bytes);
        return Base64.encodeToString(bytes, Base64.NO_WRAP | Base64.NO_PADDING | Base64.URL_SAFE);
    }
}
