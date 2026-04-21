package com.proyecto.moveon.data.activities.local;

import androidx.lifecycle.LiveData;

import com.proyecto.moveon.data.local.dao.ActividadDao;
import com.proyecto.moveon.data.local.db.AppDatabase;
import com.proyecto.moveon.data.local.entity.ActividadEntity;

import java.util.List;

/**
 * Encapsula las lecturas y escrituras Room del historial local de actividades.
 */
public class ActividadLocalDataSource {

    private final ActividadDao dao;

    /**
     * Crea la fuente de datos local usando el {@link ActividadDao} expuesto por {@link AppDatabase}.
     *
     * @param db base de datos de la aplicación ya inicializada.
     */
    public ActividadLocalDataSource(AppDatabase db) {
        this.dao = db.actividadDao();
    }

    /**
     * Observa las actividades visibles de una cuenta para mantener la UI sincronizada con Room.
     *
     * @param accountKey clave estable de la cuenta autenticada.
     * @return {@link LiveData} con la lista filtrada de actividades visibles.
     */
    public LiveData<List<ActividadEntity>> observeVisible(String accountKey) {
        return dao.observeVisible(accountKey);
    }

    /**
     * Cuenta de forma síncrona las actividades visibles de una cuenta.
     *
     * @param accountKey clave estable de la cuenta autenticada.
     * @return número de actividades que deben mostrarse actualmente.
     */
    public int countVisibleNow(String accountKey) {
        return dao.countVisibleNow(accountKey);
    }

    /**
     * Recupera de forma inmediata todas las actividades almacenadas para una cuenta.
     *
     * @param accountKey clave estable de la cuenta autenticada.
     * @return lista completa de {@link ActividadEntity} persistidas para la cuenta.
     */
    public List<ActividadEntity> getAllNow(String accountKey) {
        return dao.getAllNow(accountKey);
    }

    /**
     * Devuelve las actividades que aún deben subirse al backend.
     *
     * @param accountKey clave estable de la cuenta autenticada.
     * @return actividades marcadas como creación pendiente.
     */
    public List<ActividadEntity> getPendingCreates(String accountKey) {
        return dao.getPendingCreates(accountKey);
    }

    /**
     * Devuelve las actividades ya borradas en local pero pendientes de eliminación remota.
     *
     * @param accountKey clave estable de la cuenta autenticada.
     * @return actividades pendientes de propagarse como borrado al backend.
     */
    public List<ActividadEntity> getPendingDeletes(String accountKey) {
        return dao.getPendingDeletes(accountKey);
    }

    /**
     * Busca una actividad por su identificador local estable.
     *
     * @param localId identificador generado en el dispositivo.
     * @return {@link ActividadEntity} asociada o {@code null} si no existe.
     */
    public ActividadEntity getByLocalId(String localId) {
        return dao.getByLocalId(localId);
    }

    /**
     * Busca la actividad que ya fue enlazada con un identificador remoto del backend.
     *
     * @param accountKey clave estable de la cuenta autenticada.
     * @param remoteId identificador numérico emitido por el backend.
     * @return {@link ActividadEntity} enlazada con ese id remoto o {@code null} si no existe.
     */
    public ActividadEntity getByRemoteId(String accountKey, int remoteId) {
        return dao.getByRemoteId(accountKey, remoteId);
    }

    /**
     * Inserta o actualiza una actividad individual en la base local.
     *
     * @param entity actividad que debe persistirse.
     */
    public void save(ActividadEntity entity) {
        dao.upsert(entity);
    }

    /**
     * Inserta o actualiza un lote de actividades en una sola operación DAO.
     *
     * @param items actividades que deben almacenarse o refrescarse localmente.
     */
    public void saveAll(List<ActividadEntity> items) {
        dao.upsertAll(items);
    }

    /**
     * Elimina físicamente una actividad local por su id interno.
     *
     * @param localId identificador local de la actividad a borrar.
     */
    public void deleteByLocalId(String localId) {
        dao.deleteByLocalId(localId);
    }

    /**
     * Borra todo el historial local asociado a una cuenta.
     *
     * @param accountKey clave estable de la cuenta que debe limpiarse.
     */
    public void clearByAccount(String accountKey) {
        dao.deleteByAccount(accountKey);
    }
}
