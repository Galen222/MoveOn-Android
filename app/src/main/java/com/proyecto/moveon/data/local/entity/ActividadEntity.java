package com.proyecto.moveon.data.local.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "actividades_locales",
        indices = {
                @Index(value = {"accountKey", "fecha_ruta"}, name = "ix_actividad_account_fecha"),
                @Index(value = {"accountKey", "remoteId"}, unique = true, name = "ix_actividad_account_remote")
        }
)
public class ActividadEntity {

    @PrimaryKey
    @NonNull
    public String localId;

    @NonNull
    public String accountKey;

    @Nullable
    public Integer remoteId;

    @NonNull
    public String tipo;

    public int distancia;
    public int duracion;

    @ColumnInfo(name = "calorias_quemadas")
    public int caloriasQuemadas;

    @Nullable
    @ColumnInfo(name = "ruta_polilinea")
    public String rutaPolilinea;

    @Nullable
    @ColumnInfo(name = "ruta_mapa_url")
    public String rutaMapaUrl;

    @NonNull
    @ColumnInfo(name = "fecha_ruta")
    public String fechaRuta;

    @NonNull
    @ColumnInfo(name = "sync_state")
    public String syncState;

    @Nullable
    @ColumnInfo(name = "last_error")
    public String lastError;

    @ColumnInfo(name = "created_at_ms")
    public long createdAtMs;

    @ColumnInfo(name = "updated_at_ms")
    public long updatedAtMs;
}
