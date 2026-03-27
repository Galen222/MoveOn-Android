package com.proyecto.moveon.ui.auth;

import android.util.Base64;

import androidx.annotation.NonNull;
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

import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;
import com.proyecto.moveon.BuildConfig;
import com.proyecto.moveon.R;
import com.proyecto.moveon.utils.StringUtils;

import java.security.SecureRandom;

public final class SocialAuthManager {

    public interface Listener {
        void onGoogleTokenReady(@NonNull String idToken);
        void onSocialFlowError(@NonNull String message);
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
        if (!StringUtils.hasText(BuildConfig.GOOGLE_WEB_CLIENT_ID)) {
            listener.onSocialFlowError(activity.getString(R.string.social_google_not_configured));
            return;
        }

        GetSignInWithGoogleOption googleOption =
                new GetSignInWithGoogleOption.Builder(BuildConfig.GOOGLE_WEB_CLIENT_ID)
                        .setNonce(generateNonce())
                        .build();

        GetCredentialRequest request = new GetCredentialRequest.Builder()
                .addCredentialOption(googleOption)
                .build();

        credentialManager.getCredentialAsync(
                activity,
                request,
                null,
                ContextCompat.getMainExecutor(activity),
                new CredentialManagerCallback<GetCredentialResponse, GetCredentialException>() {
                    @Override
                    public void onResult(GetCredentialResponse result) {
                        handleGoogleCredential(result);
                    }

                    @Override
                    public void onError(@NonNull GetCredentialException e) {
                        if (e instanceof GetCredentialCancellationException) {
                            listener.onSocialFlowCanceled();
                            return;
                        }
                        listener.onSocialFlowError(resolveGoogleErrorMessage(e));
                    }
                }
        );
    }

    private void handleGoogleCredential(@NonNull GetCredentialResponse result) {
        Credential credential = result.getCredential();
        if (!(credential instanceof CustomCredential)) {
            listener.onSocialFlowError(activity.getString(R.string.social_google_generic_error));
            return;
        }

        CustomCredential customCredential = (CustomCredential) credential;
        if (!GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL.equals(customCredential.getType())) {
            listener.onSocialFlowError(activity.getString(R.string.social_google_generic_error));
            return;
        }

        final GoogleIdTokenCredential googleCredential;
        try {
            googleCredential = GoogleIdTokenCredential.createFrom(customCredential.getData());
        } catch (RuntimeException e) {
            listener.onSocialFlowError(activity.getString(R.string.social_google_invalid_token));
            return;
        }

        if (!StringUtils.hasText(googleCredential.getIdToken())) {
            listener.onSocialFlowError(activity.getString(R.string.social_google_invalid_token));
            return;
        }

        listener.onGoogleTokenReady(googleCredential.getIdToken());
    }

    @NonNull
    private String resolveGoogleErrorMessage(@NonNull GetCredentialException e) {
        String raw = e.getMessage();
        if (StringUtils.hasText(raw)) {
            String normalized = raw.toLowerCase();
            if (normalized.contains("cancel")) {
                return activity.getString(R.string.social_auth_canceled);
            }
        }
        return activity.getString(R.string.social_google_generic_error);
    }

    @NonNull
    private String generateNonce() {
        byte[] bytes = new byte[16];
        new SecureRandom().nextBytes(bytes);
        return Base64.encodeToString(bytes, Base64.NO_WRAP | Base64.NO_PADDING | Base64.URL_SAFE);
    }
}
