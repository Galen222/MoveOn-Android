
package com.proyecto.moveon.ui.auth;

import android.content.Context;
import android.os.CancellationSignal;
import android.util.Base64;
import android.util.Log;

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
import com.proyecto.moveon.core.settings.AppSettingsManager;
import com.proyecto.moveon.R;
import com.proyecto.moveon.utils.StringUtils;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Locale;
/**
 * Gestor que coordina la lógica relacionada con social auth.
 */
public final class SocialAuthManager {

    private static final String TAG = "SocialAuthManager";

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

    /**
     * Intenta restaurar el acceso con Google solo cuando el caller confirma que la última
     * sesión recuperable pertenecía a ese provider.
     *
     * <p>Así evitamos abrir Credential Manager o consultar Google en arranques donde la app
     * ya sabe que el usuario venía de email/usuario u otro flujo no social.</p>
     */
    public void trySilentSignInWithGoogle(boolean lastSessionUsedGoogle) {
        if (!lastSessionUsedGoogle) {
            return;
        }
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
        AppSettingsManager.setGoogleSilentSignInEnabled(context, true);
    }

    public static void disableSilentGoogleSignIn(@NonNull Context context) {
        AppSettingsManager.setGoogleSilentSignInEnabled(context, false);
    }

    public static boolean isSilentGoogleSignInEnabled(@NonNull Context context) {
        // Arrancamos desactivado por defecto: solo se habilita tras un acceso real con Google.
        return AppSettingsManager.isGoogleSilentSignInEnabled(context);
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
        logCredentialError(e, silent);

        if (silent && e instanceof NoCredentialException) {
            Log.i(TAG, "credential_no_credentials_silent");
            return;
        }

        if (e instanceof GetCredentialCancellationException) {
            if (isReauthFailure(e)) {
                listener.onSocialFlowError(
                        activity.getString(R.string.social_google_provider_configuration_error),
                        silent
                );
                return;
            }

            if (!silent) {
                listener.onSocialFlowCanceled();
            } else {
                Log.i(TAG, "credential_canceled_silent");
            }
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
        if (isReauthFailure(e) || looksLikeProviderConfigurationIssue(e)) {
            return activity.getString(R.string.social_google_provider_configuration_error);
        }

        if (e instanceof NoCredentialException) {
            return silent
                    ? activity.getString(R.string.social_google_silent_sign_in_failed)
                    : activity.getString(R.string.social_google_no_credentials);
        }

        if (silent) {
            return activity.getString(R.string.social_google_silent_sign_in_failed);
        }

        return activity.getString(R.string.social_google_generic_error);
    }

    private void logCredentialError(@NonNull GetCredentialException e, boolean silent) {
        Log.e(
                TAG,
                "credential_error"
                        + " silent=" + silent
                        + " exceptionClass=" + e.getClass().getName()
                        + " rawMessage=" + sanitizeForLog(e.getMessage())
                        + " appId=" + BuildConfig.APPLICATION_ID
                        + " clientIdSuffix=" + clientIdSuffix(),
                e
        );
    }

    private boolean isReauthFailure(@NonNull GetCredentialException e) {
        String normalized = normalizedMessage(e);
        return normalized.contains("reauth failed")
                || normalized.contains("account reauth failed");
    }

    private boolean looksLikeProviderConfigurationIssue(@NonNull GetCredentialException e) {
        String normalized = normalizedMessage(e);
        return normalized.contains("developer console is not set up correctly")
                || normalized.contains("oauth")
                || normalized.contains("unauthorized")
                || normalized.contains("invalid audience")
                || normalized.contains("sha")
                || normalized.contains("package name")
                || normalized.contains("configuration");
    }

    @NonNull
    private String normalizedMessage(@NonNull GetCredentialException e) {
        String raw = e.getMessage();
        return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
    }

    @NonNull
    private String sanitizeForLog(@Nullable String value) {
        if (!StringUtils.hasText(value)) {
            return "<empty>";
        }
        String trimmed = value.trim().replace('\n', ' ').replace('\r', ' ');
        return trimmed.length() > 240 ? trimmed.substring(0, 240) + "…" : trimmed;
    }

    @NonNull
    private String clientIdSuffix() {
        String clientId = BuildConfig.GOOGLE_WEB_CLIENT_ID;
        if (!StringUtils.hasText(clientId)) {
            return "<missing>";
        }
        int len = clientId.length();
        return len <= 12 ? clientId : clientId.substring(len - 12);
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
