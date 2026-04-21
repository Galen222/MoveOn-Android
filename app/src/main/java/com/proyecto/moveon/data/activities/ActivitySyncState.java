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

    /**
     * Constructor privado: esta clase sólo agrupa constantes y utilidades
     * estáticas sobre los estados de sincronización, no se instancia.
     */
    private ActivitySyncState() {}

    /**
     * Devuelve si un estado de sincronización debe considerarse pendiente.
     * Cualquier valor que no sea {@link #SYNCED} cuenta como pendiente,
     * incluidos los estados de error: así la UI y los workers los vuelven
     * a reintentar cuando hay red.
     *
     * @param value estado tal y como se guarda en Room.
     * @return {@code true} si el valor NO es {@link #SYNCED}.
     */
    public static boolean isPending(@NonNull String value) {
        return !SYNCED.equals(value);
    }
}
