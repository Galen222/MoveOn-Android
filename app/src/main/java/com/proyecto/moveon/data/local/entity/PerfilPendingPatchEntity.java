package com.proyecto.moveon.data.local.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
/**
 * Entidad persistente que representa perfil pending patch.
 */
@Entity(tableName = "perfil_pending_patch")
public class PerfilPendingPatchEntity {

    @PrimaryKey
    @NonNull
    public String operationId;

    @NonNull
    public String accountKey;

    @NonNull
    public String payloadJson;

    public long createdAtMs;
    public int attempts;

    @Nullable
    public String lastError;

    @NonNull
    public String state;
}
