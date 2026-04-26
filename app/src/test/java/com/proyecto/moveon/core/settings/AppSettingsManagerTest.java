package com.proyecto.moveon.core.settings;

import static org.junit.Assert.*;

import com.proyecto.moveon.core.i18n.AppLanguageManager;
import com.proyecto.moveon.core.theme.ThemeManager;
import com.proyecto.moveon.testutil.MemoryContext;

import org.junit.Test;

/**
 * Tests de preferencias globales de {@link AppSettingsManager} usando almacenamiento en memoria.
 */
public class AppSettingsManagerTest {

    /**
     * Verifica que el tema por defecto es sistema y que una selección explícita queda marcada como existente.
     */
    @Test
    public void themeMode_defaultsToSystemAndPersistsExplicitValue() {
        MemoryContext context = new MemoryContext();

        assertEquals(ThemeManager.MODE_SYSTEM, AppSettingsManager.getThemeMode(context));
        assertFalse(AppSettingsManager.hasThemeMode(context));

        AppSettingsManager.setThemeMode(context, ThemeManager.MODE_DARK);

        assertEquals(ThemeManager.MODE_DARK, AppSettingsManager.getThemeMode(context));
        assertTrue(AppSettingsManager.hasThemeMode(context));
    }

    /**
     * Verifica que sólo los idiomas soportados se devuelven como preferencia válida.
     */
    @Test
    public void storedAppLanguage_returnsOnlySupportedModes() {
        MemoryContext context = new MemoryContext();

        assertNull(AppSettingsManager.getStoredAppLanguage(context));
        assertFalse(AppSettingsManager.hasAppLanguage(context));

        AppSettingsManager.setAppLanguage(context, AppLanguageManager.MODE_SPANISH);

        assertEquals(AppLanguageManager.MODE_SPANISH, AppSettingsManager.getStoredAppLanguage(context));
        assertEquals(AppLanguageManager.MODE_SPANISH, AppSettingsManager.getRawAppLanguage(context));
        assertTrue(AppSettingsManager.hasAppLanguage(context));

        AppSettingsManager.setAppLanguage(context, "fr");

        assertNull(AppSettingsManager.getStoredAppLanguage(context));
        assertNull(AppSettingsManager.getRawAppLanguage(context));
        assertFalse(AppSettingsManager.hasAppLanguage(context));
    }

    /**
     * Verifica que limpiar el idioma manual elimina el valor bruto y el modo validado.
     */
    @Test
    public void clearAppLanguage_removesRawAndValidatedLanguage() {
        MemoryContext context = new MemoryContext();
        AppSettingsManager.setAppLanguage(context, AppLanguageManager.MODE_ENGLISH);

        AppSettingsManager.clearAppLanguage(context);

        assertNull(AppSettingsManager.getRawAppLanguage(context));
        assertNull(AppSettingsManager.getStoredAppLanguage(context));
        assertFalse(AppSettingsManager.hasAppLanguage(context));
    }

    /**
     * Verifica que el modo de ritmo normaliza entradas desconocidas al modo total.
     */
    @Test
    public void paceDisplayMode_normalizesUnknownValuesToTotal() {
        MemoryContext context = new MemoryContext();

        assertEquals(AppSettingsManager.PACE_DISPLAY_TOTAL, AppSettingsManager.getPaceDisplayMode(context));
        assertFalse(AppSettingsManager.isPaceDisplayMoving(context));

        AppSettingsManager.setPaceDisplayMode(context, AppSettingsManager.PACE_DISPLAY_MOVING);

        assertEquals(AppSettingsManager.PACE_DISPLAY_MOVING, AppSettingsManager.getPaceDisplayMode(context));
        assertTrue(AppSettingsManager.isPaceDisplayMoving(context));

        AppSettingsManager.setPaceDisplayMode(context, "invalid");

        assertEquals(AppSettingsManager.PACE_DISPLAY_TOTAL, AppSettingsManager.getPaceDisplayMode(context));
        assertFalse(AppSettingsManager.isPaceDisplayMoving(context));
    }

    /**
     * Verifica que la preferencia de alertas de auto-pausa parte activada y puede persistirse en falso.
     */
    @Test
    public void autoPauseAlerts_defaultTrueAndCanBeDisabled() {
        MemoryContext context = new MemoryContext();

        assertTrue(AppSettingsManager.shouldShowAutoPauseAlertsByDefault(context));

        AppSettingsManager.setShowAutoPauseAlertsByDefault(context, false);

        assertFalse(AppSettingsManager.shouldShowAutoPauseAlertsByDefault(context));
    }

    /**
     * Verifica que el provider de autenticación se recorta y se elimina cuando llega vacío.
     */
    @Test
    public void authProvider_trimsValueAndRemovesBlankValues() {
        MemoryContext context = new MemoryContext();

        AppSettingsManager.setAuthProvider(context, " google ");

        assertEquals("google", AppSettingsManager.getAuthProvider(context));

        AppSettingsManager.setAuthProvider(context, "   ");

        assertNull(AppSettingsManager.getAuthProvider(context));
    }

    /**
     * Verifica que el estado social limpia provider y desactiva silent sign-in de Google.
     */
    @Test
    public void clearSocialAuthState_removesProviderAndDisablesSilentSignIn() {
        MemoryContext context = new MemoryContext();
        AppSettingsManager.setAuthProvider(context, "google");
        AppSettingsManager.setGoogleSilentSignInEnabled(context, true);

        AppSettingsManager.clearSocialAuthState(context);

        assertNull(AppSettingsManager.getAuthProvider(context));
        assertFalse(AppSettingsManager.isGoogleSilentSignInEnabled(context));
    }

    /**
     * Verifica el ciclo de marca pendiente para forzar el splash de transición.
     */
    @Test
    public void uiTransitionSplash_canBeRequestedAndCleared() {
        MemoryContext context = new MemoryContext();

        assertFalse(AppSettingsManager.isUiTransitionSplashRequested(context));

        AppSettingsManager.requestUiTransitionSplash(context);

        assertTrue(AppSettingsManager.isUiTransitionSplashRequested(context));

        AppSettingsManager.clearUiTransitionSplashRequest(context);

        assertFalse(AppSettingsManager.isUiTransitionSplashRequested(context));
    }

    /**
     * Verifica que cada flag de permisos de tracking se guarda y se limpia de forma independiente.
     */
    @Test
    public void trackingPermissionFlags_canBeSetAndClearedTogether() {
        MemoryContext context = new MemoryContext();

        assertFalse(AppSettingsManager.wasTrackingLocationPermissionRequested(context));
        assertFalse(AppSettingsManager.wasTrackingActivityPermissionRequested(context));
        assertFalse(AppSettingsManager.wasTrackingNotificationsPermissionRequested(context));

        AppSettingsManager.setTrackingLocationPermissionRequested(context, true);
        AppSettingsManager.setTrackingActivityPermissionRequested(context, true);
        AppSettingsManager.setTrackingNotificationsPermissionRequested(context, true);

        assertTrue(AppSettingsManager.wasTrackingLocationPermissionRequested(context));
        assertTrue(AppSettingsManager.wasTrackingActivityPermissionRequested(context));
        assertTrue(AppSettingsManager.wasTrackingNotificationsPermissionRequested(context));

        AppSettingsManager.clearTrackingPermissionRequestFlags(context);

        assertFalse(AppSettingsManager.wasTrackingLocationPermissionRequested(context));
        assertFalse(AppSettingsManager.wasTrackingActivityPermissionRequested(context));
        assertFalse(AppSettingsManager.wasTrackingNotificationsPermissionRequested(context));
    }
}
