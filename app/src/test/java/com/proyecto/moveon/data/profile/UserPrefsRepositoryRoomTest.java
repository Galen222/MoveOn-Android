package com.proyecto.moveon.data.profile;

import static org.junit.Assert.*;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import com.google.gson.JsonObject;
import com.proyecto.moveon.core.api.ApiResult;
import com.proyecto.moveon.data.local.db.AppDatabase;
import com.proyecto.moveon.data.local.entity.UserPrefsEntity;
import com.proyecto.moveon.data.profile.remote.PerfilRemoteDataSource;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Tests de {@link UserPrefsRepository} con Room en memoria y remoto fake.
 *
 * <p>Es una clase rentable para cobertura porque en el informe base estaba al
 * 0 %, pero su lógica principal se puede ejercitar sin backend real: se fuerza
 * el singleton de {@link AppDatabase} a una base en memoria, se inyecta un
 * {@link ExecutorService} síncrono y se sustituye el remoto por un doble que
 * sólo captura el PATCH.</p>
 */
@RunWith(RobolectricTestRunner.class)
public class UserPrefsRepositoryRoomTest {

    private AppDatabase db;
    private RecordingRemote remote;
    private UserPrefsRepository repository;

    /**
     * Prepara una base Room en memoria y construye el repositorio real para
     * cubrir también su constructor público.
     */
    @Before
    public void setUp() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries()
                .build();
        setDatabaseSingleton(db);

        repository = new UserPrefsRepository(context);
        remote = allocate(RecordingRemote.class);
        setField(repository, "remote", remote);
        setField(repository, "io", new DirectExecutorService());
    }

    /**
     * Cierra la base y limpia el singleton para no contaminar otros tests.
     */
    @After
    public void tearDown() throws Exception {
        setDatabaseSingleton(null);
        if (db != null) {
            db.close();
        }
    }

    /**
     * Verifica que observe expone el LiveData del DAO para la cuenta pedida.
     */
    @Test
    public void observe_returnsUserPrefsLiveDataForAccount() {
        assertNotNull(repository.observe("acc-observe"));
    }

    /**
     * Verifica el camino de creación: si no hay fila previa se aplican
     * defaults, se guarda el nuevo objetivo semanal y se emite PATCH semanal.
     */
    @Test
    public void setWeeklyGoal_createsDefaultPrefsAndSendsWeeklyPatch() {
        repository.setWeeklyGoal("acc-week", 12_345L);

        UserPrefsEntity saved = db.userPrefsDao().getNow("acc-week");
        assertNotNull(saved);
        assertEquals("acc-week", saved.accountKey);
        assertEquals(12_345L, saved.weeklyGoalMeters);
        assertEquals(150_000L, saved.monthlyGoalMeters);
        assertTrue(saved.updatedAtMs > 0L);

        assertEquals(1, remote.patchCalls);
        assertTrue(remote.lastBody.has("objetivo_semanal_metros"));
        assertEquals(12_345L, remote.lastBody.get("objetivo_semanal_metros").getAsLong());
        assertFalse(remote.lastBody.has("objetivo_mensual_metros"));
    }

    /**
     * Verifica el camino de actualización: si ya existe fila, conserva el
     * objetivo semanal, cambia el mensual y manda sólo el PATCH mensual.
     */
    @Test
    public void setMonthlyGoal_updatesExistingPrefsAndSendsMonthlyPatch() {
        UserPrefsEntity existing = new UserPrefsEntity();
        existing.accountKey = "acc-month";
        existing.weeklyGoalMeters = 40_000L;
        existing.monthlyGoalMeters = 120_000L;
        existing.updatedAtMs = 7L;
        db.userPrefsDao().upsert(existing);

        repository.setMonthlyGoal("acc-month", 222_000L);

        UserPrefsEntity saved = db.userPrefsDao().getNow("acc-month");
        assertNotNull(saved);
        assertEquals(40_000L, saved.weeklyGoalMeters);
        assertEquals(222_000L, saved.monthlyGoalMeters);
        assertTrue(saved.updatedAtMs >= existing.updatedAtMs);

        assertEquals(1, remote.patchCalls);
        assertTrue(remote.lastBody.has("objetivo_mensual_metros"));
        assertEquals(222_000L, remote.lastBody.get("objetivo_mensual_metros").getAsLong());
        assertFalse(remote.lastBody.has("objetivo_semanal_metros"));
    }

    /**
     * Verifica que la sincronización desde servidor escribe Room pero no hace
     * llamadas remotas de vuelta al backend.
     */
    @Test
    public void syncFromServer_upsertsBothGoalsWithoutRemotePatch() {
        repository.syncFromServer("acc-sync", 77_000L, 333_000L);

        UserPrefsEntity saved = db.userPrefsDao().getNow("acc-sync");
        assertNotNull(saved);
        assertEquals("acc-sync", saved.accountKey);
        assertEquals(77_000L, saved.weeklyGoalMeters);
        assertEquals(333_000L, saved.monthlyGoalMeters);
        assertTrue(saved.updatedAtMs > 0L);
        assertEquals(0, remote.patchCalls);
    }

    private static void setDatabaseSingleton(AppDatabase value) throws Exception {
        Field instance = AppDatabase.class.getDeclaredField("INSTANCE");
        instance.setAccessible(true);
        instance.set(null, value);
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    @SuppressWarnings("unchecked")
    private static <T> T allocate(Class<T> type) throws Exception {
        Field field = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");
        field.setAccessible(true);
        Object unsafe = field.get(null);
        Method method = unsafe.getClass().getMethod("allocateInstance", Class.class);
        return (T) method.invoke(unsafe, type);
    }

    private static final class RecordingRemote extends PerfilRemoteDataSource {
        int patchCalls;
        JsonObject lastBody;

        private RecordingRemote() {
            super(ApplicationProvider.getApplicationContext());
        }

        @Override
        public void patchPerfil(JsonObject body, Callback<String> callback) {
            patchCalls++;
            lastBody = body.deepCopy();
            callback.onResult(ApiResult.success("OK"));
        }
    }

    private static final class DirectExecutorService extends AbstractExecutorService {
        private boolean shutdown;

        @Override
        public void shutdown() {
            shutdown = true;
        }

        @Override
        public List<Runnable> shutdownNow() {
            shutdown = true;
            return Collections.emptyList();
        }

        @Override
        public boolean isShutdown() {
            return shutdown;
        }

        @Override
        public boolean isTerminated() {
            return shutdown;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return true;
        }

        @Override
        public void execute(Runnable command) {
            command.run();
        }
    }
}
