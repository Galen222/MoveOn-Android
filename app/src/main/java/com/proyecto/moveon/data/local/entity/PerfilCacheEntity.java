package com.proyecto.moveon.data.local.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
/**
 * Entidad persistente que representa perfil cache.
 */
@Entity(tableName = "perfil_cache")
public class PerfilCacheEntity {

    @PrimaryKey
    @NonNull
    public String accountKey = "";

    @NonNull public String nombreUsuario = "";
    @Nullable public String nombreReal;
    @NonNull public String email = "";
    @NonNull public String fechaNacimiento = "";
    @Nullable public String genero;
    @Nullable public Integer altura;
    @Nullable public Double peso;
    @Nullable public String provincia;
    @Nullable public String fotoPerfil;
    public int fotoVersion;
    @Nullable public String localPhotoPath;
    @Nullable public String pendingLocalPhotoPath;
    @Nullable public String photoSyncState;
    @Nullable public String photoLastError;
    public boolean perfilVisible;
    public int totalPuntos;
    public long totalCalorias;
    public long objetivoSemanalMetros;
    public long objetivoMensualMetros;

    public boolean dirty;
    public long lastFetchedAtMs;
    public long lastSyncedAtMs;
}