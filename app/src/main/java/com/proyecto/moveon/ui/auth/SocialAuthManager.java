
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

    /**
     * Crea el coordinador del flujo de autenticación social para una Activity concreta.
     *
     * @param activity activity anfitriona desde la que se abrirá {@link CredentialManager}.
     * @param listener receptor de resultados, errores y cancelaciones del flujo.
     */
    public SocialAuthManager(@NonNull AppCompatActivity activity, @NonNull Listener listener) {
        this.activity = activity;
        this.listener = listener;
        this.credentialManager = CredentialManager.create(activity);
    }

    /**
     * Inicia el login interactivo con Google usando {@link GetSignInWithGoogleOption}.
     */
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

    /**
     * Comprueba si falta la configuración mínima del cliente web de Google.
     *
     * @return {@code true} cuando {@link BuildConfig#GOOGLE_WEB_CLIENT_ID} está vacío.
     */
    private boolean isGoogleMissingConfiguration() {
        return !StringUtils.hasText(BuildConfig.GOOGLE_WEB_CLIENT_ID);
    }

    /**
     * Habilita el intento de restauración silenciosa con Google para futuros arranques.
     *
     * @param context contexto usado para persistir la preferencia en {@link AppSettingsManager}.
     */
    public static void enableSilentGoogleSignIn(@NonNull Context context) {
        AppSettingsManager.setGoogleSilentSignInEnabled(context, true);
    }

    /**
     * Deshabilita la restauración silenciosa con Google.
     *
     * @param context contexto usado para persistir la preferencia.
     */
    public static void disableSilentGoogleSignIn(@NonNull Context context) {
        AppSettingsManager.setGoogleSilentSignInEnabled(context, false);
    }

    /**
     * Indica si la app tiene permitido intentar un acceso silencioso con Google.
     *
     * @param context contexto usado para leer la preferencia.
     * @return {@code true} cuando la preferencia se ha habilitado tras un acceso real.
     */
    public static boolean isSilentGoogleSignInEnabled(@NonNull Context context) {
        // Arrancamos desactivado por defecto: solo se habilita tras un acceso real con Google.
        return AppSettingsManager.isGoogleSilentSignInEnabled(context);
    }

    /**
     * Envía la petición a {@link CredentialManager} y enruta la respuesta al handler adecuado.
     *
     * @param request solicitud ya configurada para login interactivo o silencioso.
     * @param silent {@code true} cuando el flujo no debe interrumpir la UX con errores cancelables.
     */
    private void requestCredential(@NonNull GetCredentialRequest request, boolean silent) {
        credentialManager.getCredentialAsync(
                activity,
                request,
                new CancellationSignal(),
                ContextCompat.getMainExecutor(activity),
                new CredentialManagerCallback<>() {
                    /**
                     * Entrega la credencial resuelta al manejador específico de Google para continuar el login.
                     *
                     * @param result respuesta devuelta por {@link CredentialManager}.
                     */
                    @Override
                    public void onResult(GetCredentialResponse result) {
                        handleGoogleCredential(result, silent);
                    }

                    /**
                     * Redirige el error del proveedor de credenciales al tratamiento común de fallos del flujo social.
                     *
                     * @param e excepción devuelta por {@link CredentialManager}.
                     */
                    @Override
                    public void onError(@NonNull GetCredentialException e) {
                        handleCredentialError(e, silent);
                    }
                }
        );
    }

    /**
     * Traduce un error de Credential Manager al contrato de callbacks de la pantalla.
     *
     * @param e excepción devuelta por la librería de credenciales.
     * @param silent {@code true} cuando el flujo era silencioso y ciertos errores deben ignorarse.
     */
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

    /**
     * Extrae y valida la credencial de Google recibida antes de notificar la cuenta lista.
     *
     * @param result respuesta de credenciales devuelta por Google.
     * @param silent {@code true} cuando el flujo se inició en modo silencioso.
     */
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

    /**
     * Convierte una excepción de credenciales en un mensaje localizado para la UI.
     *
     * @param e excepción original.
     * @param silent {@code true} cuando el mensaje debe ajustarse a un intento silencioso.
     * @return texto localizado que describe el fallo de la autenticación social.
     */
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

    /**
     * Registra un error de credenciales incluyendo datos mínimos de diagnóstico no sensibles.
     *
     * @param e excepción original.
     * @param silent {@code true} si el error ocurrió durante un intento silencioso.
     */
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

    /**
     * Detecta si el mensaje apunta a un fallo de reautenticación del proveedor.
     *
     * @param e excepción a inspeccionar.
     * @return {@code true} cuando el mensaje normalizado contiene patrones de reauth.
     */
    private boolean isReauthFailure(@NonNull GetCredentialException e) {
        String normalized = normalizedMessage(e);
        return normalized.contains("reauth failed")
                || normalized.contains("account reauth failed");
    }

    /**
     * Detecta errores que suelen indicar una mala configuración del proveedor Google/OAuth.
     *
     * @param e excepción a inspeccionar.
     * @return {@code true} cuando el texto sugiere un problema de consola, SHA, audiencia o autorización.
     */
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

    /**
     * Normaliza el mensaje de la excepción para facilitar búsquedas por patrones.
     *
     * @param e excepción a convertir.
     * @return mensaje saneado en minúsculas y sin nulos.
     */
    @NonNull
    private String normalizedMessage(@NonNull GetCredentialException e) {
        String raw = e.getMessage();
        return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Reduce y sanea un texto antes de incluirlo en logs.
     *
     * @param value texto original potencialmente nulo.
     * @return texto recortado y seguro para registro.
     */
    @NonNull
    private String sanitizeForLog(@Nullable String value) {
        if (!StringUtils.hasText(value)) {
            return "<empty>";
        }
        String trimmed = value.trim().replace('\n', ' ').replace('\r', ' ');
        return trimmed.length() > 240 ? trimmed.substring(0, 240) + "…" : trimmed;
    }

    /**
     * Devuelve un sufijo corto del client id para distinguir configuraciones en logs.
     *
     * @return últimos caracteres del client id o un marcador neutro si falta configuración.
     */
    @NonNull
    private String clientIdSuffix() {
        String clientId = BuildConfig.GOOGLE_WEB_CLIENT_ID;
        if (!StringUtils.hasText(clientId)) {
            return "<missing>";
        }
        int len = clientId.length();
        return len <= 12 ? clientId : clientId.substring(len - 12);
    }

    /**
     * Intenta extraer el email del payload del ID token sin validar su firma.
     *
     * <p>Se usa solo para precargar datos del flujo social; la verificación real ocurre en backend.</p>
     *
     * @param idToken token JWT emitido por Google.
     * @return email contenido en el payload o {@code null} si no pudo extraerse.
     */
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

    /**
     * Genera un nonce aleatorio para asociar la petición de credenciales con la respuesta.
     *
     * @return cadena Base64 URL-safe sin saltos de línea.
     */
    @NonNull
    private String generateNonce() {
        byte[] bytes = new byte[16];
        new SecureRandom().nextBytes(bytes);
        return Base64.encodeToString(bytes, Base64.NO_WRAP | Base64.NO_PADDING | Base64.URL_SAFE);
    }
}
