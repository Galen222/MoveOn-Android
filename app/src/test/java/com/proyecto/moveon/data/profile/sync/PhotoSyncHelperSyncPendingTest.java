package com.proyecto.moveon.data.profile.sync;

import static org.junit.Assert.*;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.proyecto.moveon.core.api.ApiError;
import com.proyecto.moveon.core.api.ApiErrorType;
import com.proyecto.moveon.core.api.ApiResult;
import com.proyecto.moveon.data.local.entity.PerfilCacheEntity;
import com.proyecto.moveon.data.profile.dto.ProfileInfoDto;
import com.proyecto.moveon.data.profile.local.PerfilLocalDataSource;
import com.proyecto.moveon.data.profile.remote.PerfilRemoteDataSource;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Tests del flujo {@link PhotoSyncHelper#syncPendingIfNeeded(String)} cubriendo
 * los caminos: caché ausente, sin foto pendiente, archivo pendiente
 * desaparecido, subida exitosa con merge remoto, subida exitosa sin snapshot,
 * subida fallida transitoria y subida fallida permanente.
 *
 * <p>Se ejecuta bajo {@link RobolectricTestRunner} porque algunos errores
 * generan mensajes localizados con {@code Context#getString}.</p>
 *
 * <p>El helper se construye saltando el constructor real con {@code Unsafe}
 * y se inyectan dobles ligeros para cada dependencia: {@link RecordingLocal},
 * {@link RecordingRemote} y {@link RecordingBridge}.</p>
 */
@RunWith(RobolectricTestRunner.class)
public class PhotoSyncHelperSyncPendingTest {

    private PhotoSyncHelper helper;
    private RecordingLocal local;
    private RecordingRemote remote;
    private RecordingBridge bridge;

    /**
     * Construye el helper con {@code Unsafe} e inyecta dobles para las
     * dependencias visibles vía reflexión.
     */
    @Before
    public void setUp() throws Exception {
        helper = allocate(PhotoSyncHelper.class);
        Context ctx = ApplicationProvider.getApplicationContext();
        setField(helper, "appContext", ctx);

        // Allocamos los dobles con Unsafe (sin invocar al constructor real,
        // que tocaría AppDatabase / Retrofit). Como Unsafe.allocateInstance
        // NO ejecuta los inicializadores de campo, hay que reinicializar los
        // campos con valores por defecto explícitamente.
        local = allocate(RecordingLocal.class);
        setRecordingField(local, "savedEntities", new java.util.ArrayList<PerfilCacheEntity>());
        setField(helper, "local", local);

        remote = allocate(RecordingRemote.class);
        setRecordingField(remote, "nextUploadResult",
                ApiResult.failure(ApiError.local("no configurado")));
        setRecordingField(remote, "nextFetchResult",
                ApiResult.failure(ApiError.local("no configurado")));
        setField(helper, "remote", remote);

        bridge = new RecordingBridge();
        setField(helper, "bridge", bridge);
    }

    /**
     * Inyecta un valor en un campo declarado de la clase concreta del doble
     * (necesario porque {@code Unsafe.allocateInstance} no ejecuta los
     * inicializadores definidos junto a los campos).
     *
     * @param target instancia objetivo.
     * @param name nombre del campo declarado en la clase concreta.
     * @param value valor a publicar.
     */
    private static void setRecordingField(Object target, String name, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    /**
     * Verifica que con caché inexistente devuelve {@code false} y no realiza I/O remota.
     */
    @Test
    public void syncPendingIfNeeded_noCacheEntity_returnsFalseAndDoesNothing() {
        local.cacheToReturn = null;

        boolean retry = helper.syncPendingIfNeeded("uid_42");

        assertFalse(retry);
        assertEquals(0, remote.uploadCalls);
        assertEquals(0, remote.fetchCalls);
    }

    /**
     * Verifica que con foto sin estado pendiente devuelve {@code false} sin tocar la red.
     */
    @Test
    public void syncPendingIfNeeded_notPending_returnsFalse() {
        PerfilCacheEntity cache = new PerfilCacheEntity();
        cache.photoSyncState = PhotoSyncHelper.STATE_SYNCED;
        cache.pendingLocalPhotoPath = "/tmp/x.jpg";
        local.cacheToReturn = cache;

        boolean retry = helper.syncPendingIfNeeded("uid_42");

        assertFalse(retry);
        assertEquals(0, remote.uploadCalls);
    }

    /**
     * Verifica que con la ruta pendiente vacía no se intenta subir.
     */
    @Test
    public void syncPendingIfNeeded_blankPendingPath_returnsFalse() {
        PerfilCacheEntity cache = new PerfilCacheEntity();
        cache.photoSyncState = PhotoSyncHelper.STATE_PENDING;
        cache.pendingLocalPhotoPath = "  ";
        local.cacheToReturn = cache;

        boolean retry = helper.syncPendingIfNeeded("uid_42");

        assertFalse(retry);
        assertEquals(0, remote.uploadCalls);
    }

    /**
     * Verifica que si el archivo pendiente no existe en disco se aborta sin
     * llamar al backend.
     */
    @Test
    public void syncPendingIfNeeded_fileMissingOnDisk_returnsFalse() {
        PerfilCacheEntity cache = new PerfilCacheEntity();
        cache.photoSyncState = PhotoSyncHelper.STATE_PENDING;
        cache.pendingLocalPhotoPath = "/tmp/no-existe-" + System.nanoTime() + ".jpg";
        local.cacheToReturn = cache;

        boolean retry = helper.syncPendingIfNeeded("uid_42");

        assertFalse(retry);
        assertEquals(0, remote.uploadCalls);
    }

    /**
     * Verifica que tras una subida exitosa con snapshot remoto, se delega en
     * {@code bridge.mergeRemoteSnapshot} y se devuelve {@code false}.
     */
    @Test
    public void syncPendingIfNeeded_uploadSuccessWithFetch_delegatesToBridgeMerge() throws Exception {
        File pending = Files.createTempFile("photo-pending", ".jpg").toFile();
        Files.write(pending.toPath(), "datos".getBytes(StandardCharsets.UTF_8));

        PerfilCacheEntity cache = new PerfilCacheEntity();
        cache.accountKey = "uid_42";
        cache.photoSyncState = PhotoSyncHelper.STATE_PENDING;
        cache.pendingLocalPhotoPath = pending.getAbsolutePath();
        local.cacheToReturn = cache;

        remote.nextUploadResult = ApiResult.success("ok");
        ProfileInfoDto dto = new ProfileInfoDto();
        remote.nextFetchResult = ApiResult.success(dto);

        try {
            boolean retry = helper.syncPendingIfNeeded("uid_42");

            assertFalse(retry);
            assertEquals(1, remote.uploadCalls);
            assertEquals(1, remote.fetchCalls);
            assertEquals(1, bridge.mergeCalls);
            assertSame(dto, bridge.lastMergeDto);
            assertTrue("merge debe pedir mantener pending", bridge.lastMergePreferPending);
        } finally {
            //noinspection ResultOfMethodCallIgnored
            pending.delete();
        }
    }

    /**
     * Verifica que cuando la subida es exitosa pero el fetch falla, se ejecuta
     * la rama {@code promotePendingWithoutRemote} (no se invoca al bridge.merge).
     */
    @Test
    public void syncPendingIfNeeded_uploadSuccessFetchFailure_promotesLocallyWithoutMerge() throws Exception {
        File pending = Files.createTempFile("photo-pending", ".jpg").toFile();
        Files.write(pending.toPath(), "datos".getBytes(StandardCharsets.UTF_8));

        PerfilCacheEntity cache = new PerfilCacheEntity();
        cache.accountKey = "uid_42";
        cache.photoSyncState = PhotoSyncHelper.STATE_PENDING;
        cache.pendingLocalPhotoPath = pending.getAbsolutePath();
        local.cacheToReturn = cache;

        remote.nextUploadResult = ApiResult.success("ok");
        remote.nextFetchResult = ApiResult.failure(ApiError.typed(ApiErrorType.SERVER, 500, "ko"));

        try {
            boolean retry = helper.syncPendingIfNeeded("uid_42");

            assertFalse(retry);
            assertEquals(1, remote.uploadCalls);
            assertEquals(1, remote.fetchCalls);
            assertEquals(0, bridge.mergeCalls);
        } finally {
            //noinspection ResultOfMethodCallIgnored
            pending.delete();
        }
    }

    /**
     * Verifica que un error retryable (NETWORK) marca dirty, persiste el
     * mensaje de error y devuelve {@code true} para reintento.
     */
    @Test
    public void syncPendingIfNeeded_retryableUploadError_marksDirtyAndReturnsTrue() throws Exception {
        File pending = Files.createTempFile("photo-pending", ".jpg").toFile();
        Files.write(pending.toPath(), "datos".getBytes(StandardCharsets.UTF_8));

        PerfilCacheEntity cache = new PerfilCacheEntity();
        cache.accountKey = "uid_42";
        cache.photoSyncState = PhotoSyncHelper.STATE_PENDING;
        cache.pendingLocalPhotoPath = pending.getAbsolutePath();
        local.cacheToReturn = cache;

        remote.nextUploadResult = ApiResult.failure(
                ApiError.typed(ApiErrorType.NETWORK, "sin red"));

        try {
            boolean retry = helper.syncPendingIfNeeded("uid_42");

            assertTrue("error de red es reintentable", retry);
            assertEquals(1, remote.uploadCalls);
            assertEquals(0, remote.fetchCalls);
            assertEquals("sin red", cache.photoLastError);
            assertTrue("debe quedar dirty", cache.dirty);
            assertEquals(1, local.savedEntities.size());
        } finally {
            //noinspection ResultOfMethodCallIgnored
            pending.delete();
        }
    }

    /**
     * Verifica que un error permanente (VALIDATION) descarta la foto pendiente
     * y devuelve {@code false} sin pedir reintento.
     */
    @Test
    public void syncPendingIfNeeded_permanentUploadError_revertsAndReturnsFalse() throws Exception {
        File pending = Files.createTempFile("photo-pending", ".jpg").toFile();
        Files.write(pending.toPath(), "datos".getBytes(StandardCharsets.UTF_8));

        PerfilCacheEntity cache = new PerfilCacheEntity();
        cache.accountKey = "uid_42";
        cache.photoSyncState = PhotoSyncHelper.STATE_PENDING;
        cache.pendingLocalPhotoPath = pending.getAbsolutePath();
        local.cacheToReturn = cache;

        remote.nextUploadResult = ApiResult.failure(
                ApiError.typed(ApiErrorType.VALIDATION, 422, "tamaño invalido"));

        try {
            boolean retry = helper.syncPendingIfNeeded("uid_42");

            assertFalse("error funcional NO es reintentable", retry);
            assertEquals(1, remote.uploadCalls);
            // revertPendingPhoto borra el path pendiente y guarda
            assertEquals(1, local.savedEntities.size());
            PerfilCacheEntity saved = local.savedEntities.get(0);
            assertNull(saved.pendingLocalPhotoPath);
            assertEquals(PhotoSyncHelper.STATE_FAILED, saved.photoSyncState);
            assertEquals("tamaño invalido", saved.photoLastError);
        } finally {
            // Si el helper no borra el archivo, lo limpiamos nosotros.
            //noinspection ResultOfMethodCallIgnored
            pending.delete();
        }
    }

    // -------------------------------------------------------------------------
    // Dobles de prueba
    // -------------------------------------------------------------------------

    /**
     * Doble de {@link PerfilLocalDataSource} que registra invocaciones y
     * permite preconfigurar la entidad devuelta por {@code getCacheNow}.
     */
    public static class RecordingLocal extends PerfilLocalDataSource {
        PerfilCacheEntity cacheToReturn;
        final List<PerfilCacheEntity> savedEntities = new ArrayList<>();

        /** Constructor de utilidad NO usado: se instancia con {@code Unsafe}. */
        public RecordingLocal() { super(null); }

        @Override
        public PerfilCacheEntity getCacheNow(String accountKey) {
            return cacheToReturn;
        }

        @Override
        public void saveCache(PerfilCacheEntity entity) {
            savedEntities.add(entity);
        }
    }

    /**
     * Doble de {@link PerfilRemoteDataSource} que entrega resultados pre-fijados
     * para subida y fetch sin tocar red.
     */
    public static class RecordingRemote extends PerfilRemoteDataSource {
        ApiResult<String> nextUploadResult = ApiResult.failure(ApiError.local("no configurado"));
        ApiResult<ProfileInfoDto> nextFetchResult = ApiResult.failure(ApiError.local("no configurado"));
        int uploadCalls = 0;
        int fetchCalls = 0;

        /** Constructor de utilidad NO usado: se instancia con {@code Unsafe}. */
        public RecordingRemote(@androidx.annotation.NonNull Context ctx) { super(ctx); }

        @Override
        @androidx.annotation.NonNull
        public ApiResult<String> uploadPhotoBlocking(@androidx.annotation.NonNull File file) {
            uploadCalls++;
            return nextUploadResult;
        }

        @Override
        @androidx.annotation.NonNull
        public ApiResult<ProfileInfoDto> fetchPerfilBlocking() {
            fetchCalls++;
            return nextFetchResult;
        }
    }

    /**
     * Doble del puente {@link PhotoSyncHelper.SyncManagerBridge} que registra invocaciones.
     */
    public static class RecordingBridge implements PhotoSyncHelper.SyncManagerBridge {
        int mergeCalls = 0;
        ProfileInfoDto lastMergeDto;
        boolean lastMergePreferPending;

        @Override
        @androidx.annotation.NonNull
        public PerfilCacheEntity getOrCreateCache(@androidx.annotation.NonNull String accountKey) {
            PerfilCacheEntity entity = new PerfilCacheEntity();
            entity.accountKey = accountKey;
            return entity;
        }

        @Override
        public void mergeRemoteSnapshot(@androidx.annotation.NonNull String accountKey,
                                        @androidx.annotation.NonNull ProfileInfoDto dto,
                                        boolean preferPendingPhoto) {
            mergeCalls++;
            lastMergeDto = dto;
            lastMergePreferPending = preferPendingPhoto;
        }

        @Override
        public boolean hasPendingTextChanges(@androidx.annotation.NonNull String accountKey) {
            return false;
        }
    }

    // -------------------------------------------------------------------------
    // Helpers de reflexión
    // -------------------------------------------------------------------------

    /**
     * Inyecta un valor en un campo declarado de {@link PhotoSyncHelper}.
     *
     * @param target instancia objetivo.
     * @param name nombre del campo.
     * @param value valor a publicar.
     */
    private static void setField(Object target, String name, Object value) throws Exception {
        Field f = PhotoSyncHelper.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    /**
     * Crea una instancia saltándose el constructor real para evitar tocar
     * dependencias Android no disponibles en JVM.
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
