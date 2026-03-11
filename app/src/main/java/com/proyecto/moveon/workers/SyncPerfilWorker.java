package com.proyecto.moveon.workers;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.proyecto.moveon.data.profile.PerfilRepository;
import com.proyecto.moveon.data.session.SecureSessionManager;

public class SyncPerfilWorker extends Worker {

    public SyncPerfilWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        SecureSessionManager sessionManager = new SecureSessionManager(getApplicationContext());
        String accountKey = sessionManager.getAccountKey();
        if (accountKey == null) {
            return Result.success();
        }

        PerfilRepository repository = new PerfilRepository(getApplicationContext());
        PerfilRepository.SyncResult syncResult = repository.syncPendingNow(accountKey);
        repository.cancelOngoing();

        return syncResult.shouldRetry ? Result.retry() : Result.success();
    }
}
