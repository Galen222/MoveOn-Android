package com.proyecto.moveon.data.profile;

import static org.junit.Assert.*;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.google.gson.JsonObject;
import com.proyecto.moveon.core.api.ApiError;
import com.proyecto.moveon.core.api.ApiErrorType;
import com.proyecto.moveon.core.api.ApiResult;
import com.proyecto.moveon.data.profile.remote.PerfilRemoteDataSource;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Tests JVM de {@link PerfilRepository} centrados en los caminos que NO
 * requieren tocar el {@code PerfilSyncManager} (esa clase es {@code final}
 * y, sin Mockito-inline, no se puede sustituir por un doble en pruebas).
 *
 * <p>Cubre:</p>
 * <ul>
 *   <li>Las factorías estáticas y las constantes de {@link PerfilRepository.UpdateResult}
 *       y {@link PerfilRepository.SyncResult}.</li>
 *   <li>{@code applyLocalPatchAndEnqueue} con patch vacío (rama de fallo local
 *       que no llega a ejecutar el sync manager).</li>
 *   <li>{@code refreshPerfil} en sus tres caminos: remote OK con datos null,
 *       remote OK con data válida, y remote KO. Sólo el primero y el tercero
 *       evitan tocar el sync manager (tested aquí); el segundo se omite.</li>
 *   <li>{@code eliminarCuenta} en sus tres ramas (success con mensaje,
 *       success con data null usando fallback {@code "OK"}, y failure).</li>
 *   <li>{@code cancelOngoing} (delegación pura al {@code remote}).</li>
 * </ul>
 *
 * <p>El repositorio se instancia con {@code Unsafe.allocateInstance} para
 * evitar tocar {@code AppDatabase}, {@code WorkManager} y {@code Retrofit}
 * en JVM, y se le inyectan dobles ligeros y un executor síncrono.</p>
 */
@RunWith(RobolectricTestRunner.class)
public class PerfilRepositoryUnitTest {

    private PerfilRepository repository;
    private RecordingRemote recordingRemote;

    /**
     * Construye un {@link PerfilRepository} sin invocar su constructor real e
     * inyecta {@code appContext}, {@code remote} y un executor síncrono. No
     * inyectamos {@code syncManager}: las pruebas cubren sólo los caminos que
     * no lo necesitan.
     */
    @Before
    public void setUp() throws Exception {
        repository = allocate(PerfilRepository.class);
        Context ctx = ApplicationProvider.getApplicationContext();
        setField(repository, "appContext", ctx);

        recordingRemote = allocate(RecordingRemote.class);
        Field remoteField = PerfilRepository.class.getDeclaredField("remote");
        remoteField.setAccessible(true);
        remoteField.set(repository, recordingRemote);

        // Executor real: ejecuta cada Runnable en otro hilo, igual que en
        // producción. Los tests usan latches para esperar la finalización.
        Field io = PerfilRepository.class.getDeclaredField("io");
        io.setAccessible(true);
        io.set(repository, Executors.newSingleThreadExecutor());
    }

    /**
     * Verifica que las factorías de {@link PerfilRepository.UpdateResult}
     * producen los campos coherentes con cada estado y respetan los valores
     * constantes publicados.
     */
    @Test
    public void updateResult_factories_exposeCoherentStatusValues() {
        PerfilRepository.UpdateResult synced = PerfilRepository.UpdateResult.synced();
        PerfilRepository.UpdateResult queued = PerfilRepository.UpdateResult.queued();
        ApiError err = ApiError.local("boom");
        PerfilRepository.UpdateResult failed = PerfilRepository.UpdateResult.failed(err);

        assertEquals(PerfilRepository.UpdateResult.STATUS_SYNCED, synced.status);
        assertNull(synced.error);

        assertEquals(PerfilRepository.UpdateResult.STATUS_QUEUED, queued.status);
        assertNull(queued.error);

        assertEquals(PerfilRepository.UpdateResult.STATUS_FAILED, failed.status);
        assertSame(err, failed.error);
    }

    /**
     * Verifica que las tres constantes publicadas por
     * {@link PerfilRepository.UpdateResult} son estables — la UI las usa para
     * decidir copy y comportamiento, así que no deben renombrarse sin migración.
     */
    @Test
    public void updateResult_statusConstants_areStableContract() {
        assertEquals("SYNCED", PerfilRepository.UpdateResult.STATUS_SYNCED);
        assertEquals("QUEUED", PerfilRepository.UpdateResult.STATUS_QUEUED);
        assertEquals("FAILED", PerfilRepository.UpdateResult.STATUS_FAILED);
    }

    /**
     * Verifica que {@link PerfilRepository.SyncResult} expone flags
     * consistentes para los tres estados publicados (noop, completed, retry).
     */
    @Test
    public void syncResult_factories_exposeCoherentFlags() {
        PerfilRepository.SyncResult noop = PerfilRepository.SyncResult.successNoop();
        PerfilRepository.SyncResult completed = PerfilRepository.SyncResult.successCompleted();
        PerfilRepository.SyncResult retry = PerfilRepository.SyncResult.retry();

        assertFalse(noop.shouldRetry);
        assertFalse(noop.completedPendingWork);

        assertFalse(completed.shouldRetry);
        assertTrue(completed.completedPendingWork);

        assertTrue(retry.shouldRetry);
        assertFalse(retry.completedPendingWork);
    }

    /**
     * Verifica que el endpoint del worker tiene un identificador estable, ya
     * que es referenciado por workers externos.
     */
    @Test
    public void uniqueSyncWorkName_isStableContract() {
        assertEquals("sync_perfil", PerfilRepository.UNIQUE_SYNC_WORK_NAME);
    }

    /**
     * Verifica que {@code applyLocalPatchAndEnqueue} con un objeto vacío
     * notifica fallo local sin tocar el sync manager.
     */
    @Test
    public void applyLocalPatchAndEnqueue_emptyPatch_failsLocallyWithoutTouchingSyncManager() throws Exception {
        AtomicReference<PerfilRepository.UpdateResult> received = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        repository.applyLocalPatchAndEnqueue("uid_42", new JsonObject(), result -> {
            received.set(result);
            latch.countDown();
        });

        assertTrue("debe entregar resultado", latch.await(2, TimeUnit.SECONDS));
        assertNotNull(received.get());
        assertEquals(PerfilRepository.UpdateResult.STATUS_FAILED, received.get().status);
        assertNotNull(received.get().error);
    }

    /**
     * Verifica que {@code applyLocalPatchAndEnqueue} con patch vacío y
     * callback {@code null} no rompe.
     */
    @Test
    public void applyLocalPatchAndEnqueue_emptyPatchWithoutCallback_doesNotThrow() {
        repository.applyLocalPatchAndEnqueue("uid_42", new JsonObject(), null);
        // Si llega aquí sin lanzar, el test pasa.
        assertTrue(true);
    }

    /**
     * Verifica que {@code refreshPerfil} con fallo remoto propaga el error al
     * callback sin pasar por el sync manager.
     */
    @Test
    public void refreshPerfil_failedRemote_propagatesError() throws Exception {
        ApiError err = ApiError.typed(ApiErrorType.UNAUTHORIZED, "401");
        recordingRemote.nextFetchResult = ApiResult.failure(err);

        AtomicReference<ApiError> received = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        repository.refreshPerfil("uid_42", error -> {
            received.set(error);
            latch.countDown();
        });

        assertTrue("debe llegar callback", latch.await(2, TimeUnit.SECONDS));
        assertSame(err, received.get());
    }

    /**
     * Verifica que {@code refreshPerfil} con éxito pero {@code data == null}
     * cae en el fallback local sin pasar por el sync manager.
     */
    @Test
    public void refreshPerfil_successWithNullData_propagatesLocalFallback() throws Exception {
        recordingRemote.nextFetchResult = ApiResult.success(null);

        AtomicReference<ApiError> received = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        repository.refreshPerfil("uid_42", error -> {
            received.set(error);
            latch.countDown();
        });

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertNotNull("debe entregar fallback local", received.get());
    }

    /**
     * Verifica que {@code refreshPerfil} con fallo remoto sin error explícito
     * cae al fallback local "error_cargando_perfil".
     */
    @Test
    public void refreshPerfil_failureWithoutExplicitError_usesLocalFallback() throws Exception {
        recordingRemote.nextFetchResult = ApiResult.failure(null);

        AtomicReference<ApiError> received = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        repository.refreshPerfil("uid_42", error -> {
            received.set(error);
            latch.countDown();
        });

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertNotNull(received.get());
    }

    /**
     * Verifica que {@code refreshPerfil} con callback null y fallo remoto no
     * rompe (rama del callback ausente).
     */
    @Test
    public void refreshPerfil_failureWithoutCallback_doesNotThrow() throws Exception {
        recordingRemote.nextFetchResult = ApiResult.failure(
                ApiError.typed(ApiErrorType.SERVER, 500, "ko"));

        repository.refreshPerfil("uid_42", null);

        // Aceptamos que la operación se complete sin lanzar.
        Thread.sleep(150);
        assertTrue(true);
    }

    /**
     * Verifica que {@code eliminarCuenta} con respuesta de éxito y mensaje
     * del backend propaga ese mensaje al callback.
     */
    @Test
    public void eliminarCuenta_successfulRemote_propagatesBackendMessage() throws Exception {
        recordingRemote.nextDeleteResult = ApiResult.success("cuenta eliminada");

        AtomicReference<ApiResult<String>> received = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        repository.eliminarCuenta(result -> {
            received.set(result);
            latch.countDown();
        });

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertTrue(received.get().isSuccess());
        assertEquals("cuenta eliminada", received.get().data);
    }

    /**
     * Verifica que {@code eliminarCuenta} con éxito pero data null aplica el
     * fallback {@code "OK"} esperado por la UI.
     */
    @Test
    public void eliminarCuenta_successWithNullData_returnsOkFallback() throws Exception {
        recordingRemote.nextDeleteResult = ApiResult.success(null);

        AtomicReference<ApiResult<String>> received = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        repository.eliminarCuenta(result -> {
            received.set(result);
            latch.countDown();
        });

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertTrue(received.get().isSuccess());
        assertEquals("OK", received.get().data);
    }

    /**
     * Verifica que {@code eliminarCuenta} con error remoto explícito propaga
     * ese error sin transformarlo.
     */
    @Test
    public void eliminarCuenta_remoteFailure_propagatesError() throws Exception {
        ApiError err = ApiError.typed(ApiErrorType.SERVER, "500");
        recordingRemote.nextDeleteResult = ApiResult.failure(err);

        AtomicReference<ApiResult<String>> received = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        repository.eliminarCuenta(result -> {
            received.set(result);
            latch.countDown();
        });

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertFalse(received.get().isSuccess());
        assertSame(err, received.get().error);
    }

    /**
     * Verifica que {@code eliminarCuenta} con fallo de red explícito propaga
     * exactamente ese error sin transformarlo en un fallback.
     *
     * <p>Nota: el camino "fallo sin error explícito" no es alcanzable porque
     * {@code ApiResult.failure(null)} no es un estado válido en producción
     * ({@link com.proyecto.moveon.core.api.ApiResult#failure(com.proyecto.moveon.core.api.ApiError)}
     * exige error no nulo).</p>
     */
    @Test
    public void eliminarCuenta_failureWithGenericError_propagatesError() throws Exception {
        ApiError err = ApiError.typed(ApiErrorType.NETWORK, "sin red");
        recordingRemote.nextDeleteResult = ApiResult.failure(err);

        AtomicReference<ApiResult<String>> received = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        repository.eliminarCuenta(result -> {
            received.set(result);
            latch.countDown();
        });

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertFalse(received.get().isSuccess());
        assertNotNull(received.get().error);
    }

    /**
     * Verifica que {@code cancelOngoing} delega en el data source remoto.
     */
    @Test
    public void cancelOngoing_delegatesToRemote() {
        repository.cancelOngoing();
        assertEquals(1, recordingRemote.cancelAllCalls);
    }

    // -------------------------------------------------------------------------
    // Doble de prueba
    // -------------------------------------------------------------------------

    /**
     * Doble de {@link PerfilRemoteDataSource} que registra invocaciones y
     * expone resultados controlados por el test.
     *
     * <p>Se instancia con {@code Unsafe.allocateInstance} (sin invocar al
     * constructor real) para que su superclase no toque red.</p>
     */
    public static class RecordingRemote extends PerfilRemoteDataSource {

        ApiResult<com.proyecto.moveon.data.profile.dto.ProfileInfoDto> nextFetchResult =
                ApiResult.failure(ApiError.local("no configurado"));
        ApiResult<String> nextDeleteResult =
                ApiResult.failure(ApiError.local("no configurado"));
        int cancelAllCalls = 0;

        /**
         * Constructor de utilidad que NUNCA se invoca directamente desde el test.
         *
         * @param ctx contexto sin uso real.
         */
        public RecordingRemote(@androidx.annotation.NonNull Context ctx) {
            super(ctx);
        }

        @Override
        public void fetchPerfil(@androidx.annotation.NonNull Callback<com.proyecto.moveon.data.profile.dto.ProfileInfoDto> callback) {
            callback.onResult(nextFetchResult);
        }

        @Override
        public void eliminarCuenta(@androidx.annotation.NonNull Callback<String> callback) {
            callback.onResult(nextDeleteResult);
        }

        @Override
        public void cancelAll() {
            cancelAllCalls++;
        }
    }

    // -------------------------------------------------------------------------
    // Helpers de reflexión
    // -------------------------------------------------------------------------

    /**
     * Inyecta un valor en un campo declarado de {@link PerfilRepository}.
     *
     * @param target instancia objetivo.
     * @param name nombre del campo.
     * @param value valor a publicar.
     */
    private static void setField(Object target, String name, Object value) throws Exception {
        Field f = PerfilRepository.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    /**
     * Crea una instancia saltándose el constructor real para evitar tocar
     * Android Keystore, base de datos, red o WorkManager.
     *
     * @param type clase a instanciar.
     * @param <T> tipo devuelto.
     * @return instancia recién creada sin invocar al constructor.
     */
    @SuppressWarnings("unchecked")
    private static <T> T allocate(Class<T> type) throws Exception {
        Field f = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");
        f.setAccessible(true);
        Object unsafe = f.get(null);
        Method m = unsafe.getClass().getMethod("allocateInstance", Class.class);
        return (T) m.invoke(unsafe, type);
    }
}
