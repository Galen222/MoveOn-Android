package com.proyecto.moveon.ui.auth;

import static org.junit.Assert.*;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.proyecto.moveon.BuildConfig;
import com.proyecto.moveon.utils.StringUtils;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Tests de las utilidades internas de {@link SocialAuthManager}.
 * <p>
 * Se instancia con Unsafe para no abrir Credential Manager: solo se ejercitan
 * métodos puros y deterministas que antes quedaban fuera de cobertura.
 */
@RunWith(RobolectricTestRunner.class)
public class SocialAuthManagerUtilityTest {

    @Test
    public void extractEmailFromIdToken_readsEmailFromJwtPayload() throws Exception {
        SocialAuthManager manager = allocateManager();
        String token = jwtWithPayload("{\"email\":\"alice@example.com\"}");

        String email = (String) invoke(
                manager,
                "extractEmailFromIdToken",
                new Class<?>[]{String.class},
                token
        );

        assertEquals("alice@example.com", email);
    }

    @Test
    public void extractEmailFromIdToken_returnsNullForBlankMalformedOrMissingEmailPayloads()
            throws Exception {
        SocialAuthManager manager = allocateManager();

        assertNull(invoke(manager, "extractEmailFromIdToken", new Class<?>[]{String.class}, (String) null));
        assertNull(invoke(manager, "extractEmailFromIdToken", new Class<?>[]{String.class}, "   "));
        assertNull(invoke(manager, "extractEmailFromIdToken", new Class<?>[]{String.class}, "no-puntos"));
        assertNull(invoke(manager, "extractEmailFromIdToken", new Class<?>[]{String.class}, "header..signature"));
        assertNull(invoke(manager, "extractEmailFromIdToken", new Class<?>[]{String.class}, "header.@@@@.signature"));
        assertNull(invoke(
                manager,
                "extractEmailFromIdToken",
                new Class<?>[]{String.class},
                jwtWithPayload("{\"email\":\"   \"}")
        ));
        assertNull(invoke(
                manager,
                "extractEmailFromIdToken",
                new Class<?>[]{String.class},
                jwtWithPayload("{\"name\":\"Alice\"}")
        ));
    }

    @Test
    public void sanitizeForLog_handlesEmptyTextNormalizesLineBreaksAndTruncatesLongValues()
            throws Exception {
        SocialAuthManager manager = allocateManager();

        assertEquals("<empty>", invoke(manager, "sanitizeForLog", new Class<?>[]{String.class}, (String) null));
        assertEquals("<empty>", invoke(manager, "sanitizeForLog", new Class<?>[]{String.class}, "   "));
        assertEquals("a b c", invoke(manager, "sanitizeForLog", new Class<?>[]{String.class}, "  a\nb\rc  "));

        StringBuilder longValue = new StringBuilder();
        for (int i = 0; i < 260; i++) {
            longValue.append('x');
        }

        String sanitized = (String) invoke(
                manager,
                "sanitizeForLog",
                new Class<?>[]{String.class},
                longValue.toString()
        );

        assertEquals(241, sanitized.length());
        assertTrue(sanitized.endsWith("…"));
    }


    @Test
    public void silentGoogleSignInPreference_canBeEnabledDisabledAndRead() {
        Context context = ApplicationProvider.getApplicationContext();

        SocialAuthManager.disableSilentGoogleSignIn(context);
        assertFalse(SocialAuthManager.isSilentGoogleSignInEnabled(context));

        SocialAuthManager.enableSilentGoogleSignIn(context);
        assertTrue(SocialAuthManager.isSilentGoogleSignInEnabled(context));

        SocialAuthManager.disableSilentGoogleSignIn(context);
        assertFalse(SocialAuthManager.isSilentGoogleSignInEnabled(context));
    }

    @Test
    public void clientIdSuffix_returnsMissingOrLastTwelveCharacters() throws Exception {
        SocialAuthManager manager = allocateManager();

        String suffix = (String) invoke(manager, "clientIdSuffix", new Class<?>[]{});

        String clientId = BuildConfig.GOOGLE_WEB_CLIENT_ID;
        String expectedSuffix = !StringUtils.hasText(clientId)
                ? "<missing>"
                : clientId.substring(Math.max(0, clientId.length() - 12));
        assertEquals(expectedSuffix, suffix);
    }

    @Test
    public void generateNonce_returnsUrlSafeBase64WithoutPaddingAndChangesBetweenCalls()
            throws Exception {
        SocialAuthManager manager = allocateManager();

        String first = (String) invoke(manager, "generateNonce", new Class<?>[]{});
        String second = (String) invoke(manager, "generateNonce", new Class<?>[]{});

        assertEquals(22, first.length());
        assertEquals(22, second.length());
        assertTrue(first.matches("[A-Za-z0-9_-]+"));
        assertTrue(second.matches("[A-Za-z0-9_-]+"));
        assertFalse(first.contains("="));
        assertFalse(second.contains("="));
        assertNotEquals(first, second);
    }

    private static String jwtWithPayload(String jsonPayload) {
        String payload = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(jsonPayload.getBytes(StandardCharsets.UTF_8));
        return "header." + payload + ".signature";
    }

    private static Object invoke(Object target,
                                 String methodName,
                                 Class<?>[] parameterTypes,
                                 Object... args) throws Exception {
        Method method = SocialAuthManager.class.getDeclaredMethod(methodName, parameterTypes);
        method.setAccessible(true);
        return method.invoke(target, args);
    }

    private static SocialAuthManager allocateManager() throws Exception {
        Field field = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");
        field.setAccessible(true);
        Object unsafe = java.util.Objects.requireNonNull(field.get(null), "Unsafe no disponible");
        Method method = unsafe.getClass().getMethod("allocateInstance", Class.class);
        return (SocialAuthManager) method.invoke(unsafe, SocialAuthManager.class);
    }
}
