package com.proyecto.moveon.data.activities;

import androidx.annotation.NonNull;

/**
 * Estados persistidos de sincronización de actividades.
 *
 * <p>Se modelan como constantes canónicas porque el valor se guarda en Room
 * como texto y también se reutiliza dentro de consultas @Query.</p>
 */
public final class ActivitySyncState {

    public static final String SYNCED = "SYNCED";
    public static final String PENDING_CREATE = "PENDING_CREATE";
    public static final String FAILED_CREATE = "FAILED_CREATE";
    public static final String PENDING_DELETE = "PENDING_DELETE";
    public static final String FAILED_DELETE = "FAILED_DELETE";

    private ActivitySyncState() {}

    public static boolean isPending(@NonNull String value) {
        return !SYNCED.equals(value);
    }
}
