package com.proyecto.moveon.data.profile.local;

import androidx.lifecycle.LiveData;

import com.proyecto.moveon.data.local.dao.PerfilCacheDao;
import com.proyecto.moveon.data.local.dao.PerfilPendingPatchDao;
import com.proyecto.moveon.data.local.db.AppDatabase;
import com.proyecto.moveon.data.local.entity.PerfilCacheEntity;
import com.proyecto.moveon.data.local.entity.PerfilPendingPatchEntity;

import java.util.List;

/**
 * Encapsula el acceso Room al perfil cacheado y a la cola local de parches pendientes.
 */
public class PerfilLocalDataSource {

    private final PerfilCacheDao cacheDao;
    private final PerfilPendingPatchDao patchDao;

    /**
     * Conecta la fuente de datos con los DAO de perfil expuestos por {@link AppDatabase}.
     *
     * @param db base de datos de la aplicación ya inicializada.
     */
    public PerfilLocalDataSource(AppDatabase db) {
        this.cacheDao = db.perfilCacheDao();
        this.patchDao = db.perfilPendingPatchDao();
    }

    /**
     * Observa el perfil cacheado de una cuenta para que la UI reaccione a cambios locales o sincronizados.
     *
     * @param accountKey clave estable de la cuenta autenticada.
     * @return {@link LiveData} con la fila cacheada asociada a la cuenta.
     */
    public LiveData<PerfilCacheEntity> observeCache(String accountKey) {
        return cacheDao.observe(accountKey);
    }

    /**
     * Lee de forma síncrona la última instantánea cacheada del perfil.
     *
     * @param accountKey clave estable de la cuenta autenticada.
     * @return {@link PerfilCacheEntity} guardada para la cuenta o {@code null} si todavía no hay caché.
     */
    public PerfilCacheEntity getCacheNow(String accountKey) {
        return cacheDao.getNow(accountKey);
    }

    /**
     * Inserta o actualiza la caché local del perfil.
     *
     * @param entity entidad con el estado actual que debe quedar persistido.
     */
    public void saveCache(PerfilCacheEntity entity) {
        cacheDao.upsert(entity);
    }

    /**
     * Añade un parche pendiente a la cola offline para enviarlo cuando haya sincronización.
     *
     * @param entity parche serializado junto con su metadato de reintento.
     */
    public void enqueuePatch(PerfilPendingPatchEntity entity) {
        patchDao.insert(entity);
    }

    /**
     * Recupera la cola de parches pendientes de una cuenta en orden de procesamiento.
     *
     * @param accountKey clave estable de la cuenta autenticada.
     * @return lista de {@link PerfilPendingPatchEntity} aún no sincronizados.
     */
    public List<PerfilPendingPatchEntity> getPending(String accountKey) {
        return patchDao.getPending(accountKey);
    }

    /**
     * Cuenta cuántos parches siguen pendientes para una cuenta concreta.
     *
     * @param accountKey clave estable de la cuenta autenticada.
     * @return número de operaciones de perfil todavía no confirmadas por el backend.
     */
    public int countPending(String accountKey) {
        return patchDao.countPending(accountKey);
    }

    /**
     * Elimina un parche ya aplicado o descartado de la cola local.
     *
     * @param operationId identificador único de la operación pendiente.
     */
    public void deletePatch(String operationId) {
        patchDao.deleteById(operationId);
    }

    /**
     * Actualiza el estado de un parche pendiente, por ejemplo su contador de reintentos o último error.
     *
     * @param entity versión nueva de la entidad pendiente.
     */
    public void updatePatch(PerfilPendingPatchEntity entity) {
        patchDao.update(entity);
    }

    /**
     * Borra tanto la caché como la cola pendiente asociadas a una cuenta.
     *
     * @param accountKey clave estable de la cuenta que debe limpiarse.
     */
    public void clearAllForAccount(String accountKey) {
        patchDao.deleteAllByAccount(accountKey);
        cacheDao.deleteByAccount(accountKey);
    }
}
