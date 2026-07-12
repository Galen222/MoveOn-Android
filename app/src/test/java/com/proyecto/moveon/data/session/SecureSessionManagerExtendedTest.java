package com.proyecto.moveon.data.session;

import static org.junit.Assert.*;

import android.content.SharedPreferences;

import com.proyecto.moveon.core.settings.AppSettingsManager;
import com.proyecto.moveon.testutil.MemoryContext;
import com.proyecto.moveon.testutil.MemorySharedPreferences;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;

/**
 * Tests JVM extendidos para {@link SecureSessionManager} cubriendo logout,
 * limpieza parcial, gestión del identificador recordado, lectura de
 * snapshots con el dato persistido sin cifrar, y parseo de JWT — sin
 * depender del Android Keystore real.
 *
 * <p>Estos tests complementan {@code SecureSessionManagerStateTest}, que
 * ya cubre la API de {@link SecureSessionManager.SessionSnapshot} y los
 * helpers más íntimos. Aquí ejercitamos las rutas que tocan
 * {@link SharedPreferences} reales (en memoria) inyectando los campos
 * por reflexión.</p>
 *
 * <p>Se ejecuta bajo {@link RobolectricTestRunner} porque los tests de
 * decodificación de JWT usan {@code android.util.Base64} para garantizar que
 * la codificación coincide bit a bit con la que producción decodifica.</p>
 */
@RunWith(RobolectricTestRunner.class)
public class SecureSessionManagerExtendedTest {

    private SecureSessionManager manager;
    private MemoryContext context;
    private MemorySharedPreferences prefs;

    /**
     * Construye un {@link SecureSessionManager} sin invocar su constructor real
     * (que tocaría el Android Keystore) y le inyecta {@code prefs} y
     * {@code appContext} respaldados por implementaciones en memoria.
     */
    @Before
    public void setUp() throws Exception {
        manager = allocateManager();
        context = new MemoryContext();
        prefs = context.preferences("user_prefs_secure");

        setField(manager, "appContext", context);
        setField(manager, "prefs", prefs);
        // sessionLock es final pero hay que tener uno no nulo para los synchronized.
        setField(manager, "sessionLock", new Object());
    }

    /**
     * Verifica que {@code logout} elimina todas las claves cifradas relacionadas
     * con la sesión y limpia el provider y el flag de silent sign-in de Google.
     */
    @Test
    public void logout_clearsAllSessionKeysAndSocialState() {
        // Pre-cargamos claves de sesión simuladas y estado de provider Google.
        prefs.edit()
                .putString("username_ct", "x").putString("username_iv", "y")
                .putString("access_token_ct", "x").putString("access_token_iv", "y")
                .putString("refresh_token_ct", "x").putString("refresh_token_iv", "y")
                .putString("user_id_ct", "x").putString("user_id_iv", "y")
                .commit();
        SharedPreferences appPrefs = context.preferences(AppSettingsManager.PREFS);
        appPrefs.edit()
                .putString("auth_provider", "google")
                .putBoolean("google_silent_enabled", true)
                .commit();

        manager.logout();

        assertFalse(prefs.contains("username_ct"));
        assertFalse(prefs.contains("access_token_ct"));
        assertFalse(prefs.contains("refresh_token_ct"));
        assertFalse(prefs.contains("user_id_ct"));
        assertNull(appPrefs.getString("auth_provider", null));
        assertFalse(appPrefs.getBoolean("google_silent_enabled", true));
    }

    /**
     * Verifica que {@code clearAccessTokenOnly} borra exclusivamente el access
     * token, dejando refresh, usuario e identificador recordado intactos.
     */
    @Test
    public void clearAccessTokenOnly_removesOnlyAccessTokenKeys() {
        prefs.edit()
                .putString("access_token_ct", "x").putString("access_token_iv", "y")
                .putString("refresh_token_ct", "rx").putString("refresh_token_iv", "ry")
                .putString("username_ct", "ux").putString("username_iv", "uy")
                .commit();

        manager.clearAccessTokenOnly();

        assertFalse(prefs.contains("access_token_ct"));
        assertFalse(prefs.contains("access_token_iv"));
        assertTrue(prefs.contains("refresh_token_ct"));
        assertTrue(prefs.contains("username_ct"));
    }

    /**
     * Verifica que {@code saveRememberedIdentifier} con texto en blanco elimina
     * las claves persistidas en lugar de escribir un cifrado vacío.
     */
    @Test
    public void saveRememberedIdentifier_blankInput_removesStoredKeys() {
        prefs.edit()
                .putString("remembered_id_ct", "old-ct")
                .putString("remembered_id_iv", "old-iv")
                .commit();

        manager.saveRememberedIdentifier("   ");

        assertFalse(prefs.contains("remembered_id_ct"));
        assertFalse(prefs.contains("remembered_id_iv"));
    }

    /**
     * Verifica que {@code saveRememberedIdentifier} con {@code null} también
     * elimina cualquier valor previo persistido.
     */
    @Test
    public void saveRememberedIdentifier_nullInput_removesStoredKeys() {
        prefs.edit()
                .putString("remembered_id_ct", "old-ct")
                .putString("remembered_id_iv", "old-iv")
                .commit();

        manager.saveRememberedIdentifier(null);

        assertFalse(prefs.contains("remembered_id_ct"));
        assertFalse(prefs.contains("remembered_id_iv"));
    }

    /**
     * Verifica que {@code getRememberedIdentifier} devuelve {@code null} cuando
     * no hay nada persistido.
     */
    @Test
    public void getRememberedIdentifier_emptyPrefs_returnsNull() {
        assertNull(manager.getRememberedIdentifier());
    }

    /**
     * Verifica que {@code getRememberedIdentifier} devuelve {@code null} cuando
     * sólo está el ciphertext sin IV (estado inconsistente o legacy).
     */
    @Test
    public void getRememberedIdentifier_missingIv_returnsNull() {
        prefs.edit().putString("remembered_id_ct", "ct-sin-iv").commit();

        assertNull(manager.getRememberedIdentifier());
    }

    /**
     * Verifica que {@code isLoggedWithGoogle} delega correctamente en
     * {@link AppSettingsManager} para detectar el provider Google.
     */
    @Test
    public void isLoggedWithGoogle_returnsTrueWhenProviderIsGoogle() {
        SharedPreferences appPrefs = context.preferences(AppSettingsManager.PREFS);
        appPrefs.edit().putString("auth_provider", "google").commit();

        assertTrue(manager.isLoggedWithGoogle());
    }

    /**
     * Verifica que {@code isLoggedWithGoogle} devuelve {@code false} cuando el
     * provider almacenado no es Google.
     */
    @Test
    public void isLoggedWithGoogle_returnsFalseWhenProviderIsOther() {
        SharedPreferences appPrefs = context.preferences(AppSettingsManager.PREFS);
        appPrefs.edit().putString("auth_provider", "facebook").commit();

        assertFalse(manager.isLoggedWithGoogle());
    }

    /**
     * Verifica que {@code isLoggedWithGoogle} devuelve {@code false} cuando no
     * hay provider persistido en absoluto.
     */
    @Test
    public void isLoggedWithGoogle_returnsFalseWhenNoProviderStored() {
        assertFalse(manager.isLoggedWithGoogle());
    }

    /**
     * Verifica que {@code getAuthProvider} expone el valor persistido por
     * {@link AppSettingsManager} y normaliza la ausencia a {@code null}.
     */
    @Test
    public void getAuthProvider_reflectsAppSettings() {
        SharedPreferences appPrefs = context.preferences(AppSettingsManager.PREFS);

        assertNull(manager.getAuthProvider());

        appPrefs.edit().putString("auth_provider", "google").commit();
        assertEquals("google", manager.getAuthProvider());
    }

    /**
     * Verifica que {@code buildAccountKeyFromUserId} normaliza correctamente:
     * {@code null} y vacío se mapean a {@code null}, y los identificadores con
     * espacios se trim-ean antes del prefijo {@code uid_}.
     */
    @Test
    public void buildAccountKeyFromUserId_handlesNullAndTrim() {
        assertNull(SecureSessionManager.buildAccountKeyFromUserId(null));
        assertNull(SecureSessionManager.buildAccountKeyFromUserId(""));
        assertNull(SecureSessionManager.buildAccountKeyFromUserId("   "));
        assertEquals("uid_42", SecureSessionManager.buildAccountKeyFromUserId("42"));
        assertEquals("uid_42", SecureSessionManager.buildAccountKeyFromUserId("  42  "));
    }

    /**
     * Verifica que {@code shouldShowAutoPauseAlertsByDefault} delega en
     * {@link AppSettingsManager} con el valor por defecto cuando no hay nada persistido.
     */
    @Test
    public void shouldShowAutoPauseAlertsByDefault_defaultsToTrueWithEmptyPrefs() {
        // El default de AppSettingsManager para esta clave es true.
        assertTrue(manager.shouldShowAutoPauseAlertsByDefault());
    }

    /**
     * Verifica que {@code setShowAutoPauseAlertsByDefault} persiste el cambio
     * en {@link AppSettingsManager}.
     */
    @Test
    public void setShowAutoPauseAlertsByDefault_persistsValue() {
        manager.setShowAutoPauseAlertsByDefault(false);
        assertFalse(manager.shouldShowAutoPauseAlertsByDefault());

        manager.setShowAutoPauseAlertsByDefault(true);
        assertTrue(manager.shouldShowAutoPauseAlertsByDefault());
    }

    /**
     * Verifica que {@code isAccessTokenExpiringWithinSeconds} con un token que
     * expira muy en el futuro y leeway pequeño devuelve {@code false}.
     *
     * <p>Inyectamos el campo {@code accessToken} simulando que el cifrado ya
     * descifró: el método pasa por {@code readSessionSnapshotLocked} que sólo
     * trabaja con prefs, así que necesitamos que la lectura cifrada devuelva
     * algo. Lo conseguimos saltándonos {@code getDecryptedValueLocked} con un
     * snapshot construido por reflexión y reemplazando el campo {@code prefs}
     * con un mock que devuelva un JWT en claro... pero eso es demasiado.</p>
     *
     * <p>En su lugar, llamamos directamente al método privado
     * {@code extractExpFromAccessToken} pasando el JWT en claro.</p>
     */
    @Test
    public void extractExpFromAccessToken_validJwtWithExp_returnsExpEpoch() throws Exception {
        long exp = 9_999_999_999L; // muy en el futuro
        String jwt = buildJwt("user-42", exp);

        Method m = SecureSessionManager.class.getDeclaredMethod(
                "extractExpFromAccessToken", String.class);
        m.setAccessible(true);

        Long result = (Long) m.invoke(manager, jwt);

        assertNotNull(result);
        assertEquals(exp, result.longValue());
    }

    /**
     * Verifica que un token con {@code exp} negativo o ausente se considera no parseable.
     */
    @Test
    public void extractExpFromAccessToken_invalidExp_returnsNull() throws Exception {
        Method m = SecureSessionManager.class.getDeclaredMethod(
                "extractExpFromAccessToken", String.class);
        m.setAccessible(true);

        // Token con exp = -1 (excluido por el guard del método).
        String tokenNegative = buildJwt("user-1", -1L);
        // Token con exp = 0 (también excluido).
        String tokenZero = buildJwt("user-1", 0L);

        assertNull(m.invoke(manager, tokenNegative));
        assertNull(m.invoke(manager, tokenZero));
    }

    /**
     * Verifica que {@code decodeJwtPayload} rechaza tokens malformados sin lanzar.
     */
    @Test
    public void decodeJwtPayload_rejectsMalformedTokens() throws Exception {
        Method m = SecureSessionManager.class.getDeclaredMethod(
                "decodeJwtPayload", String.class);
        m.setAccessible(true);

        // Falta partes (sólo dos secciones).
        assertNull(m.invoke(manager, "header.payload"));
        // Cuatro secciones.
        assertNull(m.invoke(manager, "a.b.c.d"));
        // Texto en blanco.
        assertNull(m.invoke(manager, ""));
        assertNull(m.invoke(manager, "   "));
        // Null directamente.
        assertNull(m.invoke(manager, new Object[]{null}));
        // Payload no-base64 válido.
        assertNull(m.invoke(manager, "header.???.signature"));
    }

    /**
     * Verifica que {@code decodeJwtPayload} rechaza payloads que no traen {@code sub} o {@code exp}.
     */
    @Test
    public void decodeJwtPayload_requiresSubAndExp() throws Exception {
        Method m = SecureSessionManager.class.getDeclaredMethod(
                "decodeJwtPayload", String.class);
        m.setAccessible(true);

        String jwtMissingSub = buildRawJwt("{\"exp\":12345}");
        String jwtMissingExp = buildRawJwt("{\"sub\":\"user-1\"}");
        String jwtComplete = buildRawJwt("{\"sub\":\"user-1\",\"exp\":12345}");

        assertNull(m.invoke(manager, jwtMissingSub));
        assertNull(m.invoke(manager, jwtMissingExp));
        assertNotNull(m.invoke(manager, jwtComplete));
    }

    /**
     * Verifica que {@code extractUserIdFromAccessToken} extrae y trimea el claim {@code sub}.
     */
    @Test
    public void extractUserIdFromAccessToken_returnsSubClaim() throws Exception {
        Method m = SecureSessionManager.class.getDeclaredMethod(
                "extractUserIdFromAccessToken", String.class);
        m.setAccessible(true);

        String jwt = buildJwt("  user-42  ", 9_999_999_999L);
        String userId = (String) m.invoke(manager, jwt);

        assertEquals("user-42", userId);
    }

    /**
     * Verifica que {@code extractUserIdFromAccessToken} devuelve {@code null}
     * para tokens malformados o sin claim {@code sub} válido.
     */
    @Test
    public void extractUserIdFromAccessToken_invalidToken_returnsNull() throws Exception {
        Method m = SecureSessionManager.class.getDeclaredMethod(
                "extractUserIdFromAccessToken", String.class);
        m.setAccessible(true);

        assertNull(m.invoke(manager, "not-a-jwt"));
        assertNull(m.invoke(manager, new Object[]{null}));
        assertNull(m.invoke(manager, ""));
    }

    /**
     * Verifica que {@code persistAuthProvider} escribe el provider trimado
     * cuando trae texto y lo elimina cuando llega vacío.
     */
    @Test
    public void persistAuthProvider_writesAndRemovesValueAccordingly() throws Exception {
        Method m = SecureSessionManager.class.getDeclaredMethod(
                "persistAuthProvider", String.class, boolean.class);
        m.setAccessible(true);
        SharedPreferences appPrefs = context.preferences(AppSettingsManager.PREFS);

        m.invoke(manager, "google ", true);
        assertEquals("google", appPrefs.getString("auth_provider", null));

        // Texto en blanco con replaceAuthProvider=true debe limpiarlo.
        m.invoke(manager, "  ", true);
        assertNull(appPrefs.getString("auth_provider", null));
    }

    /**
     * Verifica que {@code persistAuthProvider} usa {@code commit} cuando el
     * flag {@code persistSynchronously} está activo, garantizando persistencia
     * dura antes de devolver el control.
     */
    @Test
    public void persistAuthProvider_synchronousFlag_commitsImmediately() throws Exception {
        Method m = SecureSessionManager.class.getDeclaredMethod(
                "persistAuthProvider", String.class, boolean.class);
        m.setAccessible(true);
        SharedPreferences appPrefs = context.preferences(AppSettingsManager.PREFS);

        m.invoke(manager, "facebook", true);
        // Persistencia dura: el valor está disponible inmediatamente sin
        // necesidad de drenar colas internas.
        assertEquals("facebook", appPrefs.getString("auth_provider", null));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Construye un JWT simulado con header HS256, payload con {@code sub} y
     * {@code exp}, y una firma falsa pero estructuralmente válida.
     *
     * @param sub valor del claim {@code sub}.
     * @param exp valor del claim {@code exp} en segundos epoch.
     * @return cadena {@code header.payload.signature} en formato JWT.
     */
    private static String buildJwt(String sub, long exp) {
        String header = base64Url("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String payload = base64Url(("{\"sub\":\"" + sub + "\",\"exp\":" + exp + "}")
                .getBytes(StandardCharsets.UTF_8));
        String signature = base64Url("firma-falsa".getBytes(StandardCharsets.UTF_8));
        return header + "." + payload + "." + signature;
    }

    /**
     * Construye un JWT con un payload JSON literal; útil para forzar la
     * ausencia de claims obligatorios y verificar el rechazo del decodificador.
     *
     * @param payloadJson JSON crudo a usar como payload del token.
     * @return cadena {@code header.payload.signature} con el payload provisto.
     */
    private static String buildRawJwt(String payloadJson) {
        String header = base64Url("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String payload = base64Url(payloadJson.getBytes(StandardCharsets.UTF_8));
        String signature = base64Url("firma-falsa".getBytes(StandardCharsets.UTF_8));
        return header + "." + payload + "." + signature;
    }

    /**
     * Codifica los bytes recibidos usando {@link android.util.Base64} en el
     * mismo formato que producción usa para decodificar (URL_SAFE | NO_WRAP |
     * NO_PADDING). Usar la API de Android garantiza compatibilidad bit a bit
     * cuando el test corre bajo Robolectric.
     *
     * @param bytes datos a codificar.
     * @return cadena Base64 URL-safe sin padding ni wrapping.
     */
    private static String base64Url(byte[] bytes) {
        return android.util.Base64.encodeToString(bytes,
                android.util.Base64.URL_SAFE
                        | android.util.Base64.NO_WRAP
                        | android.util.Base64.NO_PADDING);
    }

    /**
     * Inyecta un valor en un campo declarado de {@link SecureSessionManager} accesibilizándolo previamente.
     *
     * @param target instancia que recibe el valor.
     * @param name nombre del campo.
     * @param value valor a publicar.
     */
    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = SecureSessionManager.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    /**
     * Crea una instancia de la clase indicada saltándose su constructor real,
     * útil para clases Android que tocarían Keystore o servicios reales.
     *
     * @return instancia recién creada sin invocar al constructor.
     */
    private static SecureSessionManager allocateManager() throws Exception {
        Field field = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");
        field.setAccessible(true);
        Object unsafe = java.util.Objects.requireNonNull(field.get(null), "Unsafe no disponible");
        Method method = unsafe.getClass().getMethod("allocateInstance", Class.class);
        return (SecureSessionManager) method.invoke(unsafe, SecureSessionManager.class);
    }
}
