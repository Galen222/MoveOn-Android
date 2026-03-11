package com.proyecto.moveon.data.activities.local;

import androidx.lifecycle.LiveData;

import com.proyecto.moveon.data.local.dao.ActividadDao;
import com.proyecto.moveon.data.local.db.AppDatabase;
import com.proyecto.moveon.data.local.entity.ActividadEntity;

import java.util.List;

/**
 * Acceso local a actividades.
 */
public class ActividadLocalDataSource {

    private final ActividadDao dao;

    public ActividadLocalDataSource(AppDatabase db) {
        this.dao = db.actividadDao();
    }

    public LiveData<List<ActividadEntity>> observeVisible(String accountKey) {
        return dao.observeVisible(accountKey);
    }

    public int countVisibleNow(String accountKey) {
        return dao.countVisibleNow(accountKey);
    }

    public List<ActividadEntity> getAllNow(String accountKey) {
        return dao.getAllNow(accountKey);
    }

    public List<ActividadEntity> getPendingCreates(String accountKey) {
        return dao.getPendingCreates(accountKey);
    }

    public List<ActividadEntity> getPendingDeletes(String accountKey) {
        return dao.getPendingDeletes(accountKey);
    }

    public ActividadEntity getByLocalId(String localId) {
        return dao.getByLocalId(localId);
    }

    public ActividadEntity getByRemoteId(String accountKey, int remoteId) {
        return dao.getByRemoteId(accountKey, remoteId);
    }

    public void save(ActividadEntity entity) {
        dao.upsert(entity);
    }

    public void saveAll(List<ActividadEntity> items) {
        dao.upsertAll(items);
    }

    public void deleteByLocalId(String localId) {
        dao.deleteByLocalId(localId);
    }

    public void clearByAccount(String accountKey) {
        dao.deleteByAccount(accountKey);
    }
}
