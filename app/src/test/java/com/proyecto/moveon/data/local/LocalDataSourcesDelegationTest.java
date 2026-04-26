package com.proyecto.moveon.data.local;

import static org.junit.Assert.*;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.proyecto.moveon.data.activities.local.ActividadLocalDataSource;
import com.proyecto.moveon.data.local.dao.ActividadDao;
import com.proyecto.moveon.data.local.dao.PerfilCacheDao;
import com.proyecto.moveon.data.local.dao.PerfilPendingPatchDao;
import com.proyecto.moveon.data.local.entity.ActividadEntity;
import com.proyecto.moveon.data.local.entity.PerfilCacheEntity;
import com.proyecto.moveon.data.local.entity.PerfilPendingPatchEntity;
import com.proyecto.moveon.data.profile.local.PerfilLocalDataSource;

import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Tests de delegación de data sources locales Room usando DAOs fake y sin abrir una base de datos real.
 */
public class LocalDataSourcesDelegationTest {

    /**
     * Verifica que las lecturas de {@link ActividadLocalDataSource} delegan en el DAO con los argumentos recibidos.
     */
    @Test
    public void actividadLocalDataSource_readMethodsDelegateToDao() throws Exception {
        FakeActividadDao dao = new FakeActividadDao();
        ActividadEntity first = new ActividadEntity();
        first.localId = "local-1";
        dao.allNow = Collections.singletonList(first);
        dao.pendingCreates = Collections.singletonList(first);
        dao.pendingDeletes = Collections.singletonList(first);
        dao.byLocalId = first;
        dao.byRemoteId = first;
        dao.count = 7;
        ActividadLocalDataSource dataSource = actividadDataSourceWith(dao);

        assertSame(dao.visibleLiveData, dataSource.observeVisible("acc"));
        assertEquals("acc", dao.lastObserveAccountKey);
        assertEquals(7, dataSource.countVisibleNow("acc"));
        assertEquals("acc", dao.lastCountAccountKey);
        assertSame(dao.allNow, dataSource.getAllNow("acc"));
        assertSame(dao.pendingCreates, dataSource.getPendingCreates("acc"));
        assertSame(dao.pendingDeletes, dataSource.getPendingDeletes("acc"));
        assertSame(first, dataSource.getByLocalId("local-1"));
        assertEquals("local-1", dao.lastLocalId);
        assertSame(first, dataSource.getByRemoteId("acc", 99));
        assertEquals("acc", dao.lastRemoteAccountKey);
        assertEquals(99, dao.lastRemoteId);
    }

    /**
     * Verifica que las escrituras de {@link ActividadLocalDataSource} llegan al DAO correcto.
     */
    @Test
    public void actividadLocalDataSource_writeMethodsDelegateToDao() throws Exception {
        FakeActividadDao dao = new FakeActividadDao();
        ActividadLocalDataSource dataSource = actividadDataSourceWith(dao);
        ActividadEntity entity = new ActividadEntity();
        entity.localId = "local-2";
        List<ActividadEntity> batch = Collections.singletonList(entity);

        dataSource.save(entity);
        dataSource.saveAll(batch);
        dataSource.deleteByLocalId("local-2");
        dataSource.clearByAccount("acc");

        assertSame(entity, dao.upserted);
        assertSame(batch, dao.upsertedAll);
        assertEquals("local-2", dao.deletedLocalId);
        assertEquals("acc", dao.deletedAccountKey);
    }

    /**
     * Verifica que las lecturas de caché y cola de perfil se delegan sin alterar resultados.
     */
    @Test
    public void perfilLocalDataSource_readMethodsDelegateToDaos() throws Exception {
        FakePerfilCacheDao cacheDao = new FakePerfilCacheDao();
        FakePerfilPendingPatchDao patchDao = new FakePerfilPendingPatchDao();
        PerfilCacheEntity cache = new PerfilCacheEntity();
        cache.accountKey = "acc";
        PerfilPendingPatchEntity patch = new PerfilPendingPatchEntity();
        patch.operationId = "op";
        cacheDao.cache = cache;
        patchDao.pending = Collections.singletonList(patch);
        patchDao.pendingCount = 3;
        PerfilLocalDataSource dataSource = perfilDataSourceWith(cacheDao, patchDao);

        assertSame(cacheDao.liveData, dataSource.observeCache("acc"));
        assertEquals("acc", cacheDao.lastObserveAccountKey);
        assertSame(cache, dataSource.getCacheNow("acc"));
        assertEquals("acc", cacheDao.lastGetAccountKey);
        assertSame(patchDao.pending, dataSource.getPending("acc"));
        assertEquals("acc", patchDao.lastPendingAccountKey);
        assertEquals(3, dataSource.countPending("acc"));
        assertEquals("acc", patchDao.lastCountAccountKey);
    }

    /**
     * Verifica que las escrituras y el borrado total de perfil invocan ambos DAOs esperados.
     */
    @Test
    public void perfilLocalDataSource_writeAndClearMethodsDelegateToDaos() throws Exception {
        FakePerfilCacheDao cacheDao = new FakePerfilCacheDao();
        FakePerfilPendingPatchDao patchDao = new FakePerfilPendingPatchDao();
        PerfilLocalDataSource dataSource = perfilDataSourceWith(cacheDao, patchDao);
        PerfilCacheEntity cache = new PerfilCacheEntity();
        PerfilPendingPatchEntity patch = new PerfilPendingPatchEntity();
        patch.operationId = "op";

        dataSource.saveCache(cache);
        dataSource.enqueuePatch(patch);
        dataSource.updatePatch(patch);
        dataSource.deletePatch("op");
        dataSource.clearAllForAccount("acc");

        assertSame(cache, cacheDao.upserted);
        assertSame(patch, patchDao.inserted);
        assertSame(patch, patchDao.updated);
        assertEquals("op", patchDao.deletedOperationId);
        assertEquals("acc", patchDao.deletedAllAccountKey);
        assertEquals("acc", cacheDao.deletedAccountKey);
        assertEquals(Arrays.asList("patches", "cache"), patchDao.clearOrder);
    }

    private static ActividadLocalDataSource actividadDataSourceWith(FakeActividadDao dao) throws Exception {
        ActividadLocalDataSource dataSource = allocate(ActividadLocalDataSource.class);
        setField(dataSource, "dao", dao);
        return dataSource;
    }

    private static PerfilLocalDataSource perfilDataSourceWith(FakePerfilCacheDao cacheDao,
                                                              FakePerfilPendingPatchDao patchDao) throws Exception {
        PerfilLocalDataSource dataSource = allocate(PerfilLocalDataSource.class);
        setField(dataSource, "cacheDao", cacheDao);
        setField(dataSource, "patchDao", patchDao);
        patchDao.cacheDaoForOrder = cacheDao;
        return dataSource;
    }

    @SuppressWarnings("unchecked")
    private static <T> T allocate(Class<T> type) throws Exception {
        Field field = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");
        field.setAccessible(true);
        Object unsafe = field.get(null);
        Method method = unsafe.getClass().getMethod("allocateInstance", Class.class);
        return (T) method.invoke(unsafe, type);
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static final class FakeActividadDao implements ActividadDao {
        final MutableLiveData<List<ActividadEntity>> visibleLiveData = new MutableLiveData<>();
        int count;
        List<ActividadEntity> allNow = new ArrayList<>();
        List<ActividadEntity> pendingCreates = new ArrayList<>();
        List<ActividadEntity> pendingDeletes = new ArrayList<>();
        ActividadEntity byLocalId;
        ActividadEntity byRemoteId;
        ActividadEntity upserted;
        List<ActividadEntity> upsertedAll;
        String lastObserveAccountKey;
        String lastCountAccountKey;
        String lastAllAccountKey;
        String lastPendingCreatesAccountKey;
        String lastPendingDeletesAccountKey;
        String lastLocalId;
        String lastRemoteAccountKey;
        int lastRemoteId;
        String deletedLocalId;
        String deletedAccountKey;

        @Override
        public LiveData<List<ActividadEntity>> observeVisible(String accountKey) {
            lastObserveAccountKey = accountKey;
            return visibleLiveData;
        }

        @Override
        public int countVisibleNow(String accountKey) {
            lastCountAccountKey = accountKey;
            return count;
        }

        @Override
        public List<ActividadEntity> getAllNow(String accountKey) {
            lastAllAccountKey = accountKey;
            return allNow;
        }

        @Override
        public List<ActividadEntity> getPendingCreates(String accountKey) {
            lastPendingCreatesAccountKey = accountKey;
            return pendingCreates;
        }

        @Override
        public List<ActividadEntity> getPendingDeletes(String accountKey) {
            lastPendingDeletesAccountKey = accountKey;
            return pendingDeletes;
        }

        @Override
        public ActividadEntity getByLocalId(String localId) {
            lastLocalId = localId;
            return byLocalId;
        }

        @Override
        public ActividadEntity getByRemoteId(String accountKey, int remoteId) {
            lastRemoteAccountKey = accountKey;
            lastRemoteId = remoteId;
            return byRemoteId;
        }

        @Override
        public void upsert(ActividadEntity entity) {
            upserted = entity;
        }

        @Override
        public void upsertAll(List<ActividadEntity> items) {
            upsertedAll = items;
        }

        @Override
        public void deleteByLocalId(String localId) {
            deletedLocalId = localId;
        }

        @Override
        public void deleteByAccount(String accountKey) {
            deletedAccountKey = accountKey;
        }
    }

    private static final class FakePerfilCacheDao implements PerfilCacheDao {
        final MutableLiveData<PerfilCacheEntity> liveData = new MutableLiveData<>();
        PerfilCacheEntity cache;
        PerfilCacheEntity upserted;
        PerfilCacheEntity updated;
        String lastObserveAccountKey;
        String lastGetAccountKey;
        String deletedAccountKey;
        List<String> clearOrder;

        @Override
        public LiveData<PerfilCacheEntity> observe(String accountKey) {
            lastObserveAccountKey = accountKey;
            return liveData;
        }

        @Override
        public PerfilCacheEntity getNow(String accountKey) {
            lastGetAccountKey = accountKey;
            return cache;
        }

        @Override
        public void upsert(PerfilCacheEntity entity) {
            upserted = entity;
        }

        @Override
        public void update(PerfilCacheEntity entity) {
            updated = entity;
        }

        @Override
        public void deleteByAccount(String accountKey) {
            deletedAccountKey = accountKey;
            if (clearOrder != null) {
                clearOrder.add("cache");
            }
        }
    }

    private static final class FakePerfilPendingPatchDao implements PerfilPendingPatchDao {
        List<PerfilPendingPatchEntity> pending = new ArrayList<>();
        int pendingCount;
        PerfilPendingPatchEntity inserted;
        PerfilPendingPatchEntity updated;
        String lastPendingAccountKey;
        String lastCountAccountKey;
        String deletedOperationId;
        String deletedAllAccountKey;
        final List<String> clearOrder = new ArrayList<>();
        FakePerfilCacheDao cacheDaoForOrder;

        @Override
        public void insert(PerfilPendingPatchEntity entity) {
            inserted = entity;
        }

        @Override
        public List<PerfilPendingPatchEntity> getPending(String accountKey) {
            lastPendingAccountKey = accountKey;
            return pending;
        }

        @Override
        public int countPending(String accountKey) {
            lastCountAccountKey = accountKey;
            return pendingCount;
        }

        @Override
        public void update(PerfilPendingPatchEntity entity) {
            updated = entity;
        }

        @Override
        public void deleteById(String operationId) {
            deletedOperationId = operationId;
        }

        @Override
        public void deleteAllByAccount(String accountKey) {
            deletedAllAccountKey = accountKey;
            clearOrder.add("patches");
            if (cacheDaoForOrder != null) {
                cacheDaoForOrder.clearOrder = clearOrder;
            }
        }
    }
}
