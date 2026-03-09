package com.proyecto.moveon.utils;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.WorkManager;

import com.proyecto.moveon.data.activities.ActivityRepository;
import com.proyecto.moveon.data.local.db.AppDatabase;
import com.proyecto.moveon.data.profile.PerfilRepository;
import com.proyecto.moveon.data.profile.local.ProfilePhotoStorage;
import com.proyecto.moveon.data.session.SecureSessionManager;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class OfflineSessionCleaner {

    private OfflineSessionCleaner() {}

    public static void clearSessionAndLocalDataAsync(@NonNull Context context) {
        Context appContext = context.getApplicationContext();
        safeLogout(appContext);
        safeCancelWork(appContext);

        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            try {
                AppDatabase.getInstance(appContext).clearAllTables();
            } catch (Exception ignored) {
            }
            try {
                ProfilePhotoStorage.deleteAll(appContext);
            } catch (Exception ignored) {
            }
        });
        executor.shutdown();
    }

    public static void clearSessionAndLocalDataBlocking(@NonNull Context context) {
        Context appContext = context.getApplicationContext();
        safeLogout(appContext);
        safeCancelWork(appContext);
        try {
            AppDatabase.getInstance(appContext).clearAllTables();
        } catch (Exception ignored) {
        }
        try {
            ProfilePhotoStorage.deleteAll(appContext);
        } catch (Exception ignored) {
        }
    }

    private static void safeLogout(@NonNull Context context) {
        try {
            new SecureSessionManager(context).logout();
        } catch (Exception ignored) {
        }
    }

    private static void safeCancelWork(@NonNull Context context) {
        try {
            WorkManager wm = WorkManager.getInstance(context);
            wm.cancelUniqueWork(PerfilRepository.UNIQUE_SYNC_WORK_NAME);
            wm.cancelUniqueWork(ActivityRepository.UNIQUE_SYNC_WORK_NAME);
        } catch (Exception ignored) {
        }
    }
}
