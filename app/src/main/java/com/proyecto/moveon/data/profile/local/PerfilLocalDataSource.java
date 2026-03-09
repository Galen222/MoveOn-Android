package com.proyecto.moveon.data.profile.local;

import androidx.lifecycle.LiveData;

import com.proyecto.moveon.data.local.dao.PerfilCacheDao;
import com.proyecto.moveon.data.local.dao.PerfilPendingPatchDao;
import com.proyecto.moveon.data.local.db.AppDatabase;
import com.proyecto.moveon.data.local.entity.PerfilCacheEntity;
import com.proyecto.moveon.data.local.entity.PerfilPendingPatchEntity;

import java.util.List;

public class PerfilLocalDataSource {

    private final PerfilCacheDao cacheDao;
    private final PerfilPendingPatchDao patchDao;

    public PerfilLocalDataSource(AppDatabase db) {
        this.cacheDao = db.perfilCacheDao();
        this.patchDao = db.perfilPendingPatchDao();
    }

    public LiveData<PerfilCacheEntity> observeCache(String accountKey) {
        return cacheDao.observe(accountKey);
    }

    public PerfilCacheEntity getCacheNow(String accountKey) {
        return cacheDao.getNow(accountKey);
    }

    public void saveCache(PerfilCacheEntity entity) {
        cacheDao.upsert(entity);
    }

    public void enqueuePatch(PerfilPendingPatchEntity entity) {
        patchDao.insert(entity);
    }

    public List<PerfilPendingPatchEntity> getPending(String accountKey) {
        return patchDao.getPending(accountKey);
    }

    public int countPending(String accountKey) {
        return patchDao.countPending(accountKey);
    }

    public void deletePatch(String operationId) {
        patchDao.deleteById(operationId);
    }

    public void updatePatch(PerfilPendingPatchEntity entity) {
        patchDao.update(entity);
    }

    public void clearAllForAccount(String accountKey) {
        patchDao.deleteAllByAccount(accountKey);
        cacheDao.deleteByAccount(accountKey);
    }
}
