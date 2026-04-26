package com.proyecto.moveon.core.api;

import static org.junit.Assert.*;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/**
 * Tests del método público {@link BackendErrorLocalizer#localize(android.content.Context, String, String)}
 * ejercitando las ramas de localización a partir de códigos del backend.
 *
 * <p>Se ejecuta bajo {@link RobolectricTestRunner} para que los códigos
 * mapeados resuelvan su recurso real desde {@code app/src/main/res}.</p>
 */
@RunWith(RobolectricTestRunner.class)

public class BackendErrorLocalizerLocalizeTest {

    /**
     * Devuelve el contexto de aplicación gestionado por Robolectric.
     *
     * @return contexto Android funcional con recursos del módulo {@code app}.
     */
    private static Context appContext() {
        return ApplicationProvider.getApplicationContext();
    }

    /**
     * Verifica que un código en blanco devuelve {@code null} sin consultar el catálogo.
     */
    @Test
    public void localize_blankCode_returnsNull() {
        Context context = appContext();

        assertNull(BackendErrorLocalizer.localize(context, "", null));
        assertNull(BackendErrorLocalizer.localize(context, "   ", null));
        assertNull(BackendErrorLocalizer.localize(context, null, null));
    }

    /**
     * Verifica que un código que tras normalizar queda vacío también devuelve
     * {@code null}, garantizando que la normalización no introduzca claves
     * espurias en el catálogo.
     */
    @Test
    public void localize_codeNormalizesToEmpty_returnsNull() {
        assertNull(BackendErrorLocalizer.localize(appContext(), "!!! --- ...", null));
    }

    /**
     * Verifica que un código no mapeado devuelve {@code null} sin lanzar.
     */
    @Test
    public void localize_unmappedCode_returnsNull() {
        assertNull(BackendErrorLocalizer.localize(appContext(), "totally_made_up_code", null));
    }

    /**
     * Verifica que un código mapeado conocido devuelve un texto no nulo.
     */
    @Test
    public void localize_mappedCode_returnsLocalizedString() {
        String result = BackendErrorLocalizer.localize(appContext(), "username_required", null);

        assertNotNull(result);
        assertFalse(result.trim().isEmpty());
    }

    /**
     * Verifica que la entrada en mayúsculas y con guiones se normaliza al
     * mismo código mapeado.
     */
    @Test
    public void localize_mixedCaseAndDashes_resolvesToSameMapping() {
        String upperDash = BackendErrorLocalizer.localize(appContext(), "USERNAME-REQUIRED", null);
        String snake = BackendErrorLocalizer.localize(appContext(), "username_required", null);

        assertNotNull(upperDash);
        assertEquals(snake, upperDash);
    }

    /**
     * Verifica que {@code rate_limit_exceeded} con cabecera {@code Retry-After}
     * usa la plantilla específica con segundos.
     */
    @Test
    public void localize_rateLimitWithRetryAfter_usesRetryTemplate() {
        String withRetry = BackendErrorLocalizer.localize(
                appContext(), "rate_limit_exceeded", "30");
        String withoutRetry = BackendErrorLocalizer.localize(
                appContext(), "rate_limit_exceeded", null);

        assertNotNull(withRetry);
        assertNotNull(withoutRetry);
    }

    /**
     * Verifica que {@code rate_limit_exceeded} con {@code Retry-After} en blanco
     * cae en el fallback sin Retry-After.
     */
    @Test
    public void localize_rateLimitWithBlankRetryAfter_fallsBackToGenericTemplate() {
        String result = BackendErrorLocalizer.localize(appContext(), "rate_limit_exceeded", "  ");

        assertNotNull(result);
        assertFalse(result.trim().isEmpty());
    }
}
