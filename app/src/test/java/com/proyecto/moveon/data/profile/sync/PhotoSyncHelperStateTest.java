package com.proyecto.moveon.data.profile.sync;

import static org.junit.Assert.*;

import com.proyecto.moveon.core.api.ApiError;
import com.proyecto.moveon.core.api.ApiErrorType;
import com.proyecto.moveon.data.local.entity.PerfilCacheEntity;
import com.proyecto.moveon.data.profile.dto.ProfileInfoDto;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Tests de estado fotográfico de {@link PhotoSyncHelper} sin inicializar dependencias Android.
 */
public class PhotoSyncHelperStateTest {

    /**
     * Verifica que los errores transitorios y el error nulo se consideran reintentables.
     */
    @Test
    public void isRetryableError_acceptsTransientTypesAndNull() {
        assertTrue(PhotoSyncHelper.isRetryableError(null));
        assertTrue(PhotoSyncHelper.isRetryableError(ApiError.typed(ApiErrorType.NETWORK, "red")));
        assertTrue(PhotoSyncHelper.isRetryableError(ApiError.typed(ApiErrorType.TIMEOUT, "timeout")));
        assertTrue(PhotoSyncHelper.isRetryableError(ApiError.typed(ApiErrorType.SERVER, 500, "server")));
        assertTrue(PhotoSyncHelper.isRetryableError(ApiError.typed(ApiErrorType.RATE_LIMIT, 429, "rate")));
        assertTrue(PhotoSyncHelper.isRetryableError(ApiError.typed(ApiErrorType.CANCELED, "cancelado")));
    }

    /**
     * Verifica que los errores funcionales no se consideran reintentables para fotos pendientes.
     */
    @Test
    public void isRetryableError_rejectsPermanentTypes() {
        assertFalse(PhotoSyncHelper.isRetryableError(ApiError.typed(ApiErrorType.VALIDATION, 422, "invalid")));
        assertFalse(PhotoSyncHelper.isRetryableError(ApiError.typed(ApiErrorType.UNAUTHORIZED, 401, "auth")));
        assertFalse(PhotoSyncHelper.isRetryableError(ApiError.typed(ApiErrorType.FORBIDDEN, 403, "forbidden")));
        assertFalse(PhotoSyncHelper.isRetryableError(ApiError.typed(ApiErrorType.PARSE, "parse")));
    }

    /**
     * Verifica que una foto pendiente requiere estado pendiente y ruta local con texto.
     */
    @Test
    public void hasPendingPhoto_requiresPendingStateAndPath() throws Exception {
        PhotoSyncHelper helper = allocate(PhotoSyncHelper.class);
        PerfilCacheEntity pending = new PerfilCacheEntity();
        pending.photoSyncState = PhotoSyncHelper.STATE_PENDING;
        pending.pendingLocalPhotoPath = "/tmp/avatar.jpg";

        PerfilCacheEntity withoutPath = new PerfilCacheEntity();
        withoutPath.photoSyncState = PhotoSyncHelper.STATE_PENDING;
        withoutPath.pendingLocalPhotoPath = "  ";

        PerfilCacheEntity synced = new PerfilCacheEntity();
        synced.photoSyncState = PhotoSyncHelper.STATE_SYNCED;
        synced.pendingLocalPhotoPath = "/tmp/avatar.jpg";

        assertTrue(helper.hasPendingPhoto(pending));
        assertFalse(helper.hasPendingPhoto(withoutPath));
        assertFalse(helper.hasPendingPhoto(synced));
    }

    /**
     * Verifica que el estado por defecto deja la foto limpia y sincronizada.
     */
    @Test
    public void initDefaultPhotoState_clearsPhotoFieldsAndMarksSynced() throws Exception {
        PhotoSyncHelper helper = allocate(PhotoSyncHelper.class);
        PerfilCacheEntity entity = new PerfilCacheEntity();
        entity.localPhotoPath = "/tmp/current.jpg";
        entity.pendingLocalPhotoPath = "/tmp/pending.jpg";
        entity.photoSyncState = PhotoSyncHelper.STATE_FAILED;
        entity.photoLastError = "fallo";

        helper.initDefaultPhotoState(entity);

        assertNull(entity.localPhotoPath);
        assertNull(entity.pendingLocalPhotoPath);
        assertEquals(PhotoSyncHelper.STATE_SYNCED, entity.photoSyncState);
        assertNull(entity.photoLastError);
    }


    /**
     * Verifica que mergePhotoState limpia la foto local cuando el snapshot remoto no contiene foto.
     */
    @Test
    public void mergePhotoState_withoutRemotePhotoClearsCurrentPhoto() throws Exception {
        PhotoSyncHelper helper = allocate(PhotoSyncHelper.class);
        File current = Files.createTempFile("moveon-current", ".jpg").toFile();
        Files.write(current.toPath(), "old".getBytes(StandardCharsets.UTF_8));
        PerfilCacheEntity entity = new PerfilCacheEntity();
        entity.accountKey = "uid_1";
        entity.localPhotoPath = current.getAbsolutePath();
        entity.photoSyncState = PhotoSyncHelper.STATE_SYNCED;
        ProfileInfoDto dto = new ProfileInfoDto();
        dto.fotoPerfil = "";

        helper.mergePhotoState(entity, null, dto, false);

        assertNull(entity.localPhotoPath);
        assertNull(entity.pendingLocalPhotoPath);
        assertEquals(PhotoSyncHelper.STATE_SYNCED, entity.photoSyncState);
        assertNull(entity.photoLastError);
        assertFalse(current.exists());
    }

    /**
     * Verifica que mergePhotoState reutiliza el archivo local previo si la versión remota no cambió.
     */
    @Test
    public void mergePhotoState_reusesPreviousLocalPhotoWhenVersionMatches() throws Exception {
        PhotoSyncHelper helper = allocate(PhotoSyncHelper.class);
        File current = Files.createTempFile("moveon-current", ".png").toFile();
        Files.write(current.toPath(), "avatar".getBytes(StandardCharsets.UTF_8));
        PerfilCacheEntity previous = new PerfilCacheEntity();
        previous.localPhotoPath = current.getAbsolutePath();
        previous.fotoVersion = 7;
        PerfilCacheEntity entity = new PerfilCacheEntity();
        entity.accountKey = "uid_2";
        entity.fotoVersion = 7;
        ProfileInfoDto dto = new ProfileInfoDto();
        dto.fotoPerfil = "https://res.cloudinary.com/demo/avatar.png";
        dto.fotoVersion = 7;

        helper.mergePhotoState(entity, previous, dto, false);

        assertEquals(current.getAbsolutePath(), entity.localPhotoPath);
        assertNull(entity.pendingLocalPhotoPath);
        assertEquals(PhotoSyncHelper.STATE_SYNCED, entity.photoSyncState);
        assertNull(entity.photoLastError);
        assertTrue(current.exists());
    }

    /**
     * Verifica que mergePhotoState no pisa una foto pendiente existente cuando todavía debe subirse.
     */
    @Test
    public void mergePhotoState_keepsExistingPendingPhotoWhenUploadIsStillPending() throws Exception {
        PhotoSyncHelper helper = allocate(PhotoSyncHelper.class);
        File pending = Files.createTempFile("moveon-pending", ".webp").toFile();
        Files.write(pending.toPath(), "pending".getBytes(StandardCharsets.UTF_8));
        PerfilCacheEntity entity = new PerfilCacheEntity();
        entity.accountKey = "uid_3";
        entity.pendingLocalPhotoPath = pending.getAbsolutePath();
        entity.photoSyncState = PhotoSyncHelper.STATE_PENDING;
        entity.photoLastError = "retry";
        ProfileInfoDto dto = new ProfileInfoDto();
        dto.fotoPerfil = "https://res.cloudinary.com/demo/remote.webp";
        dto.fotoVersion = 9;

        helper.mergePhotoState(entity, null, dto, false);

        assertNull(entity.localPhotoPath);
        assertEquals(pending.getAbsolutePath(), entity.pendingLocalPhotoPath);
        assertEquals(PhotoSyncHelper.STATE_PENDING, entity.photoSyncState);
        assertEquals("retry", entity.photoLastError);
        assertTrue(pending.exists());
    }
    @SuppressWarnings("unchecked")
    private static <T> T allocate(Class<T> type) throws Exception {
        Field field = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");
        field.setAccessible(true);
        Object unsafe = field.get(null);
        Method method = unsafe.getClass().getMethod("allocateInstance", Class.class);
        return (T) method.invoke(unsafe, type);
    }
}
