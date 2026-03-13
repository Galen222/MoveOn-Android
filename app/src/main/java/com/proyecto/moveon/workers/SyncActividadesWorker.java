package com.proyecto.moveon.workers;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.proyecto.moveon.data.activities.ActivityRepository;
import com.proyecto.moveon.data.session.SecureSessionManager;

public class SyncActividadesWorker extends Worker {

    public SyncActividadesWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        // BUG-08: Singleton en lugar de new para evitar múltiples instancias.
        String accountKey = SecureSessionManager.getInstance(getApplicationContext()).getAccountKey();

        if (accountKey == null) {
            return Result.success();
        }

        ActivityRepository repository = new ActivityRepository(getApplicationContext());
        ActivityRepository.SyncResult result = repository.syncPendingNow(accountKey);
        repository.cancelAll();

        return result.retry ? Result.retry() : Result.success();
    }
}